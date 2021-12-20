package com.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.automation.HandleDes;
import com.sonic.agent.automation.IOSStepHandler;
import com.sonic.agent.bridge.ios.IOSDeviceLocalStatus;
import com.sonic.agent.bridge.ios.IOSDeviceThreadPool;
import com.sonic.agent.bridge.ios.TIDeviceTool;
import com.sonic.agent.interfaces.DeviceStatus;
import com.sonic.agent.maps.DevicesLockMap;
import com.sonic.agent.maps.HandlerMap;
import com.sonic.agent.maps.WebSocketSessionMap;
import com.sonic.agent.netty.NettyThreadPool;
import com.sonic.agent.tools.UploadTools;
import io.appium.java_client.TouchAction;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.PointOption;
import org.openqa.selenium.OutputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@ServerEndpoint(value = "/websockets/ios/{key}/{udId}/{token}", configurator = MyEndpointConfigure.class)
public class IOSWSServer {
    private final Logger logger = LoggerFactory.getLogger(IOSWSServer.class);
    private Map<Session, String> udIdMap = new ConcurrentHashMap<>();
    @Value("${sonic.agent.key}")
    private String key;

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

        JSONObject jsonDebug = new JSONObject();
        jsonDebug.put("msg", "debugUser");
        jsonDebug.put("token", token);
        jsonDebug.put("udId", udId);
        NettyThreadPool.send(jsonDebug);
        WebSocketSessionMap.addSession(session);
        if (!TIDeviceTool.getDeviceList().contains(udId)) {
            logger.info("设备未连接，请检查！");
            return;
        }
        udIdMap.put(session, udId);
        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
            IOSStepHandler iosStepHandler = new IOSStepHandler();
            iosStepHandler.setTestMode(0, 0, udId, DeviceStatus.DEBUGGING, session.getId());
            JSONObject result = new JSONObject();
            try {
                IOSDeviceLocalStatus.startDebug(udId);
                int imgPort = iosStepHandler.startIOSDriver(udId);
                result.put("status", "success");
                result.put("port", imgPort);
                result.put("width", iosStepHandler.getDriver().manage().window().getSize().width);
                result.put("height", iosStepHandler.getDriver().manage().window().getSize().height);
                result.put("detail", "初始化Driver完成！");
                HandlerMap.getIOSMap().put(session.getId(), iosStepHandler);
            } catch (Exception e) {
                logger.error(e.getMessage());
                result.put("status", "error");
                result.put("detail", "初始化Driver失败！部分功能不可用！请联系管理员");
            } finally {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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
        switch (msg.getString("type")) {
            case "debug":
                IOSStepHandler iosStepHandler;
                try {
                    if (msg.getString("detail").equals("tap")) {
                        iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
                        TouchAction ta = new TouchAction(iosStepHandler.getDriver());
                        String xy = msg.getString("point");
                        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                        ta.tap(PointOption.point(x, y)).perform();
                    }
                    if (msg.getString("detail").equals("longPress")) {
                        iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
                        TouchAction ta = new TouchAction(iosStepHandler.getDriver());
                        String xy = msg.getString("point");
                        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                        ta.longPress(PointOption.point(x, y)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(1500))).release().perform();
                    }
                    if (msg.getString("detail").equals("swipe")) {
                        iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
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
                    iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
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
                    iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
                    IOSStepHandler finalIOSStepHandler = iosStepHandler;
                    IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
                        finalIOSStepHandler.siriCommand(new HandleDes(), msg.getString("command"));
                    });
                }
                if (msg.getString("detail").equals("install")) {
                    iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
                    IOSStepHandler finalIosStepHandler = iosStepHandler;
                    IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
                        JSONObject result = new JSONObject();
                        result.put("msg", "installFinish");
                        HandleDes handleDes = new HandleDes();
                        finalIosStepHandler.install(handleDes, msg.getString("ipa"));
                        if (handleDes.getE() == null) {
                            result.put("status", "success");
                        } else {
                            System.out.println(handleDes.getE());
                            result.put("status", "fail");
                        }
                        sendText(session, result.toJSONString());
                    });
                }
                if (msg.getString("detail").equals("tree")) {
                    iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
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
                    iosStepHandler = HandlerMap.getIOSMap().get(session.getId());
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
                if (msg.getString("detail").equals("runStep")) {
                    JSONObject jsonDebug = new JSONObject();
                    jsonDebug.put("msg", "findSteps");
                    jsonDebug.put("key", key);
                    jsonDebug.put("udId", udIdMap.get(session));
                    jsonDebug.put("sessionId", session.getId());
                    jsonDebug.put("caseId", msg.getInteger("caseId"));
                    NettyThreadPool.send(jsonDebug);
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
        IOSDeviceLocalStatus.finish(udIdMap.get(session));
        try {
            HandlerMap.getIOSMap().get(session.getId()).closeIOSDriver();
        } catch (Exception e) {
            logger.info("关闭driver异常!");
        } finally {
            HandlerMap.getIOSMap().remove(session.getId());
        }
        WebSocketSessionMap.removeSession(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info(session.getId() + "退出");
    }
}
