package org.cloud.sonic.agent.websockets;

import org.springframework.util.StringUtils;

import javax.websocket.Session;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author JayWenStar
 * @date 2022/4/10 11:06 下午
 */
public interface IIOSWSServer {

    Map<Session, String> udIdMap = new ConcurrentHashMap<>();
    Set<String> udIdSet = Collections.synchronizedSet(new HashSet<>());

    default Map<Session, String> getUdIdMap() {
        return udIdMap;
    }

    default Set<String> getUdIdSet() {
        return udIdSet;
    }

    default void saveUdIdMapAndSet(Session session, String udId) {
        udIdMap.put(session, udId);
        if (StringUtils.hasText(udId)) {
            udIdSet.add(udId);
        }
    }

    default void removeUdIdMapAndSet(Session session) {
        String udId = udIdMap.remove(session);
        if (StringUtils.hasText(udId)) {
            udIdSet.remove(udId);
        }
    }

}
