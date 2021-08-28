package com.sonic.agent.tools;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.automation.AppiumServer;
import com.sonic.agent.rabbitmq.RabbitMQThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

@Component
@DependsOn("rabbitMsgInit")
public class LaunchTool implements ApplicationRunner {
    private final Logger logger = LoggerFactory.getLogger(LaunchTool.class);
    @Value("${spring.version}")
    private String version;
    @Value("${sonic.agent.id}")
    private int agentId;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("当前apex-agent版本为：" + version);
        AppiumServer.start();
        AgentTool.agentId = agentId;
        JSONObject agentInfo = new JSONObject();
        agentInfo.put("msg", "agentInfo");
        agentInfo.put("port", GetWebStartPort.getTomcatPort());
        agentInfo.put("version", version);
        agentInfo.put("systemType", System.getProperty("os.name"));
        agentInfo.put("ip", LocalHostTool.getHostIp());
        agentInfo.put("id", AgentTool.agentId);
        RabbitMQThread.send(agentInfo);
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        JSONObject agentOffLine = new JSONObject();
        agentOffLine.put("msg", "offLine");
        agentOffLine.put("id", AgentTool.agentId);
        RabbitMQThread.send(agentOffLine);
        AppiumServer.close();
        Thread.sleep(3000);
        while (true) {
            if (!AppiumServer.service.isRunning()) {
                break;
            }
        }
    }
}
