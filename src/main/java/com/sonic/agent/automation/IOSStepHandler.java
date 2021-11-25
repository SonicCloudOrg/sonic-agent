package com.sonic.agent.automation;

import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.remote.AutomationName;
import io.appium.java_client.remote.IOSMobileCapabilityType;
import io.appium.java_client.remote.MobileCapabilityType;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.IOException;

public class IOSStepHandler {
    public void startIOSDriver(String udId,String bundleId,String name) throws InterruptedException, IOException {
        Process process = Runtime.getRuntime().exec("tidevice -u " + udId +
                " wdaproxy" + " -B " + bundleId +
                " --port " + 8888);
        System.out.println("wda:" + 8888);
        Thread.sleep(2000);
        System.out.println("tidevice -u " + udId +
                " relay " + 8889 + " " + 9100);
        Process process2 = Runtime.getRuntime().exec("tidevice -u " + udId +
                " relay " + 8889 + " " + 9100);
        System.out.println("relay:" + 8889);
        Thread.sleep(2000);
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(MobileCapabilityType.PLATFORM_NAME, Platform.IOS);
        desiredCapabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, AutomationName.IOS_XCUI_TEST);
        desiredCapabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, 3600);
        desiredCapabilities.setCapability(MobileCapabilityType.NO_RESET, true);
        desiredCapabilities.setCapability(MobileCapabilityType.DEVICE_NAME, name);
        desiredCapabilities.setCapability(MobileCapabilityType.UDID, udId);
        desiredCapabilities.setCapability("wdaConnectionTimeout", 60000);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.WEB_DRIVER_AGENT_URL, "http://127.0.0.1:8888");
        desiredCapabilities.setCapability("useXctestrunFile", false);
        desiredCapabilities.setCapability("mjpegServerPort", 8889);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.USE_PREBUILT_WDA, true);
        try {
            IOSDriver iosDriver = new IOSDriver(AppiumServer.service.getUrl(), desiredCapabilities);
//            logger.info("连接设备成功");
            Thread.sleep(60000);
            iosDriver.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
