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
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.bridge.ios.IOSDeviceLocalStatus;
import org.cloud.sonic.agent.bridge.ios.IOSDeviceThreadPool;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.maps.DevicesLockMap;
import org.cloud.sonic.agent.common.maps.HandlerMap;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.models.HandleDes;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.ios.IOSRunStepThread;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.SGMTool;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.cloud.sonic.core.ios.IOSDriver;
import org.cloud.sonic.core.ios.RespHandler;
import org.cloud.sonic.core.ios.enums.PasteboardType;
import org.cloud.sonic.core.ios.enums.SystemButton;
import org.cloud.sonic.core.tool.SonicRespException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import static org.cloud.sonic.agent.tools.BytesTool.sendText;

@Component
@ServerEndpoint(value = "/websockets/ios/{key}/{udId}/{token}", configurator = WsEndpointConfigure.class)
public class IOSWSServer implements IIOSWSServer {
    private final Logger logger = LoggerFactory.getLogger(IOSWSServer.class);
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
            logger.info("拦截访问！");
            return;
        }

        session.getUserProperties().put("udId", udId);
        boolean lockSuccess = DevicesLockMap.lockByUdId(udId, 30L, TimeUnit.SECONDS);
        if (!lockSuccess) {
            logger.info("30s内获取设备锁失败，请确保设备无人使用");
            return;
        }
        logger.info("ios lock udId：{}", udId);
        IOSDeviceLocalStatus.startDebug(udId);

        // 更新使用用户
        JSONObject jsonDebug = new JSONObject();
        jsonDebug.put("msg", "debugUser");
        jsonDebug.put("token", token);
        jsonDebug.put("udId", udId);
        TransportWorker.send(jsonDebug);

        WebSocketSessionMap.addSession(session);
        if (!SibTool.getDeviceList().contains(udId)) {
            logger.info("设备未连接，请检查！");
            return;
        }
        saveUdIdMapAndSet(session, udId);
        int[] ports = SibTool.startWda(udId);
        JSONObject picFinish = new JSONObject();
        picFinish.put("msg", "picFinish");
        picFinish.put("wda", ports[0]);
        picFinish.put("port", ports[1]);
        sendText(session, picFinish.toJSONString());
        if (ports[0] != 0) {
            SibTool.orientationWatcher(udId, session);
        }

        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
            IOSStepHandler iosStepHandler = new IOSStepHandler();
            iosStepHandler.setTestMode(0, 0, udId, DeviceStatus.DEBUGGING, session.getId());
            JSONObject result = new JSONObject();
            try {
                iosStepHandler.startIOSDriver(udId, ports[0]);
                result.put("status", "success");
                result.put("width", iosStepHandler.getDriver().getWindowSize().getWidth());
                result.put("height", iosStepHandler.getDriver().getWindowSize().getHeight());
                result.put("detail", "初始化Driver完成！");
                JSONObject appiumSettings = new JSONObject();
                appiumSettings.put("mjpegServerFramerate", 100);
                appiumSettings.put("mjpegScalingFactor", 100);
                appiumSettings.put("mjpegServerScreenshotQuality", 25);
                iosStepHandler.appiumSettings(appiumSettings);
                HandlerMap.getIOSMap().put(session.getId(), iosStepHandler);
                JSONObject port = new JSONObject();
                port.put("port", 0);
                port.put("msg", "appiumPort");
                sendText(session, port.toJSONString());
            } catch (Exception e) {
                logger.error(e.getMessage());
                result.put("status", "error");
                result.put("detail", "初始化Driver失败！部分功能不可用！请联系管理员");
                iosStepHandler.closeIOSDriver();
            } finally {
                result.put("msg", "openDriver");
                sendText(session, result.toJSONString());
            }
        });
    }

    @OnClose
    public void onClose(Session session) {
        String udId = (String) session.getUserProperties().get("udId");
        try {
            exit(session);
        } finally {
            DevicesLockMap.unlockAndRemoveByUdId(udId);
            logger.info("ios unlock udId：{}", udId);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.error(error.getMessage());
        JSONObject errMsg = new JSONObject();
        errMsg.put("msg", "error");
        sendText(session, errMsg.toJSONString());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        JSONObject msg = JSON.parseObject(message);
        logger.info("{} send: {}", session.getId(), msg);
        String udId = udIdMap.get(session);
        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
            IOSDriver iosDriver = null;
            IOSStepHandler iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
            if (iosStepHandler != null && iosStepHandler.getDriver() != null) {
                iosDriver = iosStepHandler.getDriver();
            }
            switch (msg.getString("type")) {
                case "screen": {
                    JSONObject appiumSettings = new JSONObject();
                    if ("low".equals(msg.getString("detail"))) {
                        appiumSettings.put("mjpegServerFramerate", 50);
                        appiumSettings.put("mjpegScalingFactor", 50);
                        appiumSettings.put("mjpegServerScreenshotQuality", 10);
                    } else {
                        appiumSettings.put("mjpegServerFramerate", 100);
                        appiumSettings.put("mjpegScalingFactor", 100);
                        appiumSettings.put("mjpegServerScreenshotQuality", 25);
                    }
                    try {
                        iosStepHandler.appiumSettings(appiumSettings);
                    } catch (SonicRespException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "setPasteboard": {
                    if (iosDriver != null) {
                        try {
                            iosDriver.appActivate("com.apple.springboard");
                            iosDriver.sendSiriCommand("open WebDriverAgentRunner-Runner");
                            Thread.sleep(2000);
                            iosDriver.setPasteboard(PasteboardType.PLAIN_TEXT, msg.getString("detail"));
                            iosDriver.pressButton(SystemButton.HOME);
                            JSONObject setPaste = new JSONObject();
                            setPaste.put("msg", "setPaste");
                            sendText(session, setPaste.toJSONString());
                        } catch (SonicRespException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
                case "getPasteboard": {
                    if (iosDriver != null) {
                        try {
                            iosDriver.appActivate("com.apple.springboard");
                            iosDriver.sendSiriCommand("open WebDriverAgentRunner-Runner");
                            Thread.sleep(2000);
                            JSONObject paste = new JSONObject();
                            paste.put("msg", "paste");
                            paste.put("detail", new String(iosDriver.getPasteboard(PasteboardType.PLAIN_TEXT), StandardCharsets.UTF_8));
                            sendText(session, paste.toJSONString());
                            iosDriver.pressButton(SystemButton.HOME);
                        } catch (SonicRespException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
                case "send": {
                    RespHandler sendHandler = new RespHandler();
                    sendHandler.setRequestTimeOut(2000);
                    RespHandler respHandler = iosDriver.getWdaClient().getRespHandler();
                    iosDriver.getWdaClient().setRespHandler(sendHandler);
                    try {
                        iosDriver.sendKeys(msg.getString("detail"));
                    } catch (SonicRespException e) {
                        e.printStackTrace();
                    } finally {
                        iosDriver.getWdaClient().setRespHandler(respHandler);
                    }
                    break;
                }
                case "location": {
                    if ("set".equals(msg.getString("detail"))) {
                        SibTool.locationSet(udId, msg.getString("long"), msg.getString("lat"));
                    } else {
                        SibTool.locationUnset(udId);
                    }
                    break;
                }
                case "proxy": {
                    Socket portSocket = PortTool.getBindSocket();
                    Socket webPortSocket = PortTool.getBindSocket();
                    int pPort = PortTool.releaseAndGetPort(portSocket);
                    int webPort = PortTool.releaseAndGetPort(webPortSocket);
                    SGMTool.startProxy(udId, SGMTool.getCommand(pPort, webPort));
                    JSONObject proxy = new JSONObject();
                    proxy.put("webPort", webPort);
                    proxy.put("port", pPort);
                    proxy.put("msg", "proxyResult");
                    sendText(session, proxy.toJSONString());
                    break;
                }
                case "installCert":
                    SibTool.launch(udId, "com.apple.mobilesafari");
                    break;
                case "launch":
                    SibTool.launch(udId, msg.getString("pkg"));
                    break;
                case "uninstallApp":
                    SibTool.uninstall(udId, msg.getString("detail"));
                    break;
                case "debug":
                    switch (msg.getString("detail")) {
                        case "runStep": {
                            JSONObject jsonDebug = new JSONObject();
                            jsonDebug.put("msg", "findSteps");
                            jsonDebug.put("key", key);
                            jsonDebug.put("udId", udId);
                            jsonDebug.put("sessionId", session.getId());
                            jsonDebug.put("caseId", msg.getInteger("caseId"));
                            TransportWorker.send(jsonDebug);
                            break;
                        }
                        case "stopStep": {
                            TaskManager.forceStopDebugStepThread(
                                    IOSRunStepThread.IOS_RUN_STEP_TASK_PRE.formatted(
                                            0, msg.getInteger("caseId"), msg.getString("udId")
                                    )
                            );
                            break;
                        }
                        case "tap": {
                            if (iosDriver != null) {
                                String xy = msg.getString("point");
                                int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                                int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                                try {
                                    iosDriver.tap(x, y);
                                } catch (SonicRespException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        }
                        case "longPress": {
                            if (iosDriver != null) {
                                String xy = msg.getString("point");
                                int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                                int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                                try {
                                    iosDriver.longPress(x, y, 1500);
                                } catch (SonicRespException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        }
                        case "swipe": {
                            if (iosDriver != null) {
                                String xy1 = msg.getString("pointA");
                                String xy2 = msg.getString("pointB");
                                int x1 = Integer.parseInt(xy1.substring(0, xy1.indexOf(",")));
                                int y1 = Integer.parseInt(xy1.substring(xy1.indexOf(",") + 1));
                                int x2 = Integer.parseInt(xy2.substring(0, xy2.indexOf(",")));
                                int y2 = Integer.parseInt(xy2.substring(xy2.indexOf(",") + 1));
                                try {
                                    iosDriver.swipe(x1, y1, x2, y2);
                                } catch (SonicRespException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        }
                        case "keyEvent": {
                            if (iosDriver != null) {
                                try {
                                    if ("home".equals(msg.getString("key")) || "volumeup".equals(msg.getString("key")) || "volumedown".equals(msg.getString("key"))) {
                                        iosDriver.pressButton(msg.getString("key"));
                                    } else if ("lock".equals(msg.getString("key"))) {
                                        if (iosDriver.isLocked()) {
                                            iosDriver.unlock();
                                        } else {
                                            iosDriver.lock();
                                        }
                                    } else {
                                        iosDriver.appActivate("com.apple." + msg.getString("key"));
                                    }
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        }
                        case "siri": {
                            if (iosDriver != null) {
                                try {
                                    iosDriver.sendSiriCommand(msg.getString("command"));
                                } catch (SonicRespException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        }
                        case "tree": {
                            if (iosDriver != null) {
                                try {
                                    JSONObject result = new JSONObject();
                                    result.put("msg", "tree");
                                    result.put("detail", iosStepHandler.getResource());
                                    HandleDes handleDes = new HandleDes();
                                    result.put("img", iosStepHandler.stepScreen(handleDes));
                                    if (handleDes.getE() != null) {
                                        logger.error(handleDes.getE().getMessage());
                                        JSONObject resultFail = new JSONObject();
                                        resultFail.put("msg", "treeFail");
                                        sendText(session, resultFail.toJSONString());
                                    } else {
                                        sendText(session, result.toJSONString());
                                    }
                                } catch (Throwable e) {
                                    logger.error(e.getMessage());
                                    JSONObject result = new JSONObject();
                                    result.put("msg", "treeFail");
                                    sendText(session, result.toJSONString());
                                }
                            }
                            break;
                        }
                        case "eleScreen": {
                            if (iosDriver != null) {
                                JSONObject result = new JSONObject();
                                result.put("msg", "eleScreen");
                                try {
                                    File folder = new File("test-output");
                                    if (!folder.exists()) {
                                        folder.mkdirs();
                                    }
                                    File output = new File(folder + File.separator + udId + Calendar.getInstance().getTimeInMillis() + ".png");
                                    try {
                                        byte[] bt = iosStepHandler.findEle("xpath", msg.getString("xpath")).screenshot();
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
                                sendText(session, result.toJSONString());
                            }
                            break;
                        }
                        case "install": {
                            JSONObject result = new JSONObject();
                            result.put("msg", "installFinish");
                            try {
                                File localFile = DownloadTool.download(msg.getString("ipa"));
                                SibTool.install(udId, localFile.getAbsolutePath());
                                result.put("status", "success");
                            } catch (IOException e) {
                                result.put("status", "fail");
                                e.printStackTrace();
                            }
                            sendText(session, result.toJSONString());
                            break;
                        }
                        default:
                    }
                    break;
                default:
            }
        });
    }

    private void exit(Session session) {
        SibTool.stopOrientationWatcher(udIdMap.get(session));
        try {
            HandlerMap.getIOSMap().get(session.getId()).closeIOSDriver();
        } catch (Exception e) {
            logger.info("关闭driver异常!");
        } finally {
            HandlerMap.getIOSMap().remove(session.getId());
        }
        SGMTool.stopProxy(udIdMap.get(session));
        IOSDeviceLocalStatus.finish(session.getUserProperties().get("udId") + "");
        WebSocketSessionMap.removeSession(session);
        removeUdIdMapAndSet(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("{} : quit.", session.getId());
    }
}
