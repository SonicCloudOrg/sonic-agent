package org.cloud.sonic.agent.common.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalProcessMap {
    private static Map<String, Process> processMap = new ConcurrentHashMap<>();

    public static Map<String, Process> getMap() {
        return processMap;
    }
}
