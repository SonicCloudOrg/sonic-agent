package com.sonic.agent.maps;

import com.sonic.agent.common.DeviceStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ZhouYiXun
 * @des 本地自己维护一下设备状态
 * @date 2021/08/16 10:26
 */
public class AndroidDeviceManagerMap {
    private static Map<String, DeviceStatus> deviceStatusMap = new ConcurrentHashMap<String, DeviceStatus>();
    public static Map<String, DeviceStatus> getMap() {
        return deviceStatusMap;
    }
}
