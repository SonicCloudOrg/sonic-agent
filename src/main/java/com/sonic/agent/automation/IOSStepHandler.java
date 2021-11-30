package com.sonic.agent.automation;

import com.sonic.agent.bridge.ios.TIDeviceTool;
import com.sonic.agent.interfaces.ResultDetailStatus;
import com.sonic.agent.interfaces.StepType;
import com.sonic.agent.maps.IOSProcessMap;
import com.sonic.agent.tools.LogTool;
import io.appium.java_client.android.appmanagement.AndroidTerminateApplicationOptions;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.remote.AutomationName;
import io.appium.java_client.remote.IOSMobileCapabilityType;
import io.appium.java_client.remote.MobileCapabilityType;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IOSStepHandler {
    public LogTool log = new LogTool();
    private IOSDriver iosDriver;
    private String testPackage = "";
    private String udId = "";
    //测试状态
    private int status = 1;

    public void startIOSDriver(String udId, String name) throws InterruptedException, IOException {
        this.udId = udId;
        int wdaPort = TIDeviceTool.startWda(udId);
        int imgPort = TIDeviceTool.relayImg(udId);
        Thread.sleep(2000);
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(MobileCapabilityType.PLATFORM_NAME, Platform.IOS);
        desiredCapabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, AutomationName.IOS_XCUI_TEST);
        desiredCapabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, 3600);
        desiredCapabilities.setCapability(MobileCapabilityType.NO_RESET, true);
        desiredCapabilities.setCapability(MobileCapabilityType.DEVICE_NAME, name);
        desiredCapabilities.setCapability(MobileCapabilityType.UDID, udId);
        desiredCapabilities.setCapability("wdaConnectionTimeout", 60000);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.WEB_DRIVER_AGENT_URL, "http://127.0.0.1:" + wdaPort);
        desiredCapabilities.setCapability("useXctestrunFile", false);
        desiredCapabilities.setCapability("mjpegServerPort", imgPort);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.USE_PREBUILT_WDA, true);
        try {
            iosDriver = new IOSDriver(AppiumServer.service.getUrl(), desiredCapabilities);
            iosDriver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
            log.sendStepLog(StepType.PASS, "连接设备驱动成功", "");
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "连接设备驱动失败！", "");
            //测试标记为失败
            setResultDetailStatus(ResultDetailStatus.FAIL);
            throw e;
        }
    }

    public void closeIOSDriver() {
        try {
            if (iosDriver != null) {
                //终止测试包
                if (!testPackage.equals("")) {
                    try {
                        iosDriver.terminateApp(testPackage, new AndroidTerminateApplicationOptions().withTimeout(Duration.ofMillis(1000)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                iosDriver.quit();
                log.sendStepLog(StepType.PASS, "退出连接设备", "");
                if (IOSProcessMap.getMap().get(udId) != null) {
                    List<Process> processList = IOSProcessMap.getMap().get(udId);
                    for (Process p : processList) {
                        if (p != null) {
                            p.destroy();
                        }
                    }
                    IOSProcessMap.getMap().remove(udId);
                }
            }
        } catch (Exception e) {
            log.sendStepLog(StepType.WARN, "测试终止异常！请检查设备连接状态", "");
            //测试异常
            setResultDetailStatus(ResultDetailStatus.WARN);
            e.printStackTrace();
        }
    }

    public void setResultDetailStatus(int status) {
        if (status > this.status) {
            this.status = status;
        }
    }
}
