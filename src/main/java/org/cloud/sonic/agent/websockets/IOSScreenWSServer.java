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

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.tests.ios.mjpeg.MjpegInputStream;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.ScheduleTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import static org.cloud.sonic.agent.tools.BytesTool.sendByte;

@Component
@Slf4j
@ServerEndpoint(value = "/websockets/ios/screen/{key}/{udId}/{token}", configurator = WsEndpointConfigure.class)
public class IOSScreenWSServer implements IIOSWSServer {
    @Value("${sonic.agent.key}")
    private String key;
    @Value("${sonic.agent.port}")
    private int port;
    private Timer timer = new Timer();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws InterruptedException {
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

        int screenPort = 0;
        int wait = 0;
        while (wait < 120) {
            Integer p = IOSWSServer.screenMap.get(udId);
            if (p != null) {
                screenPort = p;
                break;
            }
            Thread.sleep(500);
            wait++;
        }
        if (screenPort == 0) {
            return;
        }
        int finalScreenPort = screenPort;
        new Thread(() -> {
            URL url;
            try {
                url = new URL("http://localhost:" + finalScreenPort);
            } catch (MalformedURLException e) {
                return;
            }
            MjpegInputStream mjpegInputStream = null;
            int waitMjpeg = 0;
            while (mjpegInputStream == null) {
                try {
                    mjpegInputStream = new MjpegInputStream(url.openStream());
                } catch (IOException e) {
                    log.info(e.getMessage());
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.info(e.getMessage());
                    return;
                }
                waitMjpeg++;
                if (waitMjpeg >= 20) {
                    log.info("mjpeg server connect fail");
                    return;
                }
            }
            ByteBuffer bufferedImage;
            int i = 0;
            while (true) {
                try {
                    if ((bufferedImage = mjpegInputStream.readFrameForByteBuffer()) == null) break;
                } catch (IOException e) {
                    log.info(e.getMessage());
                    break;
                }
                i++;
                if (i % 3 != 0) {
                    sendByte(session, bufferedImage);
                } else {
                    i = 0;
                }
            }
            try {
                mjpegInputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.info("screen done.");
        }).start();

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

    @OnClose
    public void onClose(Session session) {
        exit(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error(error.getMessage());
    }

    private void exit(Session session) {
        synchronized (session) {
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
