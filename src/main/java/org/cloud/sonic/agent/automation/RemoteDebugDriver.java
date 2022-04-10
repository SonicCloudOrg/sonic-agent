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

import org.cloud.sonic.agent.tools.PortTool;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
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
    private static String chromePath;
    public static int chromePort;
    public static WebDriver webDriver;
    @Value("${modules.webview.chrome-driver-path}")
    private String path;
    @Value("${modules.webview.chrome-driver-debug-port}")
    private int port;

    @Bean
    public void setChromePath() {
        chromePath = path;
        chromePort = port;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        startChromeDriver();
    }

    public static void startChromeDriver() {
        logger.info("开启webview相关功能");
        System.setProperty("webdriver.chrome.silentOutput", "true");
        try {
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
            ChromeOptions chromeOptions = new ChromeOptions();
            System.setProperty("webdriver.chrome.driver", chromePath);
            if (chromePort == 0) {
                int debugPort = PortTool.getPort();
                chromePort = debugPort;
                chromeOptions.addArguments("--remote-debugging-port=" + debugPort);
            } else {
                chromeOptions.addArguments("--remote-debugging-port=" + chromePort);
            }
            chromeOptions.addArguments("--remote-debugging-address=0.0.0.0");
            chromeOptions.addArguments("--headless");
            chromeOptions.addArguments("--no-sandbox");
            chromeOptions.addArguments("--disable-gpu");
            chromeOptions.addArguments("--disable-dev-shm-usage");
            desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
            webDriver = new ChromeDriver(desiredCapabilities);
            logger.info("chromeDriver启动完毕！");
        } catch (Exception e) {
            logger.info("chromeDriver启动失败！");
        }
    }

    public static void close() {
        if (webDriver != null) {
            webDriver.quit();
        }
    }
}
