package com.sonic.agent.maps;

import com.sonic.agent.automation.AndroidStepHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HandlerMap {
    private static Map<String, AndroidStepHandler> androidHandlerMap = new ConcurrentHashMap<String, AndroidStepHandler>();
    public static Map<String, AndroidStepHandler> getAndroidMap() {
        return androidHandlerMap;
    }
}
