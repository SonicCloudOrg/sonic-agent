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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.ScheduleTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;

import static org.cloud.sonic.agent.tools.BytesTool.sendText;

@Component
@Slf4j
@ServerEndpoint(value = "/websockets/ios/terminal/{key}/{udId}/{token}", configurator = WsEndpointConfigure.class)
public class IOSTerminalWSServer implements IIOSWSServer {
    @Value("${sonic.agent.key}")
    private String key;

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key)) || token.length() == 0) {
            log.info("Auth Failed!");
            return;
        }

        if (!SibTool.getDeviceList().contains(udId)) {
            log.info("Target device is not connecting, please check the connection.");
            return;
        }

        session.getUserProperties().put("udId", udId);
        session.getUserProperties().put("id", String.format("%s-%s", this.getClass().getSimpleName(), udId));
        WebSocketSessionMap.addSession(session);
        saveUdIdMapAndSet(session, udId);

        JSONObject ter = new JSONObject();
        ter.put("msg", "terminal");
        sendText(session, ter.toJSONString());

        ScheduleTool.schedule(() -> {
            log.info("time up!");
            if (session.isOpen()) {
                JSONObject errMsg = new JSONObject();
                errMsg.put("msg", "error");
                BytesTool.sendText(session, errMsg.toJSONString());
                exit(session);
            }
        }, BytesTool.remoteTimeout);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws InterruptedException {
        JSONObject msg = JSON.parseObject(message);
        log.info("{} send: {}", session.getUserProperties().get("id").toString(), msg);
        String udId = udIdMap.get(session);
        switch (msg.getString("type")) {
            case "processList" -> SibTool.getProcessList(udId, session);
            case "appList" -> SibTool.getAppList(udId, session);
            case "syslog" -> SibTool.getSysLog(udId, msg.getString("filter"), session);
            case "stopSyslog" -> SibTool.stopSysLog(udId);
        }
    }

    @OnClose
    public void onClose(Session session) {
        exit(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error(error.getMessage());
        JSONObject errMsg = new JSONObject();
        errMsg.put("msg", "error");
        sendText(session, errMsg.toJSONString());
    }

    private void exit(Session session) {
        synchronized (session) {
            if (udIdMap.get(session) != null) {
                SibTool.stopSysLog(udIdMap.get(session));
            }
            WebSocketSessionMap.removeSession(session);
            removeUdIdMapAndSet(session);
            try {
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info("{} : quit.", session.getUserProperties().get("id").toString());
        }
    }
}
