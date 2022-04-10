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

import com.alibaba.fastjson.JSON;
import lombok.SneakyThrows;
import org.apache.curator.framework.CuratorFramework;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.zookeeper.ZookeeperRegistry;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;
import org.apache.zookeeper.CreateMode;
import org.cloud.sonic.agent.event.AgentRegisteredEvent;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.common.models.domain.Agents;
import org.cloud.sonic.common.models.interfaces.AgentStatus;
import org.cloud.sonic.common.services.AgentsService;
import org.cloud.sonic.common.tools.SpringTool;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.ObjectUtils;

import java.util.concurrent.TimeUnit;

/**
 * @author JayWenStar
 * @date 2022/4/8 3:24 下午
 */
public class AgentZookeeperRegistry extends ZookeeperRegistry {

    private boolean registered = false;

    public static Agents currentAgent;

    public AgentZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url, zookeeperTransporter);
    }

    @SneakyThrows
    @Override
    public void doRegister(URL url) {
        super.doRegister(url);
        if (!"dubbo".equals(url.getProtocol())) {
            return;
        }

        if (registered) {
            logger.debug("已注册，无需重复注册");
            return;
        }

        AgentsService agentsService = SpringTool.getBean(AgentsService.class);
        AgentManagerTool agentManagerTool = SpringTool.getBean(AgentManagerTool.class);
        ApplicationEventPublisher publisher = SpringTool.getApplicationContext();
        int count = 1;
        while (!agentManagerTool.checkServerOnline() && count <= 6) {
            logger.warn("未检测到server上线，10s后重试");
            TimeUnit.SECONDS.sleep(10);
            count++;
        }
        if (count > 6) {
            String tips = "请确保server启动后再启动agent";
            logger.error(tips);
            AgentManagerTool.stop(tips);
            return;
        }
        logger.info("检查到server上线，开始注册");

        String host = url.getHost();
        int rpcPort = url.getPort();
        String version = SpringTool.getPropertiesValue("spring.version");
        String secretKey = SpringTool.getPropertiesValue("sonic.agent.key");
        int webPort = Integer.parseInt(SpringTool.getPropertiesValue("sonic.agent.port"));
        String systemType = System.getProperty("os.name");

        // 保存记录
        Agents currentAgent = agentsService.findBySecretKey(secretKey);
        if (ObjectUtils.isEmpty(currentAgent)) {
            throw new RuntimeException("配置 sonic.agent.key 错误，请检查！");
        }
        currentAgent.setHost(host)
                .setPort(webPort)
                .setRpcPort(rpcPort)
                .setStatus(AgentStatus.ONLINE)
                .setSystemType(systemType)
                .setVersion(version);
        agentsService.updateAgentsByLockVersion(currentAgent);
        AgentZookeeperRegistry.currentAgent = currentAgent;

        // 注册节点
        currentAgent = agentsService.findBySecretKey(secretKey);
        CuratorFramework curatorFramework = SpringTool.getApplicationContext().getBean(CuratorFramework.class);
        String nodePath = "/sonic-agent/%s".formatted(currentAgent.getId());
        try {
            if (curatorFramework.checkExists().forPath(nodePath) != null) {
                curatorFramework.delete().guaranteed().forPath(nodePath);
            }
            curatorFramework.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(nodePath, JSON.toJSONBytes(currentAgent));
        } catch (Exception e) {
            throw new RuntimeException("注册节点失败！");
        }
        registered = true;

        publisher.publishEvent(new AgentRegisteredEvent(this));
    }
}
