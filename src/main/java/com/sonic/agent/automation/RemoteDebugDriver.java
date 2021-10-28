package com.sonic.agent.automation;

import com.sonic.agent.tools.PortTool;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/29 0:28
 */
public class RemoteDebugDriver {
    public static int port = 0;
    private static WebDriver webDriver;

    public static void start() {
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        ChromeOptions chromeOptions = new ChromeOptions();
        System.setProperty("webdriver.chrome.driver", "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chromedriver.exe");
        int debugPort = PortTool.getPort();
        chromeOptions.addArguments("--remote-debugging-port=" + debugPort);
        chromeOptions.addArguments("--headless");
        desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
        webDriver = new ChromeDriver(desiredCapabilities);
        port = debugPort;
    }

    public static void close(){
        if(webDriver!=null){
            webDriver.quit();
        }
    }
}
