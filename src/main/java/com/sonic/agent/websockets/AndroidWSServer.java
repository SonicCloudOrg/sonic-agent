package com.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.automation.HandleDes;
import com.sonic.agent.automation.RemoteDebugDriver;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import com.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import com.sonic.agent.interfaces.DeviceStatus;
import com.sonic.agent.maps.HandlerMap;
import com.sonic.agent.maps.WebSocketSessionMap;
import com.sonic.agent.rabbitmq.RabbitMQThread;
import com.sonic.agent.tools.MiniCapTool;
import com.sonic.agent.tools.PortTool;
import com.sonic.agent.tools.UploadTools;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ServerEndpoint(value = "/websockets/android/{key}/{udId}", configurator = MyEndpointConfigure.class)
public class AndroidWSServer {
    private final Logger logger = LoggerFactory.getLogger(AndroidWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, IDevice> udIdMap = new ConcurrentHashMap<>();
    private Map<IDevice, List<JSONObject>> webViewForwardMap = new ConcurrentHashMap<>();
    private Map<Session, OutputStream> outputMap = new ConcurrentHashMap<>();
    private Map<Session, Future<?>> miniCapMap = new ConcurrentHashMap<>();
    @Autowired
    private RestTemplate restTemplate;

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey, @PathParam("udId") String udId) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key))) {
            logger.info("拦截访问！");
            return;
        }
        int devicePlatformVersion;
        WebSocketSessionMap.getMap().put(session.getId(), session);
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        String platformVersion = iDevice.getProperty(IDevice.PROP_BUILD_VERSION);
        if (platformVersion.indexOf(".") == -1) {
            devicePlatformVersion = Integer.parseInt(platformVersion.replace(" ", ""));
        } else {
            devicePlatformVersion = Integer.parseInt(platformVersion.substring(0, platformVersion.indexOf(".")));
        }
        if (iDevice == null) {
            logger.info("设备未连接，请检查！");
            return;
        }
        udIdMap.put(session, iDevice);

        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
            AndroidStepHandler androidStepHandler = new AndroidStepHandler();
            androidStepHandler.setTestMode(0, 0, udId, DeviceStatus.DEBUGGING, session.getId());
            JSONObject result = new JSONObject();
            try {
                AndroidDeviceLocalStatus.startDebug(udId);
                androidStepHandler.startAndroidDriver(udId);
                result.put("status", "success");
                result.put("detail", "初始化Driver完成！");
                HandlerMap.getAndroidMap().put(session.getId(), androidStepHandler);
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

        AndroidDeviceBridgeTool.screen(iDevice, "abort");
        MiniCapTool miniCapTool = new MiniCapTool();
        AtomicReference<String[]> banner = new AtomicReference<>(new String[24]);
        Future<?> miniCapThread = miniCapTool.start(udId, banner, null, "middle", session);
        miniCapMap.put(session, miniCapThread);

        if (devicePlatformVersion < 9) {
            int finalMiniTouchPort = PortTool.getPort();
            Future<?> miniTouchPro = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> AndroidDeviceBridgeTool.miniTouchStart(iDevice));
            AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                AndroidDeviceBridgeTool.forward(iDevice, finalMiniTouchPort, "minitouch");
                Socket touchSocket = null;
                OutputStream outputStream = null;
                try {
                    touchSocket = new Socket("localhost", finalMiniTouchPort);
                    outputStream = touchSocket.getOutputStream();
                    outputMap.put(session, outputStream);
                    while (outputMap.get(session) != null && (!miniTouchPro.isDone())) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (!miniTouchPro.isDone()) {
                        miniTouchPro.cancel(true);
                        logger.info("miniTouch thread已关闭");
                    }
                    if (touchSocket != null && touchSocket.isConnected()) {
                        try {
                            touchSocket.close();
                            logger.info("miniTouch socket已关闭");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                            logger.info("miniTouch output流已关闭");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                AndroidDeviceBridgeTool.removeForward(iDevice, finalMiniTouchPort, "minitouch");
            });
        }
    }

    @OnClose
    public void onClose(Session session) {
        exit(session);
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
            case "forwardView": {
                JSONObject forwardView = new JSONObject();
                IDevice iDevice = udIdMap.get(session);
                List<String> webViewList = Arrays.asList(AndroidDeviceBridgeTool
                        .executeCommand(iDevice, "cat /proc/net/unix | grep webview").split("\n"));
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
                        ResponseEntity<LinkedHashMap> infoEntity =
                                restTemplate.exchange("http://localhost:" + port + "/json/version", HttpMethod.GET, new HttpEntity(headers), LinkedHashMap.class);
                        if (infoEntity.getStatusCode() == HttpStatus.OK) {
                            r.put("version", infoEntity.getBody().get("Browser"));
                            r.put("package", infoEntity.getBody().get("Android-Package"));
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
                if(RemoteDebugDriver.webDriver==null){
                    RemoteDebugDriver.startChromeDriver();
                }
                forwardView.put("detail", result);
                sendText(session, forwardView.toJSONString());
                break;
            }
            case "text":
                AndroidDeviceBridgeTool.executeCommand(udIdMap.get(session), "input text " + msg.getString("detail"));
                break;
            case "pic": {
                Future<?> old = miniCapMap.get(session);
                old.cancel(true);
                Thread.sleep(3000);
                miniCapMap.remove(session);
                MiniCapTool miniCapTool = new MiniCapTool();
                AtomicReference<String[]> banner = new AtomicReference<>(new String[24]);
                Future<?> miniCapThread = miniCapTool.start(
                        udIdMap.get(session).getSerialNumber(), banner, null, msg.getString("detail"), session);
                miniCapMap.put(session, miniCapThread);
                JSONObject picFinish = new JSONObject();
                picFinish.put("msg", "picFinish");
                sendText(session, picFinish.toJSONString());
                break;
            }
            case "screen": {
                AndroidDeviceBridgeTool.screen(udIdMap.get(session), msg.getString("s"));
                if (!msg.getString("s").equals("abort")) {
                    Future<?> old = miniCapMap.get(session);
                    old.cancel(true);
                    Thread.sleep(3000);
                    miniCapMap.remove(session);
                    MiniCapTool miniCapTool = new MiniCapTool();
                    AtomicReference<String[]> banner = new AtomicReference<>(new String[24]);
                    Future<?> miniCapThread = miniCapTool.start(
                            udIdMap.get(session).getSerialNumber(), banner, null, msg.getString("detail"), session);
                    miniCapMap.put(session, miniCapThread);
                    JSONObject picFinish = new JSONObject();
                    picFinish.put("msg", "picFinish");
                    sendText(session, picFinish.toJSONString());
                }
                break;
            }
            case "touch":
                OutputStream outputStream = outputMap.get(session);
                try {
                    outputStream.write(msg.getString("detail").getBytes());
                    outputStream.flush();
                    outputStream.write("c\n".getBytes());
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case "keyEvent":
                AndroidDeviceBridgeTool.pressKey(udIdMap.get(session), msg.getInteger("detail"));
                break;
            case "debug":
                AndroidStepHandler androidStepHandler;
                try {
                    if (msg.getString("detail").equals("tap")) {
                        String xy = msg.getString("point");
                        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(udIdMap.get(session), "input tap " + x + " " + y);
                    }
                    if (msg.getString("detail").equals("longPress")) {
                        String xy = msg.getString("point");
                        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
                        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(udIdMap.get(session), "input swipe " + x + " " + y + " " + x + " " + y + " 1500");
                    }
                    if (msg.getString("detail").equals("swipe")) {
                        String xy1 = msg.getString("pointA");
                        String xy2 = msg.getString("pointB");
                        int x1 = Integer.parseInt(xy1.substring(0, xy1.indexOf(",")));
                        int y1 = Integer.parseInt(xy1.substring(xy1.indexOf(",") + 1));
                        int x2 = Integer.parseInt(xy2.substring(0, xy2.indexOf(",")));
                        int y2 = Integer.parseInt(xy2.substring(xy2.indexOf(",") + 1));
                        AndroidDeviceBridgeTool.executeCommand(udIdMap.get(session), "input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " 300");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (msg.getString("detail").equals("install")) {
                    androidStepHandler = HandlerMap.getAndroidMap().get(session.getId());
                    AndroidStepHandler finalAndroidStepHandler = androidStepHandler;
                    AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                        JSONObject result = new JSONObject();
                        result.put("msg", "installFinish");
                        HandleDes handleDes = new HandleDes();
                        finalAndroidStepHandler.install(handleDes, msg.getString("apk"), msg.getString("pkg"));
                        if (handleDes.getE() == null) {
                            result.put("status", "success");
                        } else {
                            result.put("status", "fail");
                        }
                        sendText(session, result.toJSONString());
                    });
                }
                if (msg.getString("detail").equals("tree")) {
                    androidStepHandler = HandlerMap.getAndroidMap().get(session.getId());
                    AndroidStepHandler finalAndroidStepHandler = androidStepHandler;
                    AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                        try {
                            JSONObject result = new JSONObject();
                            result.put("msg", "tree");
                            result.put("detail", finalAndroidStepHandler.getResource());
                            HandleDes handleDes = new HandleDes();
                            result.put("img", finalAndroidStepHandler.stepScreen(handleDes));
                            if (handleDes.getE() != null) {
                                logger.error(handleDes.getE().getMessage());
                                JSONObject resultFail = new JSONObject();
                                resultFail.put("msg", "treeFail");
                                sendText(session, resultFail.toJSONString());
                            } else {
                                result.put("webView", finalAndroidStepHandler.getWebView());
                                result.put("activity", finalAndroidStepHandler.getCurrentActivity());
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
                    androidStepHandler = HandlerMap.getAndroidMap().get(session.getId());
                    AndroidStepHandler finalAndroidStepHandler = androidStepHandler;
                    AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
                        JSONObject result = new JSONObject();
                        result.put("msg", "eleScreen");
                        try {
                            result.put("img", UploadTools.upload(finalAndroidStepHandler.findEle("xpath", msg.getString("xpath")).getScreenshotAs(OutputType.FILE), "keepFiles"));
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
                    jsonDebug.put("udId", udIdMap.get(session).getSerialNumber());
                    jsonDebug.put("pwd", msg.getString("pwd"));
                    jsonDebug.put("sessionId", session.getId());
                    jsonDebug.put("caseId", msg.getInteger("caseId"));
                    RabbitMQThread.send(jsonDebug);
                }
                break;
        }
    }

    private void sendText(Session session, String message) {
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IllegalStateException | IOException e) {
                logger.error("socket发送失败!连接已关闭！");
            }
        }
    }

    private void exit(Session session) {
        try {
            HandlerMap.getAndroidMap().get(session.getId()).closeAndroidDriver();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            AndroidDeviceLocalStatus.finish(udIdMap.get(session).getSerialNumber());
            HandlerMap.getAndroidMap().remove(session.getId());
        }
        List<JSONObject> has = webViewForwardMap.get(udIdMap.get(session));
        if (has != null && has.size() > 0) {
            for (JSONObject j : has) {
                AndroidDeviceBridgeTool.removeForward(udIdMap.get(session), j.getInteger("port"), j.getString("name"));
            }
        }
        webViewForwardMap.remove(udIdMap.get(session));
        outputMap.remove(session);
        udIdMap.remove(session);
        miniCapMap.get(session).cancel(true);
        miniCapMap.remove(session);
        WebSocketSessionMap.getMap().remove(session.getId());
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info(session.getId() + "退出");
    }
}