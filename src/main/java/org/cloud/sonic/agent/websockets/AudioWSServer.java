package org.cloud.sonic.agent.websockets;

import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.maps.AndroidAPKMap;
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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint(value = "/websockets/audio/{key}/{udId}", configurator = MyEndpointConfigure.class)
public class AudioWSServer {
    private final Logger logger = LoggerFactory.getLogger(AudioWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, IDevice> udIdMap = new ConcurrentHashMap<>();
    private Map<Session, Thread> audioMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey, @PathParam("udId") String udId) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key))) {
            logger.info("拦截访问！");
            return;
        }
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        udIdMap.put(session, iDevice);
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
            logger.info("等待安装超时！");
        } else {
            startAudio(session);
        }
    }

    private void startAudio(Session session) {
        stopAudio(session);
        IDevice iDevice = udIdMap.get(session);
        AndroidDeviceBridgeTool.executeCommand(iDevice, "appops set org.cloud.sonic.android PROJECT_MEDIA allow");
        AndroidDeviceBridgeTool.executeCommand(iDevice, "am start -n org.cloud.sonic.android/.AudioActivity");
        AndroidDeviceBridgeTool.pressKey(iDevice, 4);
        int appListPort = PortTool.getPort();
        Thread audio = new Thread(() -> {
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
                            ByteBuffer byteBuffer = ByteBuffer.allocate(buffer.length);
                            byteBuffer.put(buffer);
                            byteBuffer.flip();
                            //bug
                            AgentTool.sendByte(session, byteBuffer);
                        }
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
            } catch (Exception e) {
            }
            AndroidDeviceBridgeTool.removeForward(iDevice, appListPort, "sonicaudioservice");
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
//        logger.info(session.getId() + " 发送 " + msg);
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
        logger.error("音频socket发生错误，刷新瞬间可无视：", error);
    }

    private void exit(Session session) {
        stopAudio(session);
        udIdMap.remove(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info(session.getId() + "退出");
    }
}
