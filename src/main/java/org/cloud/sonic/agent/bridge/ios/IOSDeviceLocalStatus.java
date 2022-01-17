package org.cloud.sonic.agent.bridge.ios;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.interfaces.DeviceStatus;
import org.cloud.sonic.agent.maps.IOSDeviceManagerMap;
import org.cloud.sonic.agent.maps.IOSSizeMap;
import org.cloud.sonic.agent.netty.NettyThreadPool;

public class IOSDeviceLocalStatus {

    public static void send(String udId, String status) {
        JSONObject deviceDetail = new JSONObject();
        deviceDetail.put("msg", "deviceDetail");
        deviceDetail.put("size", IOSSizeMap.getMap().get(udId));
        deviceDetail.put("udId", udId);
        deviceDetail.put("status", status);
        NettyThreadPool.send(deviceDetail);
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
        if (TIDeviceTool.getDeviceList().contains(udId)
                && IOSDeviceManagerMap.getMap().get(udId) != null) {
            if (IOSDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.DEBUGGING)
                    || IOSDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.TESTING)) {
                send(udId, DeviceStatus.ONLINE);
            }
        }
        IOSDeviceManagerMap.getMap().remove(udId);
    }

    public static void finishError(String udId) {
        if (TIDeviceTool.getDeviceList().contains(udId)
                && IOSDeviceManagerMap.getMap().get(udId) != null) {
            if (IOSDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.DEBUGGING)
                    || IOSDeviceManagerMap.getMap().get(udId).equals(DeviceStatus.TESTING)) {
                send(udId, DeviceStatus.ERROR);
            }
        }
        IOSDeviceManagerMap.getMap().remove(udId);
    }
}
