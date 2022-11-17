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
package org.cloud.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.maps.AndroidDeviceManagerMap;
import org.cloud.sonic.agent.transport.TransportWorker;

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
        deviceDetail.put("msg", "deviceDetail");
        deviceDetail.put("udId", udId);
        deviceDetail.put("status", status);
        TransportWorker.send(deviceDetail);
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
