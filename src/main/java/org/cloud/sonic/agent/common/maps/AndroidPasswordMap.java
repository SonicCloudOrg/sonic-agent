package org.cloud.sonic.agent.common.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ZhouYiXun
 * @des 存放密码
 * @date 2021/8/27 22:01
 */
public class AndroidPasswordMap {
    private static Map<String, String> devicePasswordMap = new ConcurrentHashMap<String, String>();

    public static Map<String, String> getMap() {
        return devicePasswordMap;
    }
}
