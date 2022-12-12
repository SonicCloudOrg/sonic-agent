/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.*;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.cloud.sonic.agent.bridge.android.AndroidSupplyTool;
import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.maps.AndroidAPKMap;
import org.cloud.sonic.agent.common.maps.DevicesLockMap;
import org.cloud.sonic.agent.common.maps.HandlerMap;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.android.AndroidRunStepThread;
import org.cloud.sonic.agent.tools.*;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.cloud.sonic.driver.common.tool.SonicRespException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.stream.FileImageOutputStream;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@ServerEndpoint(value = "/websockets/android/{key}/{udId}/{token}", configurator = WsEndpointConfigure.class)
public class AndroidWSServer implements IAndroidWSServer {
    @Value("${sonic.agent.key}")
    private String key;
    @Value("${sonic.agent.port}")
    private int port;
    private Map<Session, OutputStream> outputMap = new ConcurrentHashMap<>();
    private List<Session> NotStopSession = new ArrayList<>();

    private Map<Session, Thread> touchMap = new ConcurrentHashMap<>();
    @Autowired
    private AgentManagerTool agentManagerTool;

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key)) || token.length() == 0) {
            log.info("拦截访问！");
            return;
        }

        session.getUserProperties().put("udId", udId);
        boolean lockSuccess = DevicesLockMap.lockByUdId(udId, 30L, TimeUnit.SECONDS);
        if (!lockSuccess) {
            log.info("30s内获取设备锁失败，请确保设备无人使用");
            return;
        }
        log.info("android lock udId：{}", udId);
        AndroidDeviceLocalStatus.startDebug(udId);

        // 更新使用用户
        JSONObject jsonDebug = new JSONObject();
        jsonDebug.put("msg", "debugUser");
        jsonDebug.put("token", token);
        jsonDebug.put("udId", udId);
        TransportWorker.send(jsonDebug);

        WebSocketSessionMap.addSession(session);
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        if (iDevice == null) {
            log.info("设备未连接，请检查！");
            return;
        }
        saveUdIdMapAndSet(session, iDevice);

        AndroidAPKMap.getMap().put(udId, false);
        String path = AndroidDeviceBridgeTool.executeCommand(iDevice, "pm path org.cloud.sonic.android").trim()
                .replaceAll("package:", "")
                .replaceAll("\n", "")
                .replaceAll("\t", "");
        if (path.length() > 0 && AndroidDeviceBridgeTool.checkSonicApkVersion(iDevice)) {
            log.info("Check Sonic Apk version and status pass...");
        } else {
            log.info("Sonic Apk version not newest or not install, starting install...");
            try {
                iDevice.uninstallPackage("org.cloud.sonic.android");
            } catch (InstallException e) {
                log.info("uninstall sonic Apk err...");
            }
            try {
                iDevice.installPackage("plugins/sonic-android-apk.apk",
                        true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES
                        , "-r", "-t", "-g");
            } catch (InstallException e) {
                if (e.getMessage().contains("Unknown option: -g")) {
                    try {
                        iDevice.installPackage("plugins/sonic-android-apk.apk",
                                true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES
                                , "-r", "-t");
                    } catch (InstallException e2) {
                        e2.printStackTrace();
                        log.info("Sonic Apk install failed.");
                        return;
                    }
                } else {
                    e.printStackTrace();
                    log.info("Sonic Apk install failed.");
                    return;
                }
            }
            AndroidDeviceBridgeTool.executeCommand(iDevice, "appops set org.cloud.sonic.android POST_NOTIFICATION allow");
            AndroidDeviceBridgeTool.executeCommand(iDevice, "appops set org.cloud.sonic.android RUN_IN_BACKGROUND allow");
            AndroidDeviceBridgeTool.executeCommand(iDevice, "dumpsys deviceidle whitelist +org.cloud.sonic.android");
            log.info("Sonic Apk install successful.");
            path = AndroidDeviceBridgeTool.executeCommand(iDevice, "pm path org.cloud.sonic.android").trim()
                    .replaceAll("package:", "")
                    .replaceAll("\n", "")
                    .replaceAll("\t", "");
        }
        AndroidDeviceBridgeTool.executeCommand(iDevice, "am start -n org.cloud.sonic.android/.SonicServiceActivity");
        AndroidAPKMap.getMap().put(udId, true);
        if (AndroidDeviceBridgeTool.getOrientation(iDevice) != 0) {
            AndroidDeviceBridgeTool.pressKey(iDevice, 3);
        }

        startTouch(session, iDevice, path);

        AndroidSupplyTool.startShare(udId, session);

        openDriver(iDevice, session);

        String currentIme = AndroidDeviceBridgeTool.executeCommand(iDevice, "settings get secure default_input_method");
        if (!currentIme.contains("org.cloud.sonic.android/.keyboard.SonicKeyboard")) {
            AndroidDeviceBridgeTool.executeCommand(iDevice, "ime enable org.cloud.sonic.android/.keyboard.SonicKeyboard");
            AndroidDeviceBridgeTool.executeCommand(iDevice, "ime set org.cloud.sonic.android/.keyboard.SonicKeyboard");
        }
    }

    @OnClose
    public void onClose(Session session) {
        String udId = (String) session.getUserProperties().get("udId");
        try {
            exit(session);
        } finally {
            DevicesLockMap.unlockAndRemoveByUdId(udId);
            log.info("android unlock udId：{}", udId);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error(error.getMessage());
        error.printStackTrace();
        JSONObject errMsg = new JSONObject();
        errMsg.put("msg", "error");
        BytesTool.sendText(session, errMsg.toJSONString());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        JSONObject msg = JSON.parseObject(message);
        log.info("{} send: {}", session.getId(), msg);
        IDevice iDevice = udIdMap.get(session);
        switch (msg.getString("type")) {
            case "startKeyboard": {
                String currentIme = AndroidDeviceBridgeTool.executeCommand(iDevice, "settings get secure default_input_method");
                if (!currentIme.contains("org.cloud.sonic.android/.keyboard.SonicKeyboard")) {
                    AndroidDeviceBridgeTool.executeCommand(iDevice, "ime enable org.cloud.sonic.android/.keyboard.SonicKeyboard");
                    AndroidDeviceBridgeTool.executeCommand(iDevice, "ime set org.cloud.sonic.android/.keyboard.SonicKeyboard");
                }
                break;
            }
            case "stopKeyboard":
                AndroidDeviceBridgeTool.executeCommand(iDevice, "ime disable org.cloud.sonic.android/.keyboard.SonicKeyboard");
                break;
            case "clearProxy":
                AndroidDeviceBridgeTool.clearProxy(iDevice);
                break;
            case "proxy": {
                AndroidDeviceBridgeTool.clearProxy(iDevice);
                Socket portSocket = PortTool.getBindSocket();
                Socket webPortSocket = PortTool.getBindSocket();
                int pPort = PortTool.releaseAndGetPort(portSocket);
                int webPort = PortTool.releaseAndGetPort(webPortSocket);
                SGMTool.startProxy(iDevice.getSerialNumber(), SGMTool.getCommand(pPort, webPort));
                AndroidDeviceBridgeTool.startProxy(iDevice, getHost(), pPort);
                JSONObject proxy = new JSONObject();
                proxy.put("webPort", webPort);
                proxy.put("port", pPort);
                proxy.put("msg", "proxyResult");
                BytesTool.sendText(session, proxy.toJSONString());
                break;
            }
            case "installCert": {
                AndroidDeviceBridgeTool.executeCommand(iDevice,
                        String.format("am start -a android.intent.action.VIEW -d http://%s:%d/assets/download", getHost(), port));
                break;
            }
            case "forwardView": {
                JSONObject forwardView = new JSONObject();
                forwardView.put("msg", "forwardView");
                forwardView.put("detail", AndroidDeviceBridgeTool.getWebView(iDevice));
                BytesTool.sendText(session, forwardView.toJSONString());
                break;
            }
            case "find":
                AndroidDeviceBridgeTool.searchDevice(iDevice);
                break;
            case "battery":
                AndroidDeviceBridgeTool.controlBattery(iDevice, msg.getInteger("detail"));
                break;
            case "uninstallApp": {
                JSONObject result = new JSONObject();
                try {
                    iDevice.uninstallPackage(msg.getString("detail"));
                    result.put("detail", "success");
                } catch (InstallException e) {
                    result.put("detail", "fail");
                    e.printStackTrace();
                }
                result.put("msg", "uninstallFinish");
                BytesTool.sendText(session, result.toJSONString());
                break;
            }
            case "scan":
                AndroidDeviceBridgeTool.pushToCamera(iDevice, msg.getString("url"));
                break;
            case "text":
                AndroidDeviceBridgeTool.executeCommand(iDevice, "am broadcast -a SONIC_KEYBOARD --es msg \"" + msg.getString("detail") + "\"");
                break;
            case "touch":
                OutputStream outputStream = outputMap.get(session);
                if (outputStream != null) {
                    try {
                        outputStream.write(msg.getString("detail").getBytes());
                        outputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case "keyEvent":
                AndroidDeviceBridgeTool.pressKey(iDevice, msg.getInteger("detail"));
                break;
            case "pullFile": {
                JSONObject result = new JSONObject();
                result.put("msg", "pullResult");
                String url = AndroidDeviceBridgeTool.pullFile(iDevice, msg.getString("path"));
                if (url != null) {
                    result.put("status", "success");
                    result.put("url", url);
                } else {
                    result.put("status", "fail");
                }
                BytesTool.sendText(session, result.toJSONString());
                break;
            }
            case "pushFile": {
                JSONObject result = new JSONObject();
                result.put("msg", "pushResult");
                try {
                    File localFile = DownloadTool.download(msg.getString("file"));
                    iDevice.pushFile(localFile.getAbsolutePath()
                            , msg.getString("path"));
                    result.put("status", "success");
                } catch (IOException | AdbCommandRejectedException | SyncException | TimeoutException e) {
                    result.put("status", "fail");
                    e.printStackTrace();
                }
                BytesTool.sendText(session, result.toJSONString());
                break;
            }
            case "debug":
                AndroidStepHandler androidStepHandler = HandlerMap.getAndroidMap().get(session.getId());
                switch (msg.getString("detail")) {
                    case "poco": {
                        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                            androidStepHandler.startPocoDriver(new HandleContext(), msg.getString("engine"), msg.getInteger("port"));
                            JSONObject poco = new JSONObject();
                            try {
                                poco.put("result", androidStepHandler.getPocoDriver().getPageSourceForJsonString());
                            } catch (SonicRespException e) {
                                poco.put("result", "");
                                e.printStackTrace();
                            }
                            poco.put("msg", "poco");
                            BytesTool.sendText(session, poco.toJSONString());
                            androidStepHandler.closePocoDriver(new HandleContext());
                        });
                        break;
                    }
                    case "runStep": {
                        JSONObject jsonDebug = new JSONObject();
                        jsonDebug.put("msg", "findSteps");
                        jsonDebug.put("key", key);
                        jsonDebug.put("udId", iDevice.getSerialNumber());
                        jsonDebug.put("pwd", msg.getString("pwd"));
                        jsonDebug.put("sessionId", session.getId());
                        jsonDebug.put("caseId", msg.getInteger("caseId"));
                        TransportWorker.send(jsonDebug);
                        break;
                    }
                    case "stopStep": {
                        TaskManager.forceStopDebugStepThread(
                                AndroidRunStepThread.ANDROID_RUN_STEP_TASK_PRE.formatted(
                                        0, msg.getInteger("caseId"), msg.getString("udId")
                                )
                        );
                        break;
                    }
                    case "openApp": {
                        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                            AndroidDeviceBridgeTool.activateApp(iDevice, msg.getString("pkg"));
                        });
                        break;
                    }
                    case "tap": {
                        String xy = msg.getString("point");
                        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(iDevice, "input tap " + x + " " + y);
                        break;
                    }
                    case "longPress": {
                        String xy = msg.getString("point");
                        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(iDevice, "input swipe " + x + " " + y + " " + x + " " + y + " 1500");
                        break;
                    }
                    case "swipe": {
                        String xy1 = msg.getString("pointA");
                        String xy2 = msg.getString("pointB");
                        int x1 = Integer.parseInt(xy1.substring(0, xy1.indexOf(",")));
                        int y1 = Integer.parseInt(xy1.substring(xy1.indexOf(",") + 1));
                        int x2 = Integer.parseInt(xy2.substring(0, xy2.indexOf(",")));
                        int y2 = Integer.parseInt(xy2.substring(xy2.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(iDevice, "input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " 200");
                        break;
                    }
                    case "install": {
                        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                            JSONObject result = new JSONObject();
                            result.put("msg", "installFinish");
                            try {
                                File localFile = new File(msg.getString("apk"));
                                if (msg.getString("apk").contains("http")) {
                                    localFile = DownloadTool.download(msg.getString("apk"));
                                }
                                iDevice.installPackage(localFile.getAbsolutePath()
                                        , true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES
                                        , "-r", "-t", "-g");
                                result.put("status", "success");
                            } catch (IOException | InstallException e) {
                                result.put("status", "fail");
                                e.printStackTrace();
                            }
                            BytesTool.sendText(session, result.toJSONString());
                        });
                        break;
                    }
                    case "openDriver": {
                        if (androidStepHandler == null || androidStepHandler.getAndroidDriver() == null) {
                            openDriver(iDevice, session);
                        }
                        break;
                    }
                    case "tree": {
                        if (androidStepHandler != null && androidStepHandler.getAndroidDriver() != null) {
                            AndroidStepHandler finalAndroidStepHandler = androidStepHandler;
                            AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                                try {
                                    JSONObject result = new JSONObject();
                                    androidStepHandler.switchWindowMode(new HandleContext(), msg.getBoolean("isMulti"));
                                    result.put("msg", "tree");
                                    result.put("detail", finalAndroidStepHandler.getResource());
                                    result.put("webView", finalAndroidStepHandler.getWebView());
                                    result.put("activity", AndroidDeviceBridgeTool.getCurrentActivity(iDevice));
                                    BytesTool.sendText(session, result.toJSONString());
                                } catch (Throwable e) {
                                    log.error(e.getMessage());
                                    JSONObject result = new JSONObject();
                                    result.put("msg", "treeFail");
                                    BytesTool.sendText(session, result.toJSONString());
                                }
                            });
                        }
                        break;
                    }
                    case "eleScreen": {
                        if (androidStepHandler != null && androidStepHandler.getAndroidDriver() != null) {
                            AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                                JSONObject result = new JSONObject();
                                result.put("msg", "eleScreen");
                                try {
                                    File folder = new File("test-output");
                                    if (!folder.exists()) {
                                        folder.mkdirs();
                                    }
                                    File output = new File(folder + File.separator + iDevice.getSerialNumber() + Calendar.getInstance().getTimeInMillis() + ".png");
                                    try {
                                        byte[] bt = androidStepHandler.findEle("xpath", msg.getString("xpath")).screenshot();
                                        FileImageOutputStream imageOutput = new FileImageOutputStream(output);
                                        imageOutput.write(bt, 0, bt.length);
                                        imageOutput.close();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    result.put("img", UploadTools.upload(output, "keepFiles"));
                                } catch (Exception e) {
                                    result.put("errMsg", "获取元素截图失败！");
                                }
                                BytesTool.sendText(session, result.toJSONString());
                            });
                        }
                        break;
                    }
                }
                break;
        }
    }

    private void openDriver(IDevice iDevice, Session session) {
        synchronized (session) {
            AndroidStepHandler androidStepHandler = new AndroidStepHandler();
            androidStepHandler.setTestMode(0, 0, iDevice.getSerialNumber(), DeviceStatus.DEBUGGING, session.getId());
            JSONObject result = new JSONObject();
            AndroidStepHandler finalAndroidStepHandler1 = androidStepHandler;
            AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                try {
                    AndroidDeviceLocalStatus.startDebug(iDevice.getSerialNumber());
                    int port = AndroidDeviceBridgeTool.startUiaServer(iDevice);
                    finalAndroidStepHandler1.startAndroidDriver(iDevice, port);
                    result.put("status", "success");
                    result.put("detail", "初始化 UIAutomator2 Server 完成！");
                    HandlerMap.getAndroidMap().put(session.getId(), finalAndroidStepHandler1);
                } catch (Exception e) {
                    log.error(e.getMessage());
                    result.put("status", "error");
                    result.put("detail", "初始化 UIAutomator2 Server 失败！");
                    finalAndroidStepHandler1.closeAndroidDriver();
                } finally {
                    result.put("msg", "openDriver");
                    BytesTool.sendText(session, result.toJSONString());
                }
            });
        }
    }

    private void exit(Session session) {
        AndroidDeviceLocalStatus.finish(session.getUserProperties().get("udId") + "");
        IDevice iDevice = udIdMap.get(session);
        try {
            AndroidStepHandler androidStepHandler = HandlerMap.getAndroidMap().get(session.getId());
            if (androidStepHandler != null) {
                androidStepHandler.closeAndroidDriver();
            }
        } catch (Exception e) {
            log.info("关闭driver异常!");
        } finally {
            HandlerMap.getAndroidMap().remove(session.getId());
        }
        if (iDevice != null) {
            AndroidDeviceBridgeTool.clearProxy(iDevice);
            AndroidDeviceBridgeTool.clearWebView(iDevice);
            AndroidSupplyTool.stopShare(iDevice.getSerialNumber());
            SGMTool.stopProxy(iDevice.getSerialNumber());
            AndroidAPKMap.getMap().remove(iDevice.getSerialNumber());
        }
        stopTouch(session);
        removeUdIdMapAndSet(session);
        WebSocketSessionMap.removeSession(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("{} : quit.", session.getId());
    }

    private void startTouch(Session session, IDevice iDevice, String path) {
        Semaphore isTouchFinish = new Semaphore(0);
        String finalPath = path;

        Thread touchPro = new Thread(() -> {
            try {
                //开始启动
                iDevice.executeShellCommand(String.format("CLASSPATH=%s exec app_process /system/bin org.cloud.sonic.android.plugin.SonicPluginTouchService", finalPath)
                        , new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                log.info(res);
                                if (res.contains("Address already in use")) {
                                    NotStopSession.add(session);
                                    isTouchFinish.release();
                                }
                                if (res.startsWith("starting")) {
                                    isTouchFinish.release();
                                }
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }, 0, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.info("{} device touch service launch err"
                        , iDevice.getSerialNumber());
                log.error(e.getMessage());
            }
        });
        touchPro.start();

        int finalTouchPort = PortTool.getPort();
        Thread touchSocketThread = new Thread(() -> {
            int wait = 0;
            while (!isTouchFinish.tryAcquire()) {
                wait++;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (wait > 20) {
                    return;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.info(e.getMessage());
            }
            AndroidDeviceBridgeTool.forward(iDevice, finalTouchPort, "sonictouchservice");
            Socket touchSocket = null;
            OutputStream outputStream = null;
            try {
                touchSocket = new Socket("localhost", finalTouchPort);
                outputStream = touchSocket.getOutputStream();
                outputMap.put(session, outputStream);
                while (touchSocket.isConnected() && !Thread.interrupted()) {
                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException e) {
                log.info("error: {}", e.getMessage());
            } finally {
                if (touchPro.isAlive()) {
                    touchPro.interrupt();
                    log.info("touch thread closed.");
                }
                if (NotStopSession.contains(session)) {
                    NotStopSession.remove(session);
                }
                if (touchSocket != null && touchSocket.isConnected()) {
                    try {
                        touchSocket.close();
                        log.info("touch socket closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (outputStream != null) {
                    try {
                        outputStream.close();
                        log.info("touch output stream closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            outputMap.remove(session);
            AndroidDeviceBridgeTool.removeForward(iDevice, finalTouchPort, "sonictouchservice");
        });
        touchSocketThread.start();
        int w = 0;
        while (outputMap.get(session) == null) {
            if (w > 10) {
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            w++;
        }
        touchMap.put(session, touchSocketThread);
    }

    private void stopTouch(Session session) {
        if (outputMap.get(session) != null) {
            try {
                outputMap.get(session).write("release \n".getBytes(StandardCharsets.UTF_8));
                outputMap.get(session).flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (touchMap.get(session) != null) {
            touchMap.get(session).interrupt();
            int wait = 0;
            while (!touchMap.get(session).isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                wait++;
                if (wait >= 3) {
                    break;
                }
            }
        }
        touchMap.remove(session);
    }
}
