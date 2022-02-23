package org.cloud.sonic.agent.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ZhouYiXun
 * @date 2022/1/23 22:01
 */
public class AndroidAPKMap {
    private static Map<String, Boolean> deviceMap = new ConcurrentHashMap<String, Boolean>();

    public static Map<String, Boolean> getMap() {
        return deviceMap;
    }
}
