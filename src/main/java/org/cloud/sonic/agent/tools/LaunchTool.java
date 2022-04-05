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
    @Value("${modules.appium.enable}")
    private boolean isEnableAppium;
    @Value("${modules.sgm.enable}")
    private boolean isEnableSgm;

    @Override
    public void run(ApplicationArguments args) {
        File testFile = new File("test-output");
        if (!testFile.exists()) {
            testFile.mkdirs();
        }
        if (isEnableSgm) {
            // fixme 本地调试环境忽略
            // SGMTool.init();
            new Thread(() -> {
                File file = new File("plugins/sonic-go-mitmproxy-ca-cert.pem");
                if (!file.exists()) {
                    logger.info("开始生成ca证书...");
                    SGMTool.startProxy("init", SGMTool.getCommand());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // 仅生成证书
                    SGMTool.stopProxy("init");
                    file = new File("plugins/sonic-go-mitmproxy-ca-cert.pem");
                    if (!file.exists()) {
                        logger.info("sonic-go-mitmproxy-ca证书生成失败！");
                    } else {
                        logger.info("sonic-go-mitmproxy-ca证书生成成功！");
                    }
                }
            }).start();
        }
        if (isEnableAppium) {
            AppiumServer.start(port);
        }
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
