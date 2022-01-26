package org.cloud.sonic.agent.websockets;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.cloud.sonic.agent.tools.AgentTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.springframework.stereotype.Component;

import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

@Component
@ServerEndpoint(value = "/websockets/audio/{udId}", configurator = MyEndpointConfigure.class)
public class AudioWSServer {
    @OnOpen
    public void onOpen(Session session,@PathParam("udId") String udId) throws Exception {
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        AndroidDeviceBridgeTool.executeCommand(iDevice, "appops set org.cloud.sonic.android PROJECT_MEDIA allow");
        AndroidDeviceBridgeTool.executeCommand(iDevice, "am start -n org.cloud.sonic.android/.AudioActivity");
        AndroidDeviceBridgeTool.pressKey(iDevice, 4);
        int appListPort = PortTool.getPort();
        new Thread(()-> {
            try {
                AndroidDeviceBridgeTool.forward(iDevice, appListPort, "sonicaudioservice");
                Socket audioSocket = null;
                InputStream inputStream = null;
                try {
                    audioSocket = new Socket("localhost", appListPort);
                    inputStream = audioSocket.getInputStream();
                    int len = 1024;
                    while (audioSocket.isConnected()) {
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
                            sendText(session, byteBuffer);
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
        }).start();
    }

    private void sendText(Session session, ByteBuffer message) {
        synchronized (session) {
            try {
                session.getBasicRemote().sendBinary(message);
            } catch (IllegalStateException | IOException e) {
            }
        }
    }
}
