package org.cloud.sonic.agent.common.maps;

import javax.websocket.Session;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScrcpyMap {
    private static Map<Session, Thread> scrcpyMap = new ConcurrentHashMap<>();

    public static Map<Session, Thread> getMap() {
        return scrcpyMap;
    }
}
