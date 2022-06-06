package org.cloud.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

import static org.cloud.sonic.agent.tools.BytesTool.sendText;

@Component
@ServerEndpoint(value = "/websockets/ios/terminal/{key}/{udId}/{token}", configurator = MyEndpointConfigure.class)
public class IOSTerminalWSServer implements IIOSWSServer {
    private final Logger logger = LoggerFactory.getLogger(AndroidTerminalWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key)) || token.length() == 0) {
            logger.info("拦截访问！");
            return;
        }
        WebSocketSessionMap.addSession(session);
        if (!SibTool.getDeviceList().contains(udId)) {
            logger.info("设备未连接，请检查！");
            return;
        }
        saveUdIdMapAndSet(session, udId);
        JSONObject ter = new JSONObject();
        ter.put("msg", "terminal");
        sendText(session, ter.toJSONString());
    }

    @OnMessage
    public void onMessage(String message, Session session) throws InterruptedException {
        JSONObject msg = JSON.parseObject(message);
        logger.info(session.getId() + " 发送 " + msg);
        String udId = udIdMap.get(session);
        switch (msg.getString("type")) {
            case "appList":
                SibTool.getAppList(udId, session);
                break;
            case "syslog":
                SibTool.getSysLog(udId, msg.getString("filter"), session);
                break;
            case "stopSyslog":
                SibTool.stopSysLog(udId);
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
