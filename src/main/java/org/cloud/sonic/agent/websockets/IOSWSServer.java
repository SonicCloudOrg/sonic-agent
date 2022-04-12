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
import io.appium.java_client.TouchAction;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.PointOption;
import org.cloud.sonic.agent.automation.AppiumServer;
import org.cloud.sonic.agent.automation.HandleDes;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.bridge.ios.IOSDeviceLocalStatus;
import org.cloud.sonic.agent.bridge.ios.IOSDeviceThreadPool;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.maps.DevicesLockMap;
import org.cloud.sonic.agent.common.maps.HandlerMap;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.ios.IOSRunStepThread;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.SGMTool;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.openqa.selenium.OutputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@ServerEndpoint(value = "/websockets/ios/{key}/{udId}/{token}", configurator = MyEndpointConfigure.class)
public class IOSWSServer implements IIOSWSServer {
    private final Logger logger = LoggerFactory.getLogger(IOSWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    @Value("${sonic.agent.host}")
    private String host;
    @Value("${sonic.agent.port}")
    private int port;
    @Autowired private AgentManagerTool agentManagerTool;

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
        logger.info("ios上锁udId：{}", udId);
        IOSDeviceLocalStatus.startDebug(udId);

        // 更新使用用户
        agentManagerTool.updateDebugUser(udId, token);

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

        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
            IOSStepHandler iosStepHandler = new IOSStepHandler();
            iosStepHandler.setTestMode(0, 0, udId, DeviceStatus.DEBUGGING, session.getId());
            JSONObject result = new JSONObject();
            try {
                iosStepHandler.startIOSDriver(udId, ports[0]);
                result.put("status", "success");
                result.put("width", iosStepHandler.getDriver().manage().window().getSize().width);
                result.put("height", iosStepHandler.getDriver().manage().window().getSize().height);
                result.put("detail", "初始化Driver完成！");
                HandlerMap.getIOSMap().put(session.getId(), iosStepHandler);
                JSONObject port = new JSONObject();
                port.put("port", AppiumServer.serviceMap.get(udId).getUrl().getPort());
                port.put("msg", "appiumPort");
                BytesTool.sendText(session, port.toJSONString());
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
            logger.info("ios解锁udId：{}", udId);
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
    public void onMessage(String message, Session session) throws InterruptedException {
        JSONObject msg = JSON.parseObject(message);
        logger.info(session.getId() + " 发送 " + msg);
        String udId = udIdMap.get(session);
        switch (msg.getString("type")) {
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
                BytesTool.sendText(session, proxy.toJSONString());
                break;
            }
            case "installCert": {
                IOSStepHandler iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
                if (iosStepHandler == null || iosStepHandler.getDriver() == null) {
                    break;
                }
                iosStepHandler.getDriver().activateApp("com.apple.mobilesafari");
                break;
            }
            case "appList":
                JSONObject appList = SibTool.getAppList(udId);
                if (appList.get("appList") != null) {
                    appList.put("msg", "appListDetail");
                    sendText(session, appList.toJSONString());
                }
                break;
            case "launch":
                SibTool.launch(udId, msg.getString("pkg"));
                break;
            case "uninstallApp":
                SibTool.uninstall(udId, msg.getString("detail"));
                break;
            case "debug":
                if (msg.getString("detail").equals("runStep")) {
                    JSONObject steps = agentManagerTool.findSteps(
                            msg.getInteger("caseId"),
                            session.getId(),
                            "",
                            udId
                    );
                    agentManagerTool.runIOSStep(steps);
                } else if (msg.getString("detail").equals("stopStep")) {
                    TaskManager.forceStopDebugStepThread(
                            IOSRunStepThread.IOS_RUN_STEP_TASK_PRE.formatted(
                                    0, msg.getInteger("caseId"), msg.getString("udId")
                            )
                    );
                } else {
                    IOSStepHandler iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
                    if (iosStepHandler == null || iosStepHandler.getDriver() == null) {
                        break;
                    }
                    try {
                        if (msg.getString("detail").equals("tap")) {
                            TouchAction ta = new TouchAction(iosStepHandler.getDriver());
                            String xy = msg.getString("point");
                            int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                            int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                            ta.tap(PointOption.point(x, y)).perform();
                        }
                        if (msg.getString("detail").equals("longPress")) {
                            TouchAction ta = new TouchAction(iosStepHandler.getDriver());
                            String xy = msg.getString("point");
                            int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                            int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                            ta.longPress(PointOption.point(x, y)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(1500))).release().perform();
                        }
                        if (msg.getString("detail").equals("swipe")) {
                            TouchAction ta = new TouchAction(iosStepHandler.getDriver());
                            String xy1 = msg.getString("pointA");
                            String xy2 = msg.getString("pointB");
                            int x1 = Integer.parseInt(xy1.substring(0, xy1.indexOf(",")));
                            int y1 = Integer.parseInt(xy1.substring(xy1.indexOf(",") + 1));
                            int x2 = Integer.parseInt(xy2.substring(0, xy2.indexOf(",")));
                            int y2 = Integer.parseInt(xy2.substring(xy2.indexOf(",") + 1));
                            ta.press(PointOption.point(x1, y1)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(300))).moveTo(PointOption.point(x2, y2)).release().perform();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (msg.getString("detail").equals("keyEvent")) {
                        try {
                            if (msg.getString("key").equals("home") || msg.getString("key").equals("volumeup") || msg.getString("key").equals("volumedown")) {
                                iosStepHandler.getDriver().executeScript("mobile:pressButton", JSON.parse("{name: \"" + msg.getString("key") + "\"}"));
                            } else if (msg.getString("key").equals("lock")) {
                                if (iosStepHandler.getDriver().isDeviceLocked()) {
                                    iosStepHandler.unLock(new HandleDes());
                                } else {
                                    iosStepHandler.lock(new HandleDes());
                                }
                            } else {
                                iosStepHandler.openApp(new HandleDes(), "com.apple." + msg.getString("key"));
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    if (msg.getString("detail").equals("siri")) {
                        IOSStepHandler finalIOSStepHandler = iosStepHandler;
                        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
                            finalIOSStepHandler.siriCommand(new HandleDes(), msg.getString("command"));
                        });
                    }
                    if (msg.getString("detail").equals("install")) {
                        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
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
                        });
                    }
                    if (msg.getString("detail").equals("tree")) {
                        IOSStepHandler finalIOSStepHandler = iosStepHandler;
                        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
                            try {
                                JSONObject result = new JSONObject();
                                result.put("msg", "tree");
                                result.put("detail", finalIOSStepHandler.getResource());
                                HandleDes handleDes = new HandleDes();
                                result.put("img", finalIOSStepHandler.stepScreen(handleDes));
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
                        });
                    }
                    if (msg.getString("detail").equals("eleScreen")) {
                        IOSStepHandler finalIOSStepHandler = iosStepHandler;
                        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
                            JSONObject result = new JSONObject();
                            result.put("msg", "eleScreen");
                            try {
                                result.put("img", UploadTools.upload(finalIOSStepHandler.findEle("xpath", msg.getString("xpath")).getScreenshotAs(OutputType.FILE), "keepFiles"));
                            } catch (Exception e) {
                                result.put("errMsg", "获取元素截图失败！");
                            }
                            sendText(session, result.toJSONString());
                        });
                    }
                }
                break;
        }
    }

    private void sendText(Session session, String message) {
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IllegalStateException | IOException e) {
                logger.error("webSocket发送失败!连接已关闭！");
            }
        }
    }

    private void exit(Session session) {
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
        logger.info(session.getId() + "退出");
    }
}
