package com.sonic.agent.maps;

import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import javax.websocket.Session;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ZhouYiXun
 * @des webSocket的sessionId与session储存
 * @date 2021/8/16 19:54
 */
public class WebSocketSessionMap {

    /**
     * key: sessionId    value: session
     */
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();


    public static Map<String, Session> getSessionMap() {
        return sessionMap;
    }

    public static void addSession(@NonNull Session session) {
        sessionMap.put(session.getId(), session);
    }

    public static void removeSession(@NonNull Session session) {
        removeSession(session.getId());
    }

    public static void removeSession(String sessionId) {
        Assert.hasText(sessionId, "sessionId must not be blank");
        sessionMap.remove(sessionId);
    }

    public static Session getSession(String sessionId) {
        Assert.hasText(sessionId, "sessionId must not be blank");
        return sessionMap.get(sessionId);
    }

}
