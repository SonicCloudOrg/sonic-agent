/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.config.WsEndpointConfigure;
import org.cloud.sonic.agent.common.maps.AndroidAPKMap;
import org.cloud.sonic.agent.common.maps.ScreenMap;
import org.cloud.sonic.agent.tests.android.minicap.MiniCapUtil;
import org.cloud.sonic.agent.tests.android.scrcpy.ScrcpyServerUtil;
import org.cloud.sonic.agent.tools.BytesTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ServerEndpoint(value = "/websockets/android/screen/{key}/{udId}/{token}", configurator = WsEndpointConfigure.class)
public class AndroidScreenWSServer implements IAndroidWSServer {

    private final Logger logger = LoggerFactory.getLogger(AndroidScreenWSServer.class);
    @Value("${sonic.agent.key}")
    private String key;
    private Map<Session, Thread> rotationMap = new ConcurrentHashMap<>();
    private Map<Session, Integer> rotationStatusMap = new ConcurrentHashMap<>();
    private Map<Session, String> typeMap = new ConcurrentHashMap<>();
    private Map<Session, String> picMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("key") String secretKey,
                       @PathParam("udId") String udId, @PathParam("token") String token) throws Exception {
        if (secretKey.length() == 0 || (!secretKey.equals(key)) || token.length() == 0) {
            logger.info("拦截访问！");
            return;
        }
        session.getUserProperties().put("udId", udId);
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        if (iDevice == null) {
            logger.info("设备未连接，请检查！");
            return;
        }
        AndroidDeviceBridgeTool.screen(iDevice, "abort");
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
            logger.info("等待安装超时！");
            removeUdIdMapAndSet(session);
        }
    }

    @OnClose
    public void onClose(Session session) {
        exit(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.error(error.getMessage());
        error.printStackTrace();
        JSONObject errMsg = new JSONObject();
        errMsg.put("msg", "error");
        BytesTool.sendText(session, errMsg.toJSONString());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        JSONObject msg = JSON.parseObject(message);
        logger.info("{} send: {}",session.getId(), msg);
        switch (msg.getString("type")) {
            case "switch": {
                typeMap.put(session, msg.getString("detail"));
                if (rotationMap.get(session) == null) {
                    IDevice iDevice = udIdMap.get(session);
                    if (iDevice != null) {
                        String path = AndroidDeviceBridgeTool.executeCommand(iDevice, "pm path org.cloud.sonic.android").trim()
                                .replaceAll("package:", "")
                                .replaceAll("\n", "")
                                .replaceAll("\t", "");

                        String finalPath = path;

                        Thread rotationPro = new Thread(() -> {
                            try {
                                //开始启动
                                iDevice.executeShellCommand(String.format("CLASSPATH=%s exec app_process /system/bin org.cloud.sonic.android.plugin.SonicPluginMonitorService", finalPath)
                                        , new IShellOutputReceiver() {
                                            @Override
                                            public void addOutput(byte[] bytes, int i, int i1) {
                                                String res = new String(bytes, i, i1).replaceAll("\n", "").replaceAll("\r", "");
                                                logger.info(iDevice.getSerialNumber() + " rotation: " + res);
                                                rotationStatusMap.put(session, Integer.parseInt(res));
                                                JSONObject rotationJson = new JSONObject();
                                                rotationJson.put("msg", "rotation");
                                                rotationJson.put("value", Integer.parseInt(res) * 90);
                                                BytesTool.sendText(session, rotationJson.toJSONString());
                                                startScreen(session);
                                            }

                                            @Override
                                            public void flush() {
                                            }

                                            @Override
                                            public boolean isCancelled() {
                                                return false;
                                            }
                                        }, 0, TimeUnit.MILLISECONDS);
                            } catch (Exception e) {
                                logger.info("{} rotation service stopped."
                                        , iDevice.getSerialNumber());
                                logger.error(e.getMessage());
                            }
                        });
                        rotationPro.start();
                        rotationMap.put(session, rotationPro);
                    }
                } else {
                    startScreen(session);
                }
                break;
            }
            case "pic": {
                picMap.put(session, msg.getString("detail"));
                startScreen(session);
                break;
            }
        }
    }

    private void startScreen(Session session) {
        IDevice iDevice = udIdMap.get(session);
        if (iDevice != null) {
            Thread old = ScreenMap.getMap().get(session);
            if (old != null) {
                old.interrupt();
                do {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                while (ScreenMap.getMap().get(session) != null);
            }
            if (typeMap.get(session) == null) {
                typeMap.put(session, "scrcpy");
            }
            switch (typeMap.get(session)) {
                case "scrcpy": {
                    ScrcpyServerUtil scrcpyServerUtil = new ScrcpyServerUtil();
                    Thread scrcpyThread = scrcpyServerUtil.start(iDevice.getSerialNumber(), rotationStatusMap.get(session), session);
                    ScreenMap.getMap().put(session, scrcpyThread);
                    break;
                }
                case "minicap": {
                    MiniCapUtil miniCapUtil = new MiniCapUtil();
                    AtomicReference<String[]> banner = new AtomicReference<>(new String[24]);
                    Thread miniCapThread = miniCapUtil.start(
                            iDevice.getSerialNumber(), banner, null,
                            picMap.get(session) == null ? "high" : picMap.get(session),
                            rotationStatusMap.get(session), session
                    );
                    ScreenMap.getMap().put(session, miniCapThread);
                    break;
                }
            }
            JSONObject picFinish = new JSONObject();
            picFinish.put("msg", "picFinish");
            BytesTool.sendText(session, picFinish.toJSONString());
        }
    }



    private void exit(Session session) {
        removeUdIdMapAndSet(session);
        if (rotationMap.get(session) != null) {
            rotationMap.get(session).interrupt();
        }
        rotationMap.remove(session);
        if (ScreenMap.getMap().get(session) != null) {
            ScreenMap.getMap().get(session).interrupt();
        }
        typeMap.remove(session);
        picMap.remove(session);
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
