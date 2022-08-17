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
package org.cloud.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.common.maps.AndroidAPKMap;
import org.cloud.sonic.agent.tools.BytesTool;
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
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/30 23:35
 */
@Component
@Slf4j
@ServerEndpoint(value = "/websockets/android/terminal/{key}/{udId}/{token}", configurator = WsEndpointConfigure.class)
public class AndroidTerminalWSServer {

    private final Logger logger = LoggerFactory.getLogger(AndroidTerminalWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, IDevice> udIdMap = new ConcurrentHashMap<>();
    private Map<Session, Future<?>> terminalMap = new ConcurrentHashMap<>();
    private Map<Session, Thread> socketMap = new ConcurrentHashMap<>();
    private Map<Session, OutputStream> outputStreamMap = new ConcurrentHashMap<>();
    private Map<Session, Future<?>> logcatMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key)) || token.length() == 0) {
            logger.info("拦截访问！");
            return;
        }
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        udIdMap.put(session, iDevice);
        String username = iDevice.getProperty("ro.product.device");
        Future<?> terminal = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            logger.info(udId + "开启terminal");
            JSONObject ter = new JSONObject();
            ter.put("msg", "terminal");
            ter.put("user", username);
            BytesTool.sendText(session, ter.toJSONString());
        });
        Future<?> logcat = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            logger.info(udId + "开启logcat");
            JSONObject ter = new JSONObject();
            ter.put("msg", "logcat");
            BytesTool.sendText(session, ter.toJSONString());
        });
        terminalMap.put(session, terminal);
        logcatMap.put(session, logcat);
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
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        JSONObject msg = JSON.parseObject(message);
        logger.info("{} send: {}", session.getId(), msg);
        switch (msg.getString("type")) {
            case "appList": {
                startService(udIdMap.get(session), session);
                if (outputStreamMap.get(session) != null) {
                    try {
                        outputStreamMap.get(session).write("action_get_all_app_info".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case "wifiList": {
                startService(udIdMap.get(session), session);
                if (outputStreamMap.get(session) != null) {
                    try {
                        outputStreamMap.get(session).write("action_get_all_wifi_info".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case "stopCmd":
                Future<?> ter = terminalMap.get(session);
                if (!ter.isDone() || !ter.isCancelled()) {
                    try {
                        ter.cancel(true);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
                break;
            case "command":
                if (msg.getString("detail").contains("reboot")
                        || msg.getString("detail").contains("rm")
                        || msg.getString("detail").contains("su ")) {
                    JSONObject done = new JSONObject();
                    done.put("msg", "terDone");
                    BytesTool.sendText(session, done.toJSONString());
                    return;
                }
                ter = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    try {
                        udIdMap.get(session).executeShellCommand(msg.getString("detail"), new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                JSONObject resp = new JSONObject();
                                resp.put("msg", "terResp");
                                resp.put("detail", res);
                                BytesTool.sendText(session, resp.toJSONString());
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }, 0, TimeUnit.MILLISECONDS);
                    } catch (Throwable e) {
                        return;
                    }
                    JSONObject done = new JSONObject();
                    done.put("msg", "terDone");
                    BytesTool.sendText(session, done.toJSONString());
                });
                terminalMap.put(session, ter);
                break;
            case "stopLogcat": {
                Future<?> logcat = logcatMap.get(session);
                if (!logcat.isDone() || !logcat.isCancelled()) {
                    try {
                        logcat.cancel(true);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
                break;
            }
            case "logcat": {
                Future<?> logcat = logcatMap.get(session);
                if (!logcat.isDone() || !logcat.isCancelled()) {
                    try {
                        logcat.cancel(true);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
                logcat = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    try {
                        udIdMap.get(session).executeShellCommand("logcat *:"
                                + msg.getString("level") +
                                (msg.getString("filter").length() > 0 ?
                                        " | grep " + msg.getString("filter") : ""), new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                JSONObject resp = new JSONObject();
                                resp.put("msg", "logcatResp");
                                resp.put("detail", res);
                                BytesTool.sendText(session, resp.toJSONString());
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }, 0, TimeUnit.MILLISECONDS);
                    } catch (Throwable e) {
                        return;
                    }
                });
                logcatMap.put(session, logcat);
                break;
            }
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
        BytesTool.sendText(session, errMsg.toJSONString());
    }

    private void exit(Session session) {
        Future<?> cmd = terminalMap.get(session);
        if (!cmd.isDone() || !cmd.isCancelled()) {
            try {
                cmd.cancel(true);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        Thread s = socketMap.get(session);
        if (s != null) {
            s.interrupt();
        }
        terminalMap.remove(session);
        Future<?> logcat = logcatMap.get(session);
        if (!logcat.isDone() || !logcat.isCancelled()) {
            try {
                logcat.cancel(true);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        logcatMap.remove(session);
        udIdMap.remove(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("{} : quit.", session.getId());
    }

    public void startService(IDevice iDevice, Session session) {
        if (socketMap.get(session) != null && socketMap.get(session).isAlive()) {
            return;
        }
        AndroidDeviceBridgeTool.executeCommand(iDevice, "am start -n org.cloud.sonic.android/.SonicServiceActivity");
        int wait = 0;
        String has = AndroidDeviceBridgeTool.executeCommand(iDevice, "cat /proc/net/unix | grep sonicmanagersocket");
        while (!has.contains("sonicmanagersocket")) {
            wait++;
            if (wait > 8) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            has = AndroidDeviceBridgeTool.executeCommand(iDevice, "cat /proc/net/unix | grep sonicmanagersocket");
        }
        Thread manager = new ManagerThread(iDevice, session, outputStreamMap);
        manager.start();
        int w = 0;
        while (outputStreamMap.get(session) == null) {
            if (w > 10) {
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            w++;
        }
        socketMap.put(session, manager);
    }

    static class ManagerThread extends Thread {

        private Socket managerSocket = null;
        private InputStream inputStream = null;
        private OutputStream outputStream = null;

        private IDevice iDevice;
        private Session session;
        private Map<Session, OutputStream> outputStreamMap;

        public ManagerThread(IDevice iDevice, Session session, Map<Session, OutputStream> outputStreamMap) {
            this.iDevice = iDevice;
            this.session = session;
            this.outputStreamMap = outputStreamMap;
        }

        @Override
        public void run() {
            int managerPort = PortTool.getPort();
            AndroidDeviceBridgeTool.forward(iDevice, managerPort, "sonicmanagersocket");
            try {
                managerSocket = new Socket("localhost", managerPort);
                inputStream = managerSocket.getInputStream();
                outputStreamMap.put(session, managerSocket.getOutputStream());
                while (managerSocket.isConnected() && !Thread.interrupted()) {
                    byte[] lengthBytes = inputStream.readNBytes(32);
                    if (lengthBytes.length == 0) {
                        break;
                    }
                    StringBuffer binStr = new StringBuffer();
                    for (byte lengthByte : lengthBytes) {
                        binStr.append(lengthByte);
                    }
                    Integer readLen = Integer.valueOf(binStr.toString(), 2);

                    // 根据长度读取数据体
                    byte[] dataBytes = inputStream.readNBytes(readLen);
                    String dataJson = new String(dataBytes);
                    JSONObject managerDetail = new JSONObject();
                    JSONObject data = JSON.parseObject(dataJson);
                    if (data.getString("appName") != null) {
                        managerDetail.put("msg", "appListDetail");
                    } else {
                        managerDetail.put("msg", "wifiListDetail");
                    }
                    managerDetail.put("detail", JSON.parseObject(dataJson));
                    BytesTool.sendText(session, managerDetail.toJSONString());
                }
            } catch (IOException e) {
                log.info("error: {}", e.getMessage());
            }
            AndroidDeviceBridgeTool.removeForward(iDevice, managerPort, "sonicmanagersocket");
            outputStreamMap.remove(session);
        }

        @Override
        public void interrupt() {
            super.interrupt();
            stopManager();
        }

        public void stopManager() {
            if (outputStream != null) {
                try {
                    outputStream.write("org.cloud.sonic.android.STOP".getBytes(StandardCharsets.UTF_8));
                    outputStream.close();
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
            if (managerSocket != null && managerSocket.isConnected()) {
                try {
                    managerSocket.close();
                    log.info("manager socket closed.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
