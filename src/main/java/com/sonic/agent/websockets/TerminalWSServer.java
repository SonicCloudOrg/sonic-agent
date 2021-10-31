package com.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.*;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import com.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import com.sonic.agent.maps.HandlerMap;
import com.sonic.agent.maps.WebSocketSessionMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
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

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey, @PathParam("udId") String udId) throws Exception {
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
        terminalMap.put(session, terminal);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws InterruptedException {
        JSONObject msg = JSON.parseObject(message);
        logger.info(session.getId() + " 发送 " + msg);
        switch (msg.getString("type")) {
            case "stopCmd":
                Future<?> ter = terminalMap.get(session);
                if (!ter.isDone()) {
                    ter.cancel(true);
                }
            case "command":
                ter = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    try {
                        udIdMap.get(session).executeShellCommand(msg.getString("detail"), new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                logger.info(res);
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
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                    } catch (AdbCommandRejectedException e) {
                        e.printStackTrace();
                    } catch (ShellCommandUnresponsiveException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    JSONObject done = new JSONObject();
                    done.put("msg", "terDone");
                    sendText(session, done.toJSONString());
                });
                terminalMap.put(session, ter);
                break;
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
        if (!cmd.isDone()) {
            cmd.cancel(true);
        }
        terminalMap.remove(session);
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
                logger.error("socket发送失败!连接已关闭！");
            }
        }
    }
}
