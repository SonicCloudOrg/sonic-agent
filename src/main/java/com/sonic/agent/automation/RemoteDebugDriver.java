package com.sonic.agent.automation;

import com.sonic.agent.tools.PortTool;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/29 0:28
 */
@ConditionalOnProperty(value = "modules.webview.enable", havingValue = "true")
@Configuration
public class RemoteDebugDriver {
    private static final Logger logger = LoggerFactory.getLogger(RemoteDebugDriver.class);
    private static String chromePath;
    private static String debugHost;
    private static int chromePort;
    public static WebDriver webDriver;
    @Value("${modules.webview.chrome-driver-path}")
    private String path;
    @Value("${modules.webview.chrome-driver-debug-port}")
    private int port;
    @Value("${sonic.agent.host}")
    private String agentHost;

    @Bean
    public void setChromePath() {
        chromePath = path;
        chromePort = port;
        debugHost = agentHost;
    }

//    @Bean
//    @DependsOn(value = "startChromeDriver")
//    public ServletRegistrationBean proxyServletRegistration() {
//        ServletRegistrationBean registrationBean = new ServletRegistrationBean(new ProxyServlet(), "/agent/*");
//        Map<String, String> params = ImmutableMap.of(
//                "targetUri", "http://localhost:" + port + "/devtools",
//                "log", "false");
//        registrationBean.setInitParameters(params);
//        return registrationBean;
//    }

    @Bean
    @DependsOn(value = "setChromePath")
    public static void startChromeDriver() {
        logger.info("开启webview相关功能");
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
            chromeOptions.addArguments("--remote-debugging-address=" + debugHost);
            chromeOptions.addArguments("--headless");
            chromeOptions.addArguments("--no-sandbox");
            chromeOptions.addArguments("--disable-gpu");
            chromeOptions.addArguments("--disable-dev-shm-usage");
            desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
            webDriver = new ChromeDriver(desiredCapabilities);
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
