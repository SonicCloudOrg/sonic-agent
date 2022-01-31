package org.cloud.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.*;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.cloud.sonic.agent.maps.AndroidAPKMap;
import org.cloud.sonic.agent.tools.AgentTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/30 23:35
 */
@Component
@ServerEndpoint(value = "/websockets/terminal/{key}/{udId}", configurator = MyEndpointConfigure.class)
public class TerminalWSServer {
    private final Logger logger = LoggerFactory.getLogger(TerminalWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, IDevice> udIdMap = new ConcurrentHashMap<>();
    private Map<Session, Future<?>> terminalMap = new ConcurrentHashMap<>();
    private Map<Session, Future<?>> appListMap = new ConcurrentHashMap<>();
    private Map<Session, Future<?>> logcatMap = new ConcurrentHashMap<>();
    private Map<Session, Future<?>> audioMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey, @PathParam("udId") String udId) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key))) {
            logger.info("拦截访问！");
            return;
        }
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        udIdMap.put(session, iDevice);
        String username = iDevice.getProperty("ro.product.device");
        Future<?> terminal = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            logger.info(udId + "开启terminal");
            JSONObject ter = new JSONObject();
            ter.put("msg", "terminal");
            ter.put("user", username);
            sendText(session, ter.toJSONString());
        });
        Future<?> logcat = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            logger.info(udId + "开启logcat");
            JSONObject ter = new JSONObject();
            ter.put("msg", "logcat");
            sendText(session, ter.toJSONString());
        });
        terminalMap.put(session, terminal);
        logcatMap.put(session, logcat);
        int wait = 0;
        boolean isInstall = true;
        while (AndroidAPKMap.getMap().get(udId) == null || (!AndroidAPKMap.getMap().get(udId))) {
            Thread.sleep(500);
            wait++;
            if (wait >= 40) {
                isInstall = false;
                break;
            }
        }
        if (isInstall) {
            getAppList(iDevice, session);
            getAudio(iDevice, session);
        } else {
            logger.info("等待安装超时！");
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        JSONObject msg = JSON.parseObject(message);
        logger.info(session.getId() + " 发送 " + msg);
        switch (msg.getString("type")) {
            case "appList":
                getAppList(udIdMap.get(session), session);
                break;
            case "stopCmd":
                Future<?> ter = terminalMap.get(session);
                if (!ter.isDone() || !ter.isCancelled()) {
                    try {
                        ter.cancel(true);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
                break;
            case "command":
                if (msg.getString("detail").contains("reboot")
                        || msg.getString("detail").contains("rm")
                        || msg.getString("detail").contains("su ")) {
                    JSONObject done = new JSONObject();
                    done.put("msg", "terDone");
                    sendText(session, done.toJSONString());
                    return;
                }
                ter = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    try {
                        udIdMap.get(session).executeShellCommand(msg.getString("detail"), new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                JSONObject resp = new JSONObject();
                                resp.put("msg", "terResp");
                                resp.put("detail", res);
                                sendText(session, resp.toJSONString());
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }, 0, TimeUnit.MILLISECONDS);
                    } catch (Throwable e) {
                        return;
                    }
                    JSONObject done = new JSONObject();
                    done.put("msg", "terDone");
                    sendText(session, done.toJSONString());
                });
                terminalMap.put(session, ter);
                break;
            case "stopLogcat": {
                Future<?> logcat = logcatMap.get(session);
                if (!logcat.isDone() || !logcat.isCancelled()) {
                    try {
                        logcat.cancel(true);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
                break;
            }
            case "logcat": {
                Future<?> logcat = logcatMap.get(session);
                if (!logcat.isDone() || !logcat.isCancelled()) {
                    try {
                        logcat.cancel(true);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
                logcat = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    try {
                        udIdMap.get(session).executeShellCommand("logcat *:"
                                + msg.getString("level") +
                                (msg.getString("filter").length() > 0 ?
                                        " | grep " + msg.getString("filter") : ""), new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                JSONObject resp = new JSONObject();
                                resp.put("msg", "logcatResp");
                                resp.put("detail", res);
                                sendText(session, resp.toJSONString());
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }, 0, TimeUnit.MILLISECONDS);
                    } catch (Throwable e) {
                        return;
                    }
                });
                logcatMap.put(session, logcat);
                break;
            }
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

    private void exit(Session session) {
        Future<?> cmd = terminalMap.get(session);
        if (!cmd.isDone() || !cmd.isCancelled()) {
            try {
                cmd.cancel(true);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        terminalMap.remove(session);
        Future<?> audioPro = audioMap.get(session);
        if (audioPro != null && (!audioPro.isDone() || !audioPro.isCancelled())) {
            try {
                audioPro.cancel(true);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        audioMap.remove(session);
        Future<?> logcat = logcatMap.get(session);
        if (!logcat.isDone() || !logcat.isCancelled()) {
            try {
                logcat.cancel(true);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        logcatMap.remove(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info(session.getId() + "退出");
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

    public void getAudio(IDevice iDevice, Session session) {
        Future<?> audioPro = audioMap.get(session);
        if (audioPro != null && (!audioPro.isDone() || !audioPro.isCancelled())) {
            try {
                audioPro.cancel(true);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        audioPro = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            AndroidDeviceBridgeTool.executeCommand(iDevice, "appops set org.cloud.sonic.android PROJECT_MEDIA allow");
            AndroidDeviceBridgeTool.executeCommand(iDevice, "am start -n org.cloud.sonic.android/.AudioActivity");
            AndroidDeviceBridgeTool.pressKey(iDevice, 4);
            int appListPort = PortTool.getPort();
            try {
                AndroidDeviceBridgeTool.forward(iDevice, appListPort, "sonicaudioservice");
                Socket audioSocket = null;
                InputStream inputStream = null;
                try {
                    audioSocket = new Socket("localhost", appListPort);
                    inputStream = audioSocket.getInputStream();
                    int len = 1024;
                    while (audioSocket.isConnected() && !Thread.interrupted()) {
                        byte[] buffer = new byte[len];
                        int realLen;
                        realLen = inputStream.read(buffer);
                        if (buffer.length != realLen && realLen >= 0) {
                            buffer = AgentTool.subByteArray(buffer, 0, realLen);
                        }
                        if (realLen >= 0) {
//                            System.out.println(buffer);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (audioSocket != null && audioSocket.isConnected()) {
                        try {
                            audioSocket.close();
                            logger.info("audio socket已关闭");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                            logger.info("audio output流已关闭");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                logger.info("{} 设备远程音频服务启动异常！"
                        , iDevice.getSerialNumber());
                logger.error(e.getMessage());
            }
            AndroidDeviceBridgeTool.removeForward(iDevice, appListPort, "sonicaudioservice");
        });
        audioMap.put(session, audioPro);
    }

    public void getAppList(IDevice iDevice, Session session) {
        Future<?> app = appListMap.get(session);
        if (app != null && (!app.isDone() || !app.isCancelled())) {
            try {
                app.cancel(true);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        app = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            AndroidDeviceBridgeTool.executeCommand(iDevice, "am start -n org.cloud.sonic.android/.AppListActivity");
            AndroidDeviceBridgeTool.pressKey(iDevice, 4);
            int appListPort = PortTool.getPort();
            try {
                AndroidDeviceBridgeTool.forward(iDevice, appListPort, "sonicapplistservice");
                Socket appListSocket = null;
                InputStream inputStream = null;
                try {
                    appListSocket = new Socket("localhost", appListPort);
                    inputStream = appListSocket.getInputStream();
                    int len = 1024;
                    String total = "";
                    while (appListSocket.isConnected()) {
                        byte[] buffer = new byte[len];
                        int realLen;
                        realLen = inputStream.read(buffer);
                        if (buffer.length != realLen && realLen >= 0) {
                            buffer = AgentTool.subByteArray(buffer, 0, realLen);
                        }
                        if (realLen >= 0) {
                            String chunk = new String(buffer);
                            total += chunk;
                            if (chunk.contains("}")) {
                                JSONObject appListDetail = new JSONObject();
                                appListDetail.put("msg", "appListDetail");
                                appListDetail.put("detail", JSON.parseObject(total));
                                AgentTool.sendText(session, appListDetail.toJSONString());
                                total = "";
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (appListSocket != null && appListSocket.isConnected()) {
                        try {
                            appListSocket.close();
                            logger.info("appList socket已关闭");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                            logger.info("appList output流已关闭");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                logger.info("{} 设备App列表监听服务启动异常！"
                        , iDevice.getSerialNumber());
                logger.error(e.getMessage());
            }
            AndroidDeviceBridgeTool.removeForward(iDevice, appListPort, "sonicapplistservice");
        });
        appListMap.put(session, app);
    }
}
