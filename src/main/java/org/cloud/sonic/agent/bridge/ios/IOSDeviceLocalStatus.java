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
package org.cloud.sonic.agent.bridge.ios;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.maps.IOSDeviceManagerMap;
import org.cloud.sonic.agent.common.maps.IOSInfoMap;
import org.cloud.sonic.agent.transport.TransportWorker;

public class IOSDeviceLocalStatus {

    public static void send(String udId, String status) {
        JSONObject deviceDetail = new JSONObject();
        deviceDetail.put("msg", "deviceDetail");
        deviceDetail.put("size", IOSInfoMap.getSizeMap().get(udId));
        deviceDetail.put("udId", udId);
        deviceDetail.put("status", status);
        TransportWorker.send(deviceDetail);
    }

    public static boolean startTest(String udId) {
        synchronized (IOSDeviceLocalStatus.class) {
            if (IOSDeviceManagerMap.getMap().get(udId) == null) {
                send(udId, DeviceStatus.TESTING);
                IOSDeviceManagerMap.getMap().put(udId, DeviceStatus.TESTING);
                return true;
            } else {
                return false;
            }
        }
    }

    public static void startDebug(String udId) {
        send(udId, DeviceStatus.DEBUGGING);
        IOSDeviceManagerMap.getMap().put(udId, DeviceStatus.DEBUGGING);
    }

    public static void finish(String udId) {
        if (SibTool.getDeviceList().contains(udId)
                && IOSDeviceManagerMap.getMap().get(udId) != null) {
            if (IOSDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.DEBUGGING)
                    || IOSDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.TESTING)) {
                send(udId, DeviceStatus.ONLINE);
            }
        }
        IOSDeviceManagerMap.getMap().remove(udId);
    }

    public static void finishError(String udId) {
        if (SibTool.getDeviceList().contains(udId)
                && IOSDeviceManagerMap.getMap().get(udId) != null) {
            if (IOSDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.DEBUGGING)
                    || IOSDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.TESTING)) {
                send(udId, DeviceStatus.ERROR);
            }
        }
        IOSDeviceManagerMap.getMap().remove(udId);
    }
}
