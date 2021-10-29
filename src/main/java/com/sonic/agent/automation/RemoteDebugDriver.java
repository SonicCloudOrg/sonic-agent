package com.sonic.agent.automation;

import com.google.common.collect.ImmutableMap;
import com.sonic.agent.tools.PortTool;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.servlet.Servlet;
import java.util.Map;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/29 0:28
 */
@Configuration
public class RemoteDebugDriver {
    public static int port = 0;
    public static WebDriver webDriver;

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
                "log", "true");
        registrationBean.setInitParameters(params);
        return registrationBean;
    }

    @Bean
    public static void startChromeDriver() {
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        ChromeOptions chromeOptions = new ChromeOptions();
        System.setProperty("webdriver.chrome.driver", "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chromedriver.exe");
        if (port == 0) {
            int debugPort = PortTool.getPort();
            port = debugPort;
            chromeOptions.addArguments("--remote-debugging-port=" + debugPort);
        } else {
            chromeOptions.addArguments("--remote-debugging-port=" + port);
        }
        chromeOptions.addArguments("--headless");
        desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
        webDriver = new ChromeDriver(desiredCapabilities);
    }

    public static void close() {
        if (webDriver != null) {
            webDriver.quit();
        }
    }
}
