package com.sonic.agent.automation;

import com.sonic.agent.common.LogTool;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.AndroidMobileCapabilityType;
import io.appium.java_client.remote.AutomationName;
import io.appium.java_client.remote.MobileCapabilityType;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ZhouYiXun
 * @des 安卓自动化处理类
 * @date 2021/8/16 20:10
 */
public class AndroidStepHandler {
    LogTool log = new LogTool();
    private AndroidDriver androidDriver;
    private AppiumDriverLocalService appiumDriverLocalService;

    public void startAppiumServer() {
        appiumDriverLocalService = AppiumDriverLocalService.buildService(new AppiumServiceBuilder().usingAnyFreePort()
                .withArgument(GeneralServerFlag.LOG_LEVEL, "error")
                .withArgument(GeneralServerFlag.ALLOW_INSECURE, "chromedriver_autodownload")
                .withArgument(GeneralServerFlag.SESSION_OVERRIDE));
        appiumDriverLocalService.start();
    }

    /**
     * @param udId
     * @return void
     * @author ZhouYiXun
     * @des 启动安卓驱动，连接设备
     * @date 2021/8/16 20:01
     */
    public void startAndroidDriver(String udId) throws InterruptedException {
        startAppiumServer();
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        //微信webView配置
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.setExperimentalOption("androidProcess", "com.tencent.mm:tools");
        desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
        //webView通用配置，自动下载匹配的driver
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.RECREATE_CHROME_DRIVER_SESSIONS, true);
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.CHROMEDRIVER_EXECUTABLE_DIR, "chromeDriver");
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.CHROMEDRIVER_CHROME_MAPPING_FILE, "chromeDriver/version.json");
        //平台
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.PLATFORM_NAME, Platform.ANDROID);
        //选用的自动化框架
        desiredCapabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, AutomationName.ANDROID_UIAUTOMATOR2);
        //关闭运行时阻塞其他Accessibility服务，开启的话其他不能使用了
        desiredCapabilities.setCapability("disableSuppressAccessibilityService", true);
        //adb指令超时时间
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.ADB_EXEC_TIMEOUT, 60000);
        //UIA2安装超时时间
        desiredCapabilities.setCapability("uiautomator2ServerInstallTimeout", 30000);
        //等待新命令超时时间
        desiredCapabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, 3600);
        //不重置应用
        desiredCapabilities.setCapability(MobileCapabilityType.NO_RESET, true);
        //单独唤起应用的话，这个需要设置空字符串
        desiredCapabilities.setCapability(MobileCapabilityType.BROWSER_NAME, "");
        //指定设备序列号
        desiredCapabilities.setCapability(MobileCapabilityType.UDID, udId);
        //过滤logcat，注意！请将MyCrashTag替换为自己安卓崩溃时的tag，需要跟开发配合约定，有多个的话可以add()多几个
        List<String> logcatFilter = new ArrayList<>();
        logcatFilter.add("MyCrashTag:W");
        logcatFilter.add("*:S");
        desiredCapabilities.setCapability("logcatFilterSpecs", logcatFilter);
        try {
            androidDriver = new AndroidDriver(appiumDriverLocalService.getUrl(), desiredCapabilities);
            log.sendStepLog("stepLog", "pass", "连接设备驱动成功", "");
        } catch (Exception e) {
            log.sendStepLog("stepLog", "warning", "连接设备驱动失败！", "");
            if (appiumDriverLocalService.isRunning()) {
                appiumDriverLocalService.stop();
            }
        }
        Capabilities capabilities = androidDriver.getCapabilities();
        Thread.sleep(100);
        log.androidInfo("Android", capabilities.getCapability("platformVersion").toString(),
                udId, capabilities.getCapability("deviceManufacturer").toString(),
                capabilities.getCapability("deviceModel").toString(),
                capabilities.getCapability("deviceApiLevel").toString(),
                capabilities.getCapability("deviceScreenSize").toString());
    }
}
