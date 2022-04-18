/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.*;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.automation.AppiumServer;
import org.cloud.sonic.agent.automation.HandleDes;
import org.cloud.sonic.agent.automation.RemoteDebugDriver;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.*;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.android.AndroidRunStepThread;
import org.cloud.sonic.agent.tools.*;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.cloud.sonic.agent.tools.file.ZipTool;
import org.cloud.sonic.agent.tools.poco.PocoTool;
import org.openqa.selenium.OutputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
@ServerEndpoint(value = "/websockets/android/{key}/{udId}/{token}", configurator = MyEndpointConfigure.class)
public class AndroidWSServer implements IAndroidWSServer {

    private final Logger logger = LoggerFactory.getLogger(AndroidWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    @Value("${sonic.agent.port}")
    private int port;
    @Value("${modules.android.use-adbkit}")
    private boolean isEnableAdbKit;
    @Value("${modules.appium.enable}")
    private boolean isEnableAppium;
    private Map<IDevice, List<JSONObject>> webViewForwardMap = new ConcurrentHashMap<>();
    private Map<Session, OutputStream> outputMap = new ConcurrentHashMap<>();
    private List<Session> NotStopSession = new ArrayList<>();
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private AgentManagerTool agentManagerTool;

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key)) || token.length() == 0) {
            logger.info("拦截访问！");
            return;
        }

        session.getUserProperties().put("udId", udId);
        boolean lockSuccess = DevicesLockMap.lockByUdId(udId, 30L, TimeUnit.SECONDS);
        if (!lockSuccess) {
            logger.info("30s内获取设备锁失败，请确保设备无人使用");
            return;
        }
        logger.info("android上锁udId：{}", udId);
        AndroidDeviceLocalStatus.startDebug(udId);

        // 更新使用用户
        agentManagerTool.updateDebugUser(udId, token);

        WebSocketSessionMap.addSession(session);
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        if (iDevice == null) {
            logger.info("设备未连接，请检查！");
            return;
        }
        saveUdIdMapAndSet(session, iDevice);

        AndroidAPKMap.getMap().put(udId, false);
        String path = AndroidDeviceBridgeTool.executeCommand(iDevice, "pm path org.cloud.sonic.android").trim()
                .replaceAll("package:", "")
                .replaceAll("\n", "")
                .replaceAll("\t", "");
        if (path.length() > 0 && AndroidDeviceBridgeTool.checkSonicApkVersion(iDevice)) {
            logger.info("已安装Sonic插件，检查版本信息通过");
        } else {
            logger.info("未安装Sonic插件或版本不是最新，正在安装...");
            try {
                iDevice.uninstallPackage("org.cloud.sonic.android");
            } catch (InstallException e) {
                logger.info("卸载出错...");
            }
            try {
                iDevice.installPackage("plugins/sonic-android-apk.apk",
                        true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES
                        , "-r", "-t", "-g");
                logger.info("Sonic插件安装完毕");
            } catch (InstallException e) {
                e.printStackTrace();
                logger.info("Sonic插件安装失败！");
                return;
            }
            path = AndroidDeviceBridgeTool.executeCommand(iDevice, "pm path org.cloud.sonic.android").trim()
                    .replaceAll("package:", "")
                    .replaceAll("\n", "")
                    .replaceAll("\t", "");
        }
        AndroidAPKMap.getMap().put(udId, true);
        AndroidDeviceBridgeTool.pressKey(iDevice, 3);
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
                                logger.info(res);
                                if (res.contains("Address already in use")) {
                                    NotStopSession.add(session);
                                }
                                if (res.contains("starting：start()")) {
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
                logger.info("{} 设备touch服务启动异常！"
                        , iDevice.getSerialNumber());
                logger.error(e.getMessage());
            }
            System.out.println("end");
        });
        touchPro.start();

        int finalTouchPort = PortTool.getPort();
        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
            int wait = 0;
            while (!isTouchFinish.tryAcquire()) {
                wait++;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 超时就不继续等了，保证其它服务可运行
                if (wait > 20) {
                    return;
                }
            }
            AndroidDeviceBridgeTool.forward(iDevice, finalTouchPort, "sonictouchservice");
            Socket touchSocket = null;
            OutputStream outputStream = null;
            try {
                touchSocket = new Socket("localhost", finalTouchPort);
                outputStream = touchSocket.getOutputStream();
                outputMap.put(session, outputStream);
                while (outputMap.get(session) != null && (touchPro.isAlive())) {
                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (NotStopSession.contains(session)) {
                    try {
                        outputStream.write("r\n".getBytes());
                        outputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        NotStopSession.remove(session);
                    }
                }
                if (touchPro.isAlive()) {
                    touchPro.interrupt();
                    logger.info("touch thread已关闭");
                }
                if (touchSocket != null && touchSocket.isConnected()) {
                    try {
                        touchSocket.close();
                        logger.info("touch socket已关闭");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (outputStream != null) {
                    try {
                        outputStream.close();
                        logger.info("touch output流已关闭");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            AndroidDeviceBridgeTool.removeForward(iDevice, finalTouchPort, "sonictouchservice");
        });

        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> AndroidDeviceBridgeTool.pushYadb(iDevice));

        if (isEnableAdbKit) {
            String processName = String.format("process-%s-adbkit", udId);
            if (GlobalProcessMap.getMap().get(processName) != null) {
                Process ps = GlobalProcessMap.getMap().get(processName);
                ps.children().forEach(ProcessHandle::destroy);
                ps.destroy();
            }
            try {
                String system = System.getProperty("os.name").toLowerCase();
                Process ps = null;
                int port = PortTool.getPort();
                String command = String.format("adbkit usb-device-to-tcp -p %d %s", port, udId);
                if (system.contains("win")) {
                    ps = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
                } else if (system.contains("linux") || system.contains("mac")) {
                    ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                }
                GlobalProcessMap.getMap().put(processName, ps);
                JSONObject adbkit = new JSONObject();
                adbkit.put("msg", "adbkit");
                adbkit.put("isEnable", true);
                adbkit.put("port", port);
                BytesTool.sendText(session, adbkit.toJSONString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            JSONObject adbkit = new JSONObject();
            adbkit.put("msg", "adbkit");
            adbkit.put("isEnable", false);
            BytesTool.sendText(session, adbkit.toJSONString());
        }
    }

    @OnClose
    public void onClose(Session session) {
        String udId = (String) session.getUserProperties().get("udId");
        try {
            exit(session);
        } finally {
            DevicesLockMap.unlockAndRemoveByUdId(udId);
            logger.info("android解锁udId：{}", udId);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.error(error.getMessage());
        error.printStackTrace();
        JSONObject errMsg = new JSONObject();
        errMsg.put("msg", "error");
        BytesTool.sendText(session, errMsg.toJSONString());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        JSONObject msg = JSON.parseObject(message);
        logger.info(session.getId() + " 发送 " + msg);
        IDevice iDevice = udIdMap.get(session);
        switch (msg.getString("type")) {
            case "poco": {
                AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                    JSONObject poco = new JSONObject();
                    poco.put("result", PocoTool.getSocketResult(iDevice.getSerialNumber(), PlatformType.ANDROID, msg.getString("detail")));
                    poco.put("msg", "poco");
                    BytesTool.sendText(session, poco.toJSONString());
                });
                break;
            }
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
                List<String> wList = Arrays.asList("webview", "WebView", "chrome_devtools_remote", "Terrace_devtools_remote");
                List<String> webViewList = new ArrayList<>();
                for (String w : wList) {
                    webViewList.addAll(Arrays.asList(AndroidDeviceBridgeTool
                            .executeCommand(iDevice, "cat /proc/net/unix | grep " + w).split("\n")));
                }
                Set<String> webSet = new HashSet<>();
                for (String w : webViewList) {
                    if (w.contains("@") && w.indexOf("@") + 1 < w.length()) {
                        webSet.add(w.substring(w.indexOf("@") + 1));
                    }
                }
                List<JSONObject> has = webViewForwardMap.get(iDevice);
                if (has != null && has.size() > 0) {
                    for (JSONObject j : has) {
                        AndroidDeviceBridgeTool.removeForward(iDevice, j.getInteger("port"), j.getString("name"));
                    }
                }
                has = new ArrayList<>();
                List<JSONObject> result = new ArrayList<>();
                if (webViewList.size() > 0) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("Content-Type", "application/json");
                    for (String ws : webSet) {
                        int port = PortTool.getPort();
                        AndroidDeviceBridgeTool.forward(iDevice, port, ws);
                        JSONObject j = new JSONObject();
                        j.put("port", port);
                        j.put("name", ws);
                        has.add(j);
                        JSONObject r = new JSONObject();
                        r.put("port", port);
                        try {
                            ResponseEntity<LinkedHashMap> infoEntity =
                                    restTemplate.exchange("http://localhost:" + port + "/json/version", HttpMethod.GET, new HttpEntity(headers), LinkedHashMap.class);
                            if (infoEntity.getStatusCode() == HttpStatus.OK) {
                                r.put("version", infoEntity.getBody().get("Browser"));
                                r.put("package", infoEntity.getBody().get("Android-Package"));
                            }
                        } catch (Exception e) {
                            continue;
                        }
                        ResponseEntity<JSONArray> responseEntity =
                                restTemplate.exchange("http://localhost:" + port + "/json/list", HttpMethod.GET, new HttpEntity(headers), JSONArray.class);
                        if (responseEntity.getStatusCode() == HttpStatus.OK) {
                            List<JSONObject> child = new ArrayList<>();
                            for (Object e : responseEntity.getBody()) {
                                LinkedHashMap objE = (LinkedHashMap) e;
                                JSONObject c = new JSONObject();
                                c.put("favicon", objE.get("faviconUrl"));
                                c.put("title", objE.get("title"));
                                c.put("url", objE.get("url"));
                                c.put("id", objE.get("id"));
                                child.add(c);
                            }
                            r.put("children", child);
                            result.add(r);
                        }
                    }
                    webViewForwardMap.put(iDevice, has);
                }
                forwardView.put("msg", "forwardView");
                if (RemoteDebugDriver.webDriver == null) {
                    RemoteDebugDriver.startChromeDriver();
                }
                forwardView.put("chromePort", RemoteDebugDriver.chromePort);
                forwardView.put("detail", result);
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
                ProcessCommandTool.getProcessLocalCommand("adb -s " + iDevice.getSerialNumber()
                        + " shell app_process -Djava.class.path=/data/local/tmp/yadb /data/local/tmp com.ysbing.yadb.Main -keyboard " + msg.getString("detail"));
                break;
            case "touch":
                OutputStream outputStream = outputMap.get(session);
                if (outputStream != null) {
                    try {
                        outputStream.write(msg.getString("detail").getBytes());
                        outputStream.flush();
                        outputStream.write("c\n".getBytes());
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
                String filename = "test-output" + File.separator + "pull-" + UUID.randomUUID();
                File file = new File(filename);
                try {
                    file.mkdirs();
                    iDevice.pullFile(msg.getString("path"), file.getAbsolutePath());
                    File re = new File(filename + ".zip");
                    ZipTool.zip(re, file);
                    String url = UploadTools.upload(re, "packageFiles");
                    result.put("status", "success");
                    result.put("url", url);
                } catch (IOException | AdbCommandRejectedException | SyncException | TimeoutException e) {
                    result.put("status", "fail");
                    e.printStackTrace();
                } finally {
                    deleteDir(file);
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
                if (msg.getString("detail").equals("runStep")) {
                    JSONObject steps = agentManagerTool.findSteps(
                            msg.getInteger("caseId"),
                            session.getId(),
                            msg.getString("pwd"),
                            iDevice.getSerialNumber()
                    );
                    agentManagerTool.runAndroidStep(steps);
                } else if (msg.getString("detail").equals("stopStep")) {
                    TaskManager.forceStopDebugStepThread(
                            AndroidRunStepThread.ANDROID_RUN_STEP_TASK_PRE.formatted(
                                    0, msg.getInteger("caseId"), msg.getString("udId")
                            )
                    );
                } else if (msg.getString("detail").equals("openApp")) {
                    AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                        AndroidDeviceBridgeTool.executeCommand(iDevice,
                                String.format("monkey -p %s -c android.intent.category.LAUNCHER 1", msg.getString("pkg")));
                    });
                } else {
                    AndroidStepHandler androidStepHandler = HandlerMap.getAndroidMap().get(session.getId());
                    if (androidStepHandler == null || androidStepHandler.getAndroidDriver() == null) {
                        if (msg.getString("detail").equals("openDriver")) {
                            if (isEnableAppium) {
                                androidStepHandler = new AndroidStepHandler();
                                androidStepHandler.setTestMode(0, 0, iDevice.getSerialNumber(), DeviceStatus.DEBUGGING, session.getId());
                                JSONObject result = new JSONObject();
                                AndroidStepHandler finalAndroidStepHandler1 = androidStepHandler;
                                AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                                    try {
                                        AndroidDeviceLocalStatus.startDebug(iDevice.getSerialNumber());
                                        finalAndroidStepHandler1.startAndroidDriver(iDevice.getSerialNumber());
                                        result.put("status", "success");
                                        result.put("detail", "初始化Driver完成！");
                                        HandlerMap.getAndroidMap().put(session.getId(), finalAndroidStepHandler1);
                                        JSONObject port = new JSONObject();
                                        port.put("port", AppiumServer.serviceMap.get(iDevice.getSerialNumber()).getUrl().getPort());
                                        port.put("msg", "appiumPort");
                                        BytesTool.sendText(session, port.toJSONString());
                                    } catch (Exception e) {
                                        logger.error(e.getMessage());
                                        result.put("status", "error");
                                        result.put("detail", "初始化Driver失败！部分功能不可用！请联系管理员");
                                        finalAndroidStepHandler1.closeAndroidDriver();
                                    } finally {
                                        result.put("msg", "openDriver");
                                        BytesTool.sendText(session, result.toJSONString());
                                    }
                                });
                            }
                        }
                        break;
                    }
                    try {
                        if (msg.getString("detail").equals("tap")) {
                            String xy = msg.getString("point");
                            int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                            int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                            AndroidDeviceBridgeTool.executeCommand(iDevice, "input tap " + x + " " + y);
                        }
                        if (msg.getString("detail").equals("longPress")) {
                            String xy = msg.getString("point");
                            int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                            int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                            AndroidDeviceBridgeTool.executeCommand(iDevice, "input swipe " + x + " " + y + " " + x + " " + y + " 1500");
                        }
                        if (msg.getString("detail").equals("swipe")) {
                            String xy1 = msg.getString("pointA");
                            String xy2 = msg.getString("pointB");
                            int x1 = Integer.parseInt(xy1.substring(0, xy1.indexOf(",")));
                            int y1 = Integer.parseInt(xy1.substring(xy1.indexOf(",") + 1));
                            int x2 = Integer.parseInt(xy2.substring(0, xy2.indexOf(",")));
                            int y2 = Integer.parseInt(xy2.substring(xy2.indexOf(",") + 1));
                            AndroidDeviceBridgeTool.executeCommand(iDevice, "input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " 200");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (msg.getString("detail").equals("install")) {
                        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                            JSONObject result = new JSONObject();
                            result.put("msg", "installFinish");
                            try {
                                File localFile = DownloadTool.download(msg.getString("apk"));
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
                    }
                    if (msg.getString("detail").equals("tree")) {
                        AndroidStepHandler finalAndroidStepHandler = androidStepHandler;
                        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                            try {
                                JSONObject result = new JSONObject();
                                result.put("msg", "tree");
                                result.put("detail", finalAndroidStepHandler.getResource());
                                HandleDes handleDes = new HandleDes();
                                if (!msg.getBoolean("hasScreen")) {
                                    result.put("img", finalAndroidStepHandler.stepScreen(handleDes));
                                }
                                if (handleDes.getE() != null) {
                                    logger.error(handleDes.getE().getMessage());
                                    JSONObject resultFail = new JSONObject();
                                    resultFail.put("msg", "treeFail");
                                    BytesTool.sendText(session, resultFail.toJSONString());
                                } else {
                                    result.put("webView", finalAndroidStepHandler.getWebView());
                                    result.put("activity", finalAndroidStepHandler.getCurrentActivity());
                                    BytesTool.sendText(session, result.toJSONString());
                                }
                            } catch (Throwable e) {
                                logger.error(e.getMessage());
                                JSONObject result = new JSONObject();
                                result.put("msg", "treeFail");
                                BytesTool.sendText(session, result.toJSONString());
                            }
                        });
                    }
                    if (msg.getString("detail").equals("eleScreen")) {
                        AndroidStepHandler finalAndroidStepHandler = androidStepHandler;
                        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                            JSONObject result = new JSONObject();
                            result.put("msg", "eleScreen");
                            try {
                                result.put("img", UploadTools.upload(finalAndroidStepHandler.findEle("xpath", msg.getString("xpath")).getScreenshotAs(OutputType.FILE), "keepFiles"));
                            } catch (Exception e) {
                                result.put("errMsg", "获取元素截图失败！");
                            }
                            BytesTool.sendText(session, result.toJSONString());
                        });
                    }
                }
                break;
        }
    }

    private void exit(Session session) {
        AndroidDeviceLocalStatus.finish(session.getUserProperties().get("udId") + "");
        IDevice iDevice = udIdMap.get(session);
        try {
            HandlerMap.getAndroidMap().get(session.getId()).closeAndroidDriver();
        } catch (Exception e) {
            logger.info("关闭driver异常!");
        } finally {
            HandlerMap.getAndroidMap().remove(session.getId());
        }
        if (iDevice != null) {
            AndroidDeviceBridgeTool.clearProxy(iDevice);
            List<JSONObject> has = webViewForwardMap.get(iDevice);
            if (has != null && has.size() > 0) {
                for (JSONObject j : has) {
                    AndroidDeviceBridgeTool.removeForward(iDevice, j.getInteger("port"), j.getString("name"));
                }
            }
            webViewForwardMap.remove(iDevice);
            if (isEnableAdbKit) {
                String processName = String.format("process-%s-adbkit", iDevice.getSerialNumber());
                if (GlobalProcessMap.getMap().get(processName) != null) {
                    Process ps = GlobalProcessMap.getMap().get(processName);
                    ps.children().forEach(ProcessHandle::destroy);
                    ps.destroy();
                }
            }
            SGMTool.stopProxy(iDevice.getSerialNumber());
        }
        AndroidAPKMap.getMap().remove(iDevice.getSerialNumber());
        outputMap.remove(session);
        removeUdIdMapAndSet(session);
        WebSocketSessionMap.removeSession(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info(session.getId() + "退出");
    }

    public void deleteDir(File file) {
        if (!file.exists()) {
            logger.info("文件不存在");
            return;
        }
        File[] files = file.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                deleteDir(f);
            } else {
                f.delete();
            }
        }
        file.delete();
    }

}
