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
package org.cloud.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.maps.AndroidDeviceManagerMap;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.SpringTool;

/**
 * @author ZhouYiXun
 * @des 本地自定义的状态变更，不是通过adb的变更
 * @date 2021/08/16 19:26
 */
public class AndroidDeviceLocalStatus {

    /**
     * @param udId
     * @param status
     * @return void
     * @author ZhouYiXun
     * @des 发送状态变更消息
     * @date 2021/8/16 20:56
     */
    public static void send(String udId, String status) {
        JSONObject deviceDetail = new JSONObject();
        deviceDetail.put("udId", udId);
        deviceDetail.put("status", status);
        deviceDetail.put("agentId", AgentZookeeperRegistry.currentAgent.getId());
        SpringTool.getBean(AgentManagerTool.class).devicesStatus(deviceDetail);
    }

    /**
     * @param udId
     * @return void
     * @author ZhouYiXun
     * @des 开始测试
     * @date 2021/8/16 20:57
     */
    public static boolean startTest(String udId) {
        synchronized (AndroidDeviceLocalStatus.class) {
            if (AndroidDeviceManagerMap.getMap().get(udId) == null) {
                send(udId, DeviceStatus.TESTING);
                AndroidDeviceManagerMap.getMap().put(udId, DeviceStatus.TESTING);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * @param udId
     * @return void
     * @author ZhouYiXun
     * @des 开始调试
     * @date 2021/8/16 20:57
     */
    public static void startDebug(String udId) {
        send(udId, DeviceStatus.DEBUGGING);
        AndroidDeviceManagerMap.getMap().put(udId, DeviceStatus.DEBUGGING);
    }

    /**
     * @param udId
     * @return void
     * @author ZhouYiXun
     * @des 使用完毕
     * @date 2021/8/16 19:47
     */
    public static void finish(String udId) {
        if (AndroidDeviceBridgeTool.getIDeviceByUdId(udId) != null
                && AndroidDeviceManagerMap.getMap().get(udId) != null) {
            if (AndroidDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.DEBUGGING)
                    || AndroidDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.TESTING)) {
                send(udId, DeviceStatus.ONLINE);
            }
        }
        AndroidDeviceManagerMap.getMap().remove(udId);
    }

    /**
     * @param udId
     * @return void
     * @author ZhouYiXun
     * @des 使用完毕异常
     * @date 2021/8/16 19:47
     */
    public static void finishError(String udId) {
        if (AndroidDeviceBridgeTool.getIDeviceByUdId(udId) != null
                && AndroidDeviceManagerMap.getMap().get(udId) != null) {
            if (AndroidDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.DEBUGGING)
                    || AndroidDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.TESTING)) {
                send(udId, DeviceStatus.ERROR);
            }
        }
        AndroidDeviceManagerMap.getMap().remove(udId);
    }
}
