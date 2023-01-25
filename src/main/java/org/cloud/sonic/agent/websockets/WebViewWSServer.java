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

import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.tools.BytesTool;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/25 23:03
 */
@Component
@ServerEndpoint(value = "/websockets/webView/{key}/{port}/{id}", configurator = WsEndpointConfigure.class)
public class WebViewWSServer {
    private final Logger logger = LoggerFactory.getLogger(WebViewWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, WebSocketClient> sessionWebSocketClientMap = new HashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey, @PathParam("port") int port, @PathParam("id") String id) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key))) {
            logger.info("Auth Failed!");
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
                BytesTool.sendText(session, s);
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
}
