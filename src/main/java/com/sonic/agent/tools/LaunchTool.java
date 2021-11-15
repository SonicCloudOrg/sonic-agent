package com.sonic.agent.tools;

import com.sonic.agent.automation.AppiumServer;
import com.sonic.agent.automation.RemoteDebugDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

@Component
@DependsOn("nettyMsgInit")
public class LaunchTool implements ApplicationRunner {
    private final Logger logger = LoggerFactory.getLogger(LaunchTool.class);

    @Override
    public void run(ApplicationArguments args) {
        AppiumServer.start();
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        RemoteDebugDriver.close();
        AppiumServer.close();
        Thread.sleep(3000);
        while (true) {
            if (!AppiumServer.service.isRunning()) {
                break;
            }
        }
    }
}
