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
package org.cloud.sonic.agent.automation;

import com.github.kklisura.cdt.launch.ChromeArguments;
import com.github.kklisura.cdt.launch.ChromeLauncher;
import org.cloud.sonic.agent.tools.PortTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/29 0:28
 */
@ConditionalOnProperty(value = "modules.webview.enable", havingValue = "true")
@Configuration
public class RemoteDebugDriver implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(RemoteDebugDriver.class);
    public static int debugPort;
    public static ChromeLauncher launcher;
    @Value("${modules.webview.remote-debug-port}")
    private int port;

    @Bean
    public void setChromePath() {
        if (port == 0) {
            debugPort = PortTool.getPort();
        } else {
            debugPort = port;
        }
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        startChromeDebugger();
    }

    public static void startChromeDebugger() {
        logger.info("Enable webview remote debugger.");
        try {
            launcher = new ChromeLauncher();
            ChromeArguments chromeArguments = ChromeArguments.builder()
                    .additionalArguments("remote-debugging-address", "0.0.0.0")
                    .additionalArguments("no-sandbox", true)
                    .additionalArguments("disable-dev-shm-usage", true)
                    .headless().remoteDebuggingPort(debugPort)
                    .disableGpu()
                    .build();
            launcher.launch(chromeArguments);
            logger.info("Webview remote debugger start successful.");
        } catch (Exception e) {
            logger.info("Webview remote debugger start failed.");
        }
    }

    public static void close() {
        if (launcher != null) {
            launcher.close();
        }
    }
}
