package org.cloud.sonic.agent.maps;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IOSProcessMap {
    private static Map<String, List<Process>> deviceProcessMap = new ConcurrentHashMap<>();
    public static Map<String, List<Process>> getMap() {
        return deviceProcessMap;
    }
}
