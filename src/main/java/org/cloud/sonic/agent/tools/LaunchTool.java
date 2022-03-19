package org.cloud.sonic.agent.tools;

import org.cloud.sonic.agent.automation.AppiumServer;
import org.cloud.sonic.agent.automation.RemoteDebugDriver;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.common.maps.IOSProcessMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.util.List;

@Component
@DependsOn("nettyMsgInit")
public class LaunchTool implements ApplicationRunner {
    private final Logger logger = LoggerFactory.getLogger(LaunchTool.class);
    @Value("${modules.appium.port}")
    private int port;

    @Override
    public void run(ApplicationArguments args) {
        File testFile = new File("test-output");
        if (!testFile.exists()) {
            testFile.mkdirs();
        }
        AppiumServer.start(port);
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        RemoteDebugDriver.close();
        for (String key : GlobalProcessMap.getMap().keySet()) {
            Process ps = GlobalProcessMap.getMap().get(key);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
        for (String key : IOSProcessMap.getMap().keySet()) {
            List<Process> ps = IOSProcessMap.getMap().get(key);
            for (Process p : ps) {
                p.children().forEach(ProcessHandle::destroy);
                p.destroy();
            }
        }
        AppiumServer.close();
        while (AppiumServer.service != null) {
            if (!AppiumServer.service.isRunning()) {
                break;
            } else {
                Thread.sleep(1000);
            }
        }
        logger.info("Release done!");
    }
}
