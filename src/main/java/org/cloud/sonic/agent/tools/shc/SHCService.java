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
package org.cloud.sonic.agent.tools.shc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.common.maps.DevicesBatteryMap;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.common.models.domain.Devices;
import org.cloud.sonic.common.services.DevicesService;
import org.cloud.sonic.common.tools.SpringTool;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eason
 * @des sonic-hub-connector
 */
@Slf4j
public class SHCService {
    public static Map<String, Integer> positionMap = new ConcurrentHashMap<>();
    public static DevicesService devicesService = SpringTool.getBean(DevicesService.class);

    public enum SHCStatus {
        OPEN, CLOSE
    }

    public static WebSocketClient shcClient;
    public static SHCStatus status = SHCStatus.CLOSE;

    public static void connect() {
        if (shcClient != null && shcClient.isOpen()) {
            return;
        } else {
            status = SHCStatus.CLOSE;
        }
        try {
            URI ws = new URI("ws://localhost:8687");
            shcClient = new WebSocketClient(ws) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    log.info("shc connected.");
                }

                @Override
                public void onMessage(String s) {
                    JSONObject result = JSON.parseObject(s);
                    String udId = result.getString("udId");
                    int position = result.getInteger("position");
                    if (result.getString("msg").equals("add")) {
                        positionMap.put(udId, position);
                        devicesService.deviceStatus(updateMsg(udId, position, null));
                    }
                }

                @Override
                public void onClose(int i, String s, boolean b) {
                    log.info("shc close.");
                }

                @Override
                public void onError(Exception e) {

                }
            };
            shcClient.connect();
            int waitConnect = 0;
            while (!shcClient.isOpen()) {
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
            if (shcClient.isOpen()) {
                status = SHCStatus.OPEN;
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public static String generateMsg(String method, String params) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("service", "sonic-hub-connector");
        jsonObject.put("version", "1.0.0");
        jsonObject.put("method", method);
        jsonObject.put("params", params);
        return jsonObject.toJSONString();
    }

    public static JSONObject updateMsg(String udId, Integer position, Integer gear) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("agentId", AgentZookeeperRegistry.currentAgent.getId());
        jsonObject.put("udId", udId);
        jsonObject.put("position", position);
        jsonObject.put("gear", gear);
        return jsonObject;
    }

    public static Integer getGear(String udId) {
        return DevicesBatteryMap.getGearMap().get(udId);
    }

    public static void setGear(String udId, int gear) {
        Integer currentGear = getGear(udId);
        if (currentGear == null || currentGear != gear) {
            int position = 0;
            if (positionMap.get(udId) != null) {
                position = positionMap.get(udId);
            } else {
                Devices devices = devicesService.findByAgentIdAndUdId(AgentZookeeperRegistry.currentAgent.getId(), udId);
                if (devices != null && devices.getPosition() != null && devices.getPosition() != 0) {
                    positionMap.put(udId, devices.getPosition());
                    position = devices.getPosition();
                }
            }
            if (position != 0) {
                shcClient.send(generateMsg("gear",
                        String.format("%d,%d", position, gear)));
                log.info("Set {} to Gear {}!", udId, gear);
                devicesService.deviceStatus(updateMsg(udId, null, gear));
                DevicesBatteryMap.getGearMap().put(udId, gear);
            }
        }
    }

    public static Integer getTemp(String udId) {
        return DevicesBatteryMap.getTempMap().get(udId);
    }
}
