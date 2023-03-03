/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.websockets;

import org.springframework.util.StringUtils;

import jakarta.websocket.Session;
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
