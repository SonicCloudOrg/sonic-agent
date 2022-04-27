package org.cloud.sonic.agent.common.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DevicesBatteryMap {
    private static Map<String, Integer> deviceTempMap = new ConcurrentHashMap<>();
    public static Map<String, Integer> getTempMap() {
        return deviceTempMap;
    }

    private static Map<String, Integer> deviceGearMap = new ConcurrentHashMap<>();
    public static Map<String, Integer> getGearMap() {
        return deviceGearMap;
    }
}
