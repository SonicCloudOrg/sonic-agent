/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.websockets;

import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.tools.BytesTool;
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

    default String getHost() {
        return BytesTool.agentHost;
    }

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
