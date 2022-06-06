/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.registry.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.dubbo.config.annotation.DubboReference;
import org.cloud.sonic.agent.event.AgentRegisteredEvent;
import org.cloud.sonic.common.services.DevicesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author JayWenStar
 * @date 2022/4/12 4:21 下午
 */
@Component
@Slf4j
@Order(value = Ordered.LOWEST_PRECEDENCE)
public class ServerOnlineListener implements ApplicationListener<AgentRegisteredEvent> {

    @Autowired private CuratorFramework curatorFramework;
    @DubboReference private DevicesService devicesService;

    @Override
    public void onApplicationEvent(AgentRegisteredEvent event) {
        String nodePath = "/dubbo/org.cloud.sonic.common.services.DevicesService/providers";
        CuratorCache curatorCache = CuratorCache.build(curatorFramework, nodePath);
        curatorCache.listenable().addListener((type, oldData, data) -> {
            log.info("found server status: {}", type);
            if (CuratorCacheListener.Type.NODE_CREATED != type) {
                return;
            }
            devicesService.correctionAllDevicesStatus();
            log.info("server online now. request refresh devices status");
        });
        curatorCache.start();
        log.info("start watch {}", nodePath);
    }

}
