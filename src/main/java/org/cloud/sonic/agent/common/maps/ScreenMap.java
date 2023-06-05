package org.cloud.sonic.agent.common.maps;

import jakarta.websocket.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenMap {
    private static Map<Session, Thread> miniCapMap = new ConcurrentHashMap<>();

    public static Map<Session, Thread> getMap() {
        return miniCapMap;
    }
}
