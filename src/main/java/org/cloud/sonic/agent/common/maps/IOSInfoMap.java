package org.cloud.sonic.agent.common.maps;

import com.alibaba.fastjson.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IOSInfoMap {
    private static Map<String, String> sizeMap = new ConcurrentHashMap<String, String>();

    public static Map<String, String> getSizeMap() {
        return sizeMap;
    }

    private static Map<String, JSONObject> detailMap = new ConcurrentHashMap<String, JSONObject>();

    public static Map<String, JSONObject> getDetailMap() {
        return detailMap;
    }
}
