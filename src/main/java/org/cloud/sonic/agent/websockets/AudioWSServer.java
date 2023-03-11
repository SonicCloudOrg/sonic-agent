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

import com.android.ddmlib.IDevice;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.common.maps.AndroidAPKMap;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@ServerEndpoint(value = "/websockets/audio/{key}/{udId}", configurator = WsEndpointConfigure.class)
public class AudioWSServer implements IAndroidWSServer {
    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, Thread> audioMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey, @PathParam("udId") String udId) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key))) {
            log.info("Auth Failed!");
            return;
        }
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);

        session.getUserProperties().put("udId", udId);
        session.getUserProperties().put("id", String.format("%s-%s", this.getClass().getSimpleName(), udId));
        WebSocketSessionMap.addSession(session);
        saveUdIdMapAndSet(session, iDevice);

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
        if (!isInstall) {
            log.info("Waiting for apk install timeout!");
        } else {
            startAudio(session);
        }
    }

    private void startAudio(Session session) {
        stopAudio(session);
        IDevice iDevice = udIdMap.get(session);
        AndroidDeviceBridgeTool.executeCommand(iDevice, "appops set org.cloud.sonic.android PROJECT_MEDIA allow");
        AndroidDeviceBridgeTool.executeCommand(iDevice, "appops set org.cloud.sonic.android RECORD_AUDIO allow");
        AndroidDeviceBridgeTool.executeCommand(iDevice, "am start -n org.cloud.sonic.android/.plugin.audioPlugin.AudioActivity");
        int wait = 0;
        String has = AndroidDeviceBridgeTool.executeCommand(iDevice, "cat /proc/net/unix");
        while (!has.contains("sonicaudioservice")) {
            wait++;
            if (wait > 8) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            has = AndroidDeviceBridgeTool.executeCommand(iDevice, "cat /proc/net/unix");
        }
        int appAudioPort = PortTool.getPort();
        Thread audio = new Thread(() -> {
            try {
                AndroidDeviceBridgeTool.forward(iDevice, appAudioPort, "sonicaudioservice");
                Socket audioSocket = null;
                InputStream inputStream = null;
                try {
                    audioSocket = new Socket("localhost", appAudioPort);
                    inputStream = audioSocket.getInputStream();
                    while (audioSocket.isConnected() && !Thread.interrupted()) {
                        byte[] lengthBytes = inputStream.readNBytes(32);
                        if (Thread.interrupted() || lengthBytes.length == 0) {
                            break;
                        }
                        StringBuilder binStr = new StringBuilder();
                        for (byte lengthByte : lengthBytes) {
                            binStr.append(lengthByte);
                        }
                        Integer readLen = Integer.valueOf(binStr.toString(), 2);
                        byte[] dataBytes = inputStream.readNBytes(readLen);
                        ByteBuffer byteBuffer = ByteBuffer.allocate(dataBytes.length);
                        byteBuffer.put(dataBytes);
                        byteBuffer.flip();
                        BytesTool.sendByte(session, byteBuffer);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (audioSocket != null && audioSocket.isConnected()) {
                        try {
                            audioSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            AndroidDeviceBridgeTool.removeForward(iDevice, appAudioPort, "sonicaudioservice");
        });
        audio.start();
        audioMap.put(session, audio);
    }

    private void stopAudio(Session session) {
        if (audioMap.get(session) != null) {
            audioMap.get(session).interrupt();
            int wait = 0;
            while (!audioMap.get(session).isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                wait++;
                if (wait >= 3) {
                    break;
                }
            }
        }
        audioMap.remove(session);
    }

//    @OnMessage
//    public void onMessage(String message, Session session) {
//        JSONObject msg = JSON.parseObject(message);
//        log.info("{} send: {}",session.getUserProperties().get("id").toString(), msg);
//        switch (msg.getString("type")) {
//            case "start":
//                startAudio(session);
//                break;
//            case "stop":
//                stopAudio(session);
//                break;
//        }
//    }

    @OnClose
    public void onClose(Session session) {
        exit(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.info("Audio socket error, cause: {}, ignore...", error.getMessage());
    }

    private void exit(Session session) {
        stopAudio(session);
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
