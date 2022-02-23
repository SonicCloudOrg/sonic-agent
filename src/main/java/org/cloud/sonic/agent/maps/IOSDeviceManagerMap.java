package org.cloud.sonic.agent.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ZhouYiXun
 * @des 本地自己维护一下设备状态
 * @date 2021/08/16 10:26
 */
public class IOSDeviceManagerMap {
    private static Map<String, String> deviceStatusMap = new ConcurrentHashMap<String, String>();
    public static Map<String, String> getMap() {
        return deviceStatusMap;
    }
}
