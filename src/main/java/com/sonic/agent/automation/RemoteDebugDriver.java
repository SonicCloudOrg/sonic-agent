package com.sonic.agent.automation;

import com.google.common.collect.ImmutableMap;
import com.sonic.agent.tools.PortTool;
import com.sonic.agent.websockets.WebViewWSServer;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.PostConstruct;
import javax.servlet.Servlet;
import java.util.Map;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/29 0:28
 */
@Configuration
public class RemoteDebugDriver {
    private static final Logger logger = LoggerFactory.getLogger(RemoteDebugDriver.class);
    private static String chromePath;
    public static int port = 0;
    public static WebDriver webDriver;
    @Value("${sonic.chrome.path}")
    private String path;

    @Bean
    public void setChromePath() {
        chromePath = path;
    }

    @Bean
    public Servlet baiduProxyServlet() {
        return new ProxyServlet();
    }

    @Bean
    @DependsOn(value = "startChromeDriver")
    public ServletRegistrationBean proxyServletRegistration() {
        ServletRegistrationBean registrationBean = new ServletRegistrationBean(baiduProxyServlet(), "/agent/*");
        Map<String, String> params = ImmutableMap.of(
                "targetUri", "http://localhost:" + port + "/devtools",
                "log", "false");
        registrationBean.setInitParameters(params);
        return registrationBean;
    }

    @Bean
    @DependsOn(value = "setChromePath")
    public static void startChromeDriver() {
        try {
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
            ChromeOptions chromeOptions = new ChromeOptions();
            System.setProperty("webdriver.chrome.driver", chromePath);
            if (port == 0) {
                int debugPort = PortTool.getPort();
                port = debugPort;
                chromeOptions.addArguments("--remote-debugging-port=" + debugPort);
            } else {
                chromeOptions.addArguments("--remote-debugging-port=" + port);
            }
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
