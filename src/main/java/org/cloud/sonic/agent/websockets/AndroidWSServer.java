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
import org.cloud.sonic.agent.tests.handlers.AndroidStepHandler;
import org.cloud.sonic.agent.tests.handlers.AndroidTouchHandler;
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
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.SGMTool;
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
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@ServerEndpoint(value = "/websockets/android/{key}/{udId}/{token}", configurator = WsEndpointConfigure.class)
public class AndroidWSServer implements IAndroidWSServer {
    @Value("${sonic.agent.key}")
    private String key;
    @Value("${sonic.agent.port}")
    private int port;
    @Autowired
    private AgentManagerTool agentManagerTool;

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key)) || token.length() == 0) {
            log.info("Auth Failed!");
            return;
        }

        session.getUserProperties().put("udId", udId);
        boolean lockSuccess = DevicesLockMap.lockByUdId(udId, 30L, TimeUnit.SECONDS);
        if (!lockSuccess) {
            log.info("Fail to get device lock... please make sure device is not busy.");
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
            log.info("Target device is not connecting, please check the connection.");
            return;
        }
        saveUdIdMapAndSet(session, iDevice);

        AndroidAPKMap.getMap().put(udId, false);

        if (!AndroidDeviceBridgeTool.installSonicApk(iDevice)) {
            AndroidAPKMap.getMap().remove(udId);
            return;
        }

        AndroidDeviceBridgeTool.executeCommand(iDevice, "am start -n org.cloud.sonic.android/.SonicServiceActivity");
        AndroidAPKMap.getMap().put(udId, true);

        AndroidTouchHandler.startTouch(iDevice);

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
            case "startPerfmon" -> AndroidSupplyTool.startPerfmon(iDevice.getSerialNumber(), msg.getString("bundleId"), session, null, 1000);
            case "stopPerfmon" -> AndroidSupplyTool.stopPerfmon(iDevice.getSerialNumber());
            case "startKeyboard" -> {
                String currentIme = AndroidDeviceBridgeTool.executeCommand(iDevice, "settings get secure default_input_method");
                if (!currentIme.contains("org.cloud.sonic.android/.keyboard.SonicKeyboard")) {
                    AndroidDeviceBridgeTool.executeCommand(iDevice, "ime enable org.cloud.sonic.android/.keyboard.SonicKeyboard");
                    AndroidDeviceBridgeTool.executeCommand(iDevice, "ime set org.cloud.sonic.android/.keyboard.SonicKeyboard");
                }
            }
            case "stopKeyboard" -> AndroidDeviceBridgeTool.executeCommand(iDevice, "ime disable org.cloud.sonic.android/.keyboard.SonicKeyboard");
            case "clearProxy" -> AndroidDeviceBridgeTool.clearProxy(iDevice);
            case "proxy" -> {
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
            }
            case "installCert" -> AndroidDeviceBridgeTool.executeCommand(iDevice,
                    String.format("am start -a android.intent.action.VIEW -d http://%s:%d/assets/download", getHost(), port));
            case "forwardView" -> {
                JSONObject forwardView = new JSONObject();
                forwardView.put("msg", "forwardView");
                forwardView.put("detail", AndroidDeviceBridgeTool.getWebView(iDevice));
                BytesTool.sendText(session, forwardView.toJSONString());
            }
            case "find" -> AndroidDeviceBridgeTool.searchDevice(iDevice);
            case "battery" -> AndroidDeviceBridgeTool.controlBattery(iDevice, msg.getInteger("detail"));
            case "uninstallApp" -> {
                JSONObject result = new JSONObject();
                try {
                    AndroidDeviceBridgeTool.uninstall(iDevice, msg.getString("detail"));
                    result.put("detail", "success");
                } catch (InstallException e) {
                    result.put("detail", "fail");
                    e.printStackTrace();
                }
                result.put("msg", "uninstallFinish");
                BytesTool.sendText(session, result.toJSONString());
            }
            case "scan" -> AndroidDeviceBridgeTool.pushToCamera(iDevice, msg.getString("url"));
            case "text" -> AndroidDeviceBridgeTool.executeCommand(iDevice, "am broadcast -a SONIC_KEYBOARD --es msg \"" + msg.getString("detail") + "\"");
            case "touch" -> AndroidTouchHandler.writeToOutputStream(iDevice, msg.getString("detail"));
            case "keyEvent" -> AndroidDeviceBridgeTool.pressKey(iDevice, msg.getInteger("detail"));
            case "pullFile" -> {
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
            }
            case "pushFile" -> {
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
            }
            case "debug" -> {
                AndroidStepHandler androidStepHandler = HandlerMap.getAndroidMap().get(session.getId());
                switch (msg.getString("detail")) {
                    case "poco" -> AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
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
                    case "runStep" -> {
                        JSONObject jsonDebug = new JSONObject();
                        jsonDebug.put("msg", "findSteps");
                        jsonDebug.put("key", key);
                        jsonDebug.put("udId", iDevice.getSerialNumber());
                        jsonDebug.put("pwd", msg.getString("pwd"));
                        jsonDebug.put("sessionId", session.getId());
                        jsonDebug.put("caseId", msg.getInteger("caseId"));
                        TransportWorker.send(jsonDebug);
                    }
                    case "stopStep" -> TaskManager.forceStopDebugStepThread(
                            AndroidRunStepThread.ANDROID_RUN_STEP_TASK_PRE.formatted(
                                    0, msg.getInteger("caseId"), msg.getString("udId")
                            )
                    );
                    case "openApp" -> AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                        AndroidDeviceBridgeTool.activateApp(iDevice, msg.getString("pkg"));
                    });
                    case "tap" -> {
                        String xy = msg.getString("point");
                        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(iDevice, "input tap " + x + " " + y);
                    }
                    case "longPress" -> {
                        String xy = msg.getString("point");
                        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(iDevice, "input swipe " + x + " " + y + " " + x + " " + y + " 1500");
                    }
                    case "swipe" -> {
                        String xy1 = msg.getString("pointA");
                        String xy2 = msg.getString("pointB");
                        int x1 = Integer.parseInt(xy1.substring(0, xy1.indexOf(",")));
                        int y1 = Integer.parseInt(xy1.substring(xy1.indexOf(",") + 1));
                        int x2 = Integer.parseInt(xy2.substring(0, xy2.indexOf(",")));
                        int y2 = Integer.parseInt(xy2.substring(xy2.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(iDevice, "input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " 200");
                    }
                    case "install" -> AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                        JSONObject result = new JSONObject();
                        result.put("msg", "installFinish");
                        try {
                            File localFile = new File(msg.getString("apk"));
                            if (msg.getString("apk").contains("http")) {
                                localFile = DownloadTool.download(msg.getString("apk"));
                            }
                            AndroidDeviceBridgeTool.install(iDevice, localFile.getAbsolutePath());
                            result.put("status", "success");
                        } catch (IOException | InstallException e) {
                            result.put("status", "fail");
                            e.printStackTrace();
                        }
                        BytesTool.sendText(session, result.toJSONString());
                    });
                    case "openDriver" -> {
                        if (androidStepHandler == null || androidStepHandler.getAndroidDriver() == null) {
                            openDriver(iDevice, session);
                        }
                    }
                    case "closeDriver" -> {
                        if (androidStepHandler != null && androidStepHandler.getAndroidDriver() != null) {
                            androidStepHandler.closeAndroidDriver();
                            HandlerMap.getAndroidMap().remove(session.getId());
                        }
                    }
                    case "tree" -> {
                        if (androidStepHandler != null && androidStepHandler.getAndroidDriver() != null) {
                            AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                                try {
                                    JSONObject result = new JSONObject();
                                    androidStepHandler.switchWindowMode(new HandleContext(), msg.getBoolean("isMulti") != null && msg.getBoolean("isMulti"));
                                    androidStepHandler.switchVisibleMode(new HandleContext(), msg.getBoolean("isVisible") != null && msg.getBoolean("isVisible"));
                                    result.put("msg", "tree");
                                    result.put("detail", androidStepHandler.getResource());
                                    result.put("webView", androidStepHandler.getWebView());
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
                    }
                    case "eleScreen" -> {
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
                    }
                }
            }
        }
    }

    private void openDriver(IDevice iDevice, Session session) {
        synchronized (session) {
            AndroidStepHandler androidStepHandler = new AndroidStepHandler();
            androidStepHandler.setTestMode(0, 0, iDevice.getSerialNumber(), DeviceStatus.DEBUGGING, session.getId());
            JSONObject result = new JSONObject();
            AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                try {
                    AndroidDeviceLocalStatus.startDebug(iDevice.getSerialNumber());
                    int port = AndroidDeviceBridgeTool.startUiaServer(iDevice);
                    androidStepHandler.startAndroidDriver(iDevice, port);
                    result.put("status", "success");
                    result.put("detail", "初始化 UIAutomator2 Server 完成！");
                    HandlerMap.getAndroidMap().put(session.getId(), androidStepHandler);
                } catch (Exception e) {
                    log.error(e.getMessage());
                    result.put("status", "error");
                    result.put("detail", "初始化 UIAutomator2 Server 失败！");
                    androidStepHandler.closeAndroidDriver();
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
            log.info("close driver failed.");
        } finally {
            HandlerMap.getAndroidMap().remove(session.getId());
        }
        if (iDevice != null) {
            AndroidDeviceBridgeTool.clearProxy(iDevice);
            AndroidDeviceBridgeTool.clearWebView(iDevice);
            AndroidSupplyTool.stopShare(iDevice.getSerialNumber());
            AndroidSupplyTool.stopPerfmon(iDevice.getSerialNumber());
            SGMTool.stopProxy(iDevice.getSerialNumber());
            AndroidAPKMap.getMap().remove(iDevice.getSerialNumber());
            AndroidTouchHandler.stopTouch(iDevice);
        }
        removeUdIdMapAndSet(session);
        WebSocketSessionMap.removeSession(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("{} : quit.", session.getId());
    }
}
