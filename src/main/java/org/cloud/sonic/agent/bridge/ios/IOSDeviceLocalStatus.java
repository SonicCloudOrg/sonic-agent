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
