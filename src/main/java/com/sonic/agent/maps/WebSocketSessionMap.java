package com.sonic.agent.maps;

import javax.websocket.Session;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ZhouYiXun
 * @des webSocket的sessionId与session储存
 * @date 2021/8/16 19:54
 */
public class WebSocketSessionMap {
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<String, Session>();
    public static Map<String, Session> getMap() {
        return sessionMap;
    }
}
