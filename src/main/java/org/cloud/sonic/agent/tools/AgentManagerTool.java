package org.cloud.sonic.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @author ZhouYiXun
 * @des Agent管理者
 * @date 2021/8/26 22:23
 */
@Component
public class AgentManagerTool {
    private final Logger logger = LoggerFactory.getLogger(AgentManagerTool.class);

    @Autowired
    private ConfigurableApplicationContext context;

    public void stop() {
        context.close();
        logger.info("Bye！");
    }

    public void update(){
        logger.info("Updating...");

    }
}
