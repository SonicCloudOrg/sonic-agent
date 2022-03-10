package org.cloud.sonic.agent.common.maps;

import javax.websocket.Session;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniCapMap {
    private static Map<Session, Thread> miniCapMap = new ConcurrentHashMap<>();

    public static Map<Session, Thread> getMap() {
        return miniCapMap;
    }
}
