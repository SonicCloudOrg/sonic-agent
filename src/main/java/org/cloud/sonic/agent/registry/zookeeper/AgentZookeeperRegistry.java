/**
 * Copyright (C) [SonicCloudOrg] Sonic Project
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cloud.sonic.agent.registry.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.registry.zookeeper.ZookeeperRegistry;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;
import org.cloud.sonic.common.models.domain.Agents;
import org.cloud.sonic.common.models.interfaces.AgentStatus;
import org.cloud.sonic.common.services.AgentsService;
import org.cloud.sonic.common.tools.SpringTool;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Collection;

/**
 * @author JayWenStar
 * @date 2022/4/8 3:24 下午
 */
public class AgentZookeeperRegistry extends ZookeeperRegistry {

    public AgentZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url, zookeeperTransporter);
    }

    @Override
    public void doRegister(URL url) {
        super.doRegister(url);
        if (!"dubbo".equals(url.getProtocol())) {
            return;
        }

        String host = url.getHost();
        int port = url.getPort();
        String version = SpringTool.getPropertiesValue("spring.version");
        String secretKey = SpringTool.getPropertiesValue("sonic.agent.key");
        String systemType = System.getProperty("os.name");
        AgentsService agentsService = SpringTool.getBean(AgentsService.class);

        Agents currentAgent = agentsService.findBySecretKey(secretKey);
        if (ObjectUtils.isEmpty(currentAgent)) {
            throw new RuntimeException("配置 sonic.agent.key 错误，请检查！");
        }
        currentAgent.setHost(host)
                .setPort(port)
                .setStatus(AgentStatus.ONLINE)
                .setSystemType(systemType)
                .setVersion(version);
        agentsService.saveAgents(currentAgent);
    }
}
