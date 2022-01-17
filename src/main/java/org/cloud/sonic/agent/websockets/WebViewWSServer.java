package org.cloud.sonic.agent.websockets;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/25 23:03
 */
@Component
@ServerEndpoint(value = "/websockets/webView/{key}/{port}/{id}", configurator = MyEndpointConfigure.class)
public class WebViewWSServer {
    private final Logger logger = LoggerFactory.getLogger(WebViewWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, WebSocketClient> sessionWebSocketClientMap = new HashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey, @PathParam("port") int port, @PathParam("id") String id) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key))) {
            logger.info("拦截访问！");
            return;
        }
        URI uri = new URI("ws://localhost:" + port + "/devtools/page/" + id);
        WebSocketClient webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                logger.info("连接成功!");
            }

            @Override
            public void onMessage(String s) {
                sendText(session, s);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                logger.info("连接断开!");
            }

            @Override
            public void onError(Exception e) {

            }
        };
        webSocketClient.connect();
        sessionWebSocketClientMap.put(session, webSocketClient);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws InterruptedException {
        if (sessionWebSocketClientMap.get(session) != null) {
            try {
                sessionWebSocketClientMap.get(session).send(message);
            } catch (Exception e) {

            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessionWebSocketClientMap.get(session).close();
        sessionWebSocketClientMap.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.error(error.getMessage());
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
}
