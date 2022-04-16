/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.tools.poco;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.cloud.sonic.agent.tools.BytesTool.subByteArray;

public class PocoTool {
    private static final Logger logger = LoggerFactory.getLogger(PocoTool.class);

    public static JSONObject getSocketResult(String udId, int platform, String type) {
        int port = PortTool.getPort();
        int target = 0;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("jsonrpc", "2.0");
        jsonObject.put("params", Arrays.asList(true));
        jsonObject.put("id", UUID.randomUUID().toString());
        jsonObject.put("method", "Dump");
        switch (type) {
            case "Unity3d":
            case "UE4":
                target = 5001;
                break;
            case "Cocos2dx-js":
            case "cocos-creator":
                jsonObject.put("method", "dump");
            case "Egret":
                target = 5003;
                break;
            case "Cocos2dx-lua":
                target = 15004;
                break;
            case "Cocos2dx-c++":
                target = 18888;
                break;
        }
        if (platform == PlatformType.ANDROID) {
            AndroidDeviceBridgeTool.forward(AndroidDeviceBridgeTool.getIDeviceByUdId(udId), port, target);
        }
        AtomicReference<String> result = new AtomicReference<>();
        if (target == 5003) {
            try {
                URI ws = new URI("ws://localhost:" + port);
                WebSocketClient webSocketClient = new WebSocketClient(ws) {
                    @Override
                    public void onOpen(ServerHandshake serverHandshake) {
                        logger.info("poco ws connected.");
                    }

                    @Override
                    public void onMessage(String s) {
                        result.set(s);
                    }

                    @Override
                    public void onClose(int i, String s, boolean b) {
                        logger.info("poco ws close.");
                    }

                    @Override
                    public void onError(Exception e) {

                    }
                };
                webSocketClient.connect();
                int waitConnect = 0;
                while (!webSocketClient.isOpen()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    waitConnect++;
                    if (waitConnect >= 20) {
                        break;
                    }
                }
                if (webSocketClient.isOpen()) {
                    webSocketClient.send(jsonObject.toString());
                    int wait = 0;
                    while (result.get() == null) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        wait++;
                        if (wait >= 20) {
                            break;
                        }
                    }
                    webSocketClient.close();
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        } else {
            Thread pocoThread = new Thread(() -> {
                Socket poco = null;
                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    poco = new Socket("localhost", port);
                    inputStream = poco.getInputStream();
                    outputStream = poco.getOutputStream();
                    int len = jsonObject.toJSONString().length();
                    ByteBuffer header = ByteBuffer.allocate(4);
                    header.put(BytesTool.intToByteArray(len), 0, 4);
                    header.flip();
                    ByteBuffer body = ByteBuffer.allocate(len);
                    body.put(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8), 0, len);
                    body.flip();
                    ByteBuffer total = ByteBuffer.allocate(len + 4);
                    total.put(header.array());
                    total.put(body.array());
                    total.flip();
                    outputStream.write(total.array());
                    byte[] head = new byte[4];
                    inputStream.read(head);
                    int headLen = BytesTool.toInt(head);
                    String s = "";
                    while (poco.isConnected() && !Thread.interrupted()) {
                        byte[] buffer = new byte[1024];
                        int realLen;
                        realLen = inputStream.read(buffer);
                        if (buffer.length != realLen && realLen >= 0) {
                            buffer = subByteArray(buffer, 0, realLen);
                        }
                        if (realLen >= 0) {
                            s += new String(buffer);
                            if (s.getBytes(StandardCharsets.UTF_8).length == headLen) {
                                result.set(s);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (poco != null && poco.isConnected()) {
                        try {
                            poco.close();
                            logger.info("poco socket closed.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                            logger.info("poco input stream closed.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                            logger.info("poco output stream closed.");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            pocoThread.start();
            int wait = 0;
            while (pocoThread.isAlive()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                wait++;
                if (wait >= 20) {
                    pocoThread.interrupt();
                }
            }
        }
        if (platform == PlatformType.ANDROID) {
            AndroidDeviceBridgeTool.removeForward(AndroidDeviceBridgeTool.getIDeviceByUdId(udId), port, target);
        }
        if (result.get() != null) {
            try {
                return JSON.parseObject(result.get()).getJSONObject("result");
            } catch (Exception e) {
                return null;
            }
        } else {
            return null;
        }
    }
}
