package org.cloud.sonic.agent.common.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IOSSizeMap {
    private static Map<String, String> sizeMap = new ConcurrentHashMap<String, String>();
    public static Map<String, String> getMap() {
        return sizeMap;
    }
}
