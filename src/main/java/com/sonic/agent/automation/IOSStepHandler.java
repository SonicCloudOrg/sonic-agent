package com.sonic.agent.automation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.bridge.ios.TIDeviceTool;
import com.sonic.agent.interfaces.ResultDetailStatus;
import com.sonic.agent.interfaces.StepType;
import com.sonic.agent.maps.IOSProcessMap;
import com.sonic.agent.tools.LogTool;
import com.sonic.agent.tools.UploadTools;
import io.appium.java_client.MobileBy;
import io.appium.java_client.android.appmanagement.AndroidTerminateApplicationOptions;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.remote.AutomationName;
import io.appium.java_client.remote.IOSMobileCapabilityType;
import io.appium.java_client.remote.MobileCapabilityType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Platform;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedConditions;

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

    public void setTestMode(int caseId, int resultId, String udId, String type, String sessionId) {
        log.caseId = caseId;
        log.resultId = resultId;
        log.udId = udId;
        log.type = type;
        log.sessionId = sessionId;
    }

    public int startIOSDriver(String udId) throws InterruptedException, IOException {
        this.udId = udId;
        int wdaPort = TIDeviceTool.startWda(udId);
        int imgPort = TIDeviceTool.relayImg(udId);
        Thread.sleep(2000);
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(MobileCapabilityType.PLATFORM_NAME, Platform.IOS);
        desiredCapabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, AutomationName.IOS_XCUI_TEST);
        desiredCapabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, 3600);
        desiredCapabilities.setCapability(MobileCapabilityType.NO_RESET, true);
        desiredCapabilities.setCapability(MobileCapabilityType.DEVICE_NAME, TIDeviceTool.getName(udId));
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
        return imgPort;
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

    public IOSDriver getDriver() {
        return iosDriver;
    }

    public void setResultDetailStatus(int status) {
        if (status > this.status) {
            this.status = status;
        }
    }

    private int xpathId = 1;

    public JSONArray getResource() {
        JSONArray elementList = new JSONArray();
        Document doc = Jsoup.parse(iosDriver.getPageSource());
        String xpath = "";
        elementList.addAll(getChild(doc.body().children().get(0).children(), xpath));
        xpathId = 1;
        return elementList;
    }

    public JSONArray getChild(org.jsoup.select.Elements elements, String xpath) {
        JSONArray elementList = new JSONArray();
        for (int i = 0; i < elements.size(); i++) {
            JSONObject ele = new JSONObject();
            int tagCount = 0;
            int siblingIndex = 0;
            String indexXpath;
            for (int j = 0; j < elements.size(); j++) {
                if (elements.get(j).attr("type").equals(elements.get(i).attr("type"))) {
                    tagCount++;
                }
                if (i == j) {
                    siblingIndex = tagCount;
                }
            }
            if (tagCount == 1) {
                indexXpath = xpath + "/" + elements.get(i).attr("type");
            } else {
                indexXpath = xpath + "/" + elements.get(i).attr("type") + "[" + siblingIndex + "]";
            }
            ele.put("id", xpathId);
            xpathId++;
            ele.put("label", "<" + elements.get(i).attr("type") + ">");
            JSONObject detail = new JSONObject();
            detail.put("xpath", indexXpath);
            for (Attribute attr : elements.get(i).attributes()) {
                detail.put(attr.getKey(), attr.getValue());
            }
            ele.put("detail", detail);
            if (elements.get(i).children().size() > 0) {
                ele.put("children", getChild(elements.get(i).children(), indexXpath));
            }
            elementList.add(ele);
        }
        return elementList;
    }

    public void openApp(HandleDes handleDes, String appPackage) {
        handleDes.setStepDes("打开应用");
        handleDes.setDetail("App包名： " + appPackage);
        try {
            testPackage = appPackage;
            iosDriver.activateApp(appPackage);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void lock(HandleDes handleDes) {
        handleDes.setStepDes("锁定屏幕");
        try {
            iosDriver.lockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void unLock(HandleDes handleDes) {
        handleDes.setStepDes("解锁屏幕");
        try {
            iosDriver.unlockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void siriCommand(HandleDes handleDes, String command) {
        handleDes.setStepDes("siri指令");
        handleDes.setDetail("对siri发送指令： " + command);
        try {
            iosDriver.executeScript("mobile:siriCommand", JSON.parse("{text: \"" + command + "\"}"));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public String stepScreen(HandleDes handleDes) {
        handleDes.setStepDes("获取截图");
        String url = "";
        try {
            url = UploadTools.upload(((TakesScreenshot) iosDriver)
                    .getScreenshotAs(OutputType.FILE), "imageFiles");
            handleDes.setDetail(url);
        } catch (Exception e) {
            handleDes.setE(e);
        }
        return url;
    }

    public WebElement findEle(String selector, String pathValue) {
        WebElement we = null;
        switch (selector) {
            case "id":
                we = iosDriver.findElementById(pathValue);
                break;
            case "accessibilityId":
                we = iosDriver.findElementByAccessibilityId(pathValue);
                break;
            case "nsPredicate":
                we = iosDriver.findElementByIosNsPredicate(pathValue);
                break;
            case "name":
                we = iosDriver.findElementByName(pathValue);
                break;
            case "xpath":
                we = iosDriver.findElementByXPath(pathValue);
                break;
            case "cssSelector":
                we = iosDriver.findElementByCssSelector(pathValue);
                break;
            case "className":
                we = iosDriver.findElementByClassName(pathValue);
                break;
            case "tagName":
                we = iosDriver.findElementByTagName(pathValue);
                break;
            case "linkText":
                we = iosDriver.findElementByLinkText(pathValue);
                break;
            case "partialLinkText":
                we = iosDriver.findElementByPartialLinkText(pathValue);
                break;
            default:
                log.sendStepLog(StepType.ERROR, "查找控件元素失败", "这个控件元素类型: " + selector + " 不存在!!!");
                break;
        }
        return we;
    }
}
