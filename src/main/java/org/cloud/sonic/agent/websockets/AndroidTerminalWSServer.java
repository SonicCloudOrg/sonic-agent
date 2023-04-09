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
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.common.maps.AndroidAPKMap;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ScheduleTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/30 23:35
 */
@Component
@Slf4j
@ServerEndpoint(value = "/websockets/android/terminal/{key}/{udId}/{token}", configurator = WsEndpointConfigure.class)
public class AndroidTerminalWSServer implements IAndroidWSServer {

    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, Future<?>> terminalMap = new ConcurrentHashMap<>();
    private Map<Session, Thread> socketMap = new ConcurrentHashMap<>();
    private Map<Session, OutputStream> outputStreamMap = new ConcurrentHashMap<>();
    private Map<Session, Future<?>> logcatMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key)) || token.length() == 0) {
            log.info("Auth Failed!");
            return;
        }

        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);

        session.getUserProperties().put("udId", udId);
        session.getUserProperties().put("id", String.format("%s-%s", this.getClass().getSimpleName(), udId));
        WebSocketSessionMap.addSession(session);
        saveUdIdMapAndSet(session, iDevice);

        String username = iDevice.getProperty("ro.product.device");
        Future<?> terminal = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            log.info("{} open terminal", udId);
            JSONObject ter = new JSONObject();
            ter.put("msg", "terminal");
            ter.put("user", username);
            BytesTool.sendText(session, ter.toJSONString());
        });
        Future<?> logcat = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            log.info("{} open logcat", udId);
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
            log.info("Waiting for apk install timeout!");
            exit(session);
        }

        session.getUserProperties().put("schedule", ScheduleTool.schedule(() -> {
            log.info("time up!");
            if (session.isOpen()) {
                JSONObject errMsg = new JSONObject();
                errMsg.put("msg", "error");
                BytesTool.sendText(session, errMsg.toJSONString());
                exit(session);
            }
        }, BytesTool.remoteTimeout));

        startService(udIdMap.get(session), session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        JSONObject msg = JSON.parseObject(message);
        log.info("{} send: {}", session.getUserProperties().get("id").toString(), msg);
        switch (msg.getString("type")) {
            case "appList": {
                startService(udIdMap.get(session), session);
                if (outputStreamMap.get(session) != null) {
                    try {
                        outputStreamMap.get(session).write("action_get_all_app_info".getBytes(StandardCharsets.UTF_8));
                        outputStreamMap.get(session).flush();
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
                        outputStreamMap.get(session).flush();
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
                        log.error(e.getMessage());
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
                        log.error(e.getMessage());
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
                        log.error(e.getMessage());
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
        log.error(error.getMessage());
        JSONObject errMsg = new JSONObject();
        errMsg.put("msg", "error");
        BytesTool.sendText(session, errMsg.toJSONString());
    }

    private void exit(Session session) {
        synchronized (session) {
            ScheduledFuture<?> future = (ScheduledFuture<?>) session.getUserProperties().get("schedule");
            future.cancel(true);
            WebSocketSessionMap.removeSession(session);
            removeUdIdMapAndSet(session);
            Future<?> cmd = terminalMap.get(session);
            if (!cmd.isDone() || !cmd.isCancelled()) {
                try {
                    cmd.cancel(true);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
            stopService(session);
            terminalMap.remove(session);
            Future<?> logcat = logcatMap.get(session);
            if (!logcat.isDone() || !logcat.isCancelled()) {
                try {
                    logcat.cancel(true);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
            logcatMap.remove(session);
            udIdMap.remove(session);
            try {
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info("{} : quit.", session.getUserProperties().get("id").toString());
        }
    }

    public void startService(IDevice iDevice, Session session) {
        if (socketMap.get(session) != null && socketMap.get(session).isAlive()) {
            return;
        }
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            log.info(e.getMessage());
        }
        Thread manager = new Thread(() -> {
            int managerPort = PortTool.getPort();
            AndroidDeviceBridgeTool.forward(iDevice, managerPort, 2334);
            Socket managerSocket = null;
            InputStream inputStream = null;
            OutputStream outputStream = null;
            InputStreamReader isr = null;
            BufferedReader br = null;
            try {
                managerSocket = new Socket("localhost", managerPort);
                inputStream = managerSocket.getInputStream();
                outputStream = managerSocket.getOutputStream();
                outputStreamMap.put(session, outputStream);
                isr = new InputStreamReader(inputStream);
                br = new BufferedReader(isr);
                String s;
                while (managerSocket.isConnected() && !Thread.interrupted()) {
                    try {
                        if ((s = br.readLine()) == null) break;
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                    JSONObject managerDetail = new JSONObject();
                    JSONObject data = JSON.parseObject(s);
                    if (data.getString("appName") != null) {
                        managerDetail.put("msg", "appListDetail");
                    } else {
                        managerDetail.put("msg", "wifiListDetail");
                    }
                    managerDetail.put("detail", JSON.parseObject(s));
                    BytesTool.sendText(session, managerDetail.toJSONString());
                }
            } catch (IOException e) {
                log.info("error: {}", e.getMessage());
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (isr != null) {
                    try {
                        isr.close();
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
                if (managerSocket != null) {
                    try {
                        managerSocket.close();
                        log.info("manager socket closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            AndroidDeviceBridgeTool.removeForward(iDevice, managerPort, 2334);
            outputStreamMap.remove(session);
            log.info("manager done.");
        });
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

    private void stopService(Session session) {
        if (outputStreamMap.get(session) != null) {
            try {
                outputStreamMap.get(session).write("org.cloud.sonic.android.STOP".getBytes(StandardCharsets.UTF_8));
                outputStreamMap.get(session).flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (socketMap.get(session) != null) {
            socketMap.get(session).interrupt();
            int wait = 0;
            while (!socketMap.get(session).isInterrupted()) {
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
        socketMap.remove(session);
    }
}
