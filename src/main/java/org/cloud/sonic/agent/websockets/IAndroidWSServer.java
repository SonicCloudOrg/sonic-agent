package org.cloud.sonic.agent.websockets;

import com.android.ddmlib.IDevice;
import org.bytedeco.librealsense.device;
import org.springframework.util.ObjectUtils;

import javax.websocket.Session;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author JayWenStar
 * @date 2022/4/10 10:54 下午
 */
public interface IAndroidWSServer {
    Map<Session, IDevice> udIdMap = new ConcurrentHashMap<>();
    Set<String> udIdSet = Collections.synchronizedSet(new HashSet<>());

    default Map<Session, IDevice> getUdIdMap() {
        return udIdMap;
    }

    default Set<String> getUdIdSet() {
        return udIdSet;
    }

    default void saveUdIdMapAndSet(Session session, IDevice device) {
        udIdMap.put(session, device);
        if (!ObjectUtils.isEmpty(device)) {
            udIdSet.add(device.getSerialNumber());
        }
    }

    default void removeUdIdMapAndSet(Session session) {
        IDevice device = udIdMap.remove(session);
        if (!ObjectUtils.isEmpty(device)) {
            udIdSet.remove(device.getSerialNumber());
        }
    }
}
