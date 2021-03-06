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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.appium.java_client.MultiTouchAction;
import io.appium.java_client.Setting;
import io.appium.java_client.TouchAction;
import io.appium.java_client.appmanagement.BaseInstallApplicationOptions;
import io.appium.java_client.appmanagement.BaseTerminateApplicationOptions;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.IOSStartScreenRecordingOptions;
import io.appium.java_client.remote.AutomationName;
import io.appium.java_client.remote.IOSMobileCapabilityType;
import io.appium.java_client.remote.MobileCapabilityType;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.PointOption;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.interfaces.ErrorType;
import org.cloud.sonic.agent.common.interfaces.ResultDetailStatus;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.maps.IOSInfoMap;
import org.cloud.sonic.agent.common.maps.IOSProcessMap;
import org.cloud.sonic.agent.enums.ConditionEnum;
import org.cloud.sonic.agent.enums.SonicEnum;
import org.cloud.sonic.agent.models.FindResult;
import org.cloud.sonic.agent.models.HandleDes;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.StepHandlers;
import org.cloud.sonic.agent.tools.cv.AKAZEFinder;
import org.cloud.sonic.agent.tools.cv.SIFTFinder;
import org.cloud.sonic.agent.tools.cv.SimilarityChecker;
import org.cloud.sonic.agent.tools.cv.TemMatcher;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.cloud.sonic.agent.tools.SpringTool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Platform;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.util.Base64Utils;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

/**
 * @author ZhouYiXun
 * @des iOS??????????????????
 * @date 2021/8/16 20:10
 */
public class IOSStepHandler {
    public LogUtil log = new LogUtil();
    private IOSDriver iosDriver;
    private JSONObject globalParams = new JSONObject();
    private String testPackage = "";
    private String udId = "";
    //????????????
    private int status = ResultDetailStatus.PASS;

    public LogUtil getLog() {
        return log;
    }

    public void setTestMode(int caseId, int resultId, String udId, String type, String sessionId) {
        log.caseId = caseId;
        log.resultId = resultId;
        log.udId = udId;
        log.type = type;
        log.sessionId = sessionId;
    }

    public void setGlobalParams(JSONObject jsonObject) {
        globalParams = jsonObject;
    }

    public void startIOSDriver(String udId, int wdaPort) throws InterruptedException, IOException {
        this.udId = udId;
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(MobileCapabilityType.PLATFORM_NAME, Platform.IOS);
        desiredCapabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, AutomationName.IOS_XCUI_TEST);
        desiredCapabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, 3600);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.COMMAND_TIMEOUTS, 3600);
        desiredCapabilities.setCapability(MobileCapabilityType.NO_RESET, true);
        desiredCapabilities.setCapability(MobileCapabilityType.DEVICE_NAME, SibTool.getName(udId));
        desiredCapabilities.setCapability(MobileCapabilityType.UDID, udId);
        desiredCapabilities.setCapability("wdaConnectionTimeout", 60000);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.WEB_DRIVER_AGENT_URL, "http://127.0.0.1:" + wdaPort);
        desiredCapabilities.setCapability("useXctestrunFile", false);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.SHOW_IOS_LOG, false);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.SHOW_XCODE_LOG, false);
        desiredCapabilities.setCapability("skipLogCapture", true);
        desiredCapabilities.setCapability(IOSMobileCapabilityType.USE_PREBUILT_WDA, false);
        try {
            AppiumServer.start(udId);
            iosDriver = new IOSDriver(AppiumServer.serviceMap.get(udId).getUrl(), desiredCapabilities);
            iosDriver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
            iosDriver.setSetting(Setting.MJPEG_SERVER_FRAMERATE, 50);
            iosDriver.setSetting(Setting.MJPEG_SCALING_FACTOR, 50);
            iosDriver.setSetting(Setting.MJPEG_SERVER_SCREENSHOT_QUALITY, 10);
            iosDriver.setSetting("snapshotMaxDepth", 30);
            log.sendStepLog(StepType.PASS, "????????????????????????", "");
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "???????????????????????????", "");
            //?????????????????????
            setResultDetailStatus(ResultDetailStatus.FAIL);
            throw e;
        }
        int width = iosDriver.manage().window().getSize().width;
        int height = iosDriver.manage().window().getSize().height;
        IOSInfoMap.getSizeMap().put(udId, width + "x" + height);
    }

    public void closeIOSDriver() {
        try {
            if (iosDriver != null) {
                iosDriver.quit();
                log.sendStepLog(StepType.PASS, "??????????????????", "");
                if (IOSProcessMap.getMap().get(udId) != null) {
                    List<Process> processList = IOSProcessMap.getMap().get(udId);
                    for (Process p : processList) {
                        if (p != null) {
                            p.children().forEach(ProcessHandle::destroy);
                            p.destroy();
                        }
                    }
                    IOSProcessMap.getMap().remove(udId);
                }
            }
        } catch (Exception e) {
            log.sendStepLog(StepType.WARN, "????????????????????????????????????????????????", "");
            //????????????
            setResultDetailStatus(ResultDetailStatus.WARN);
            e.printStackTrace();
        } finally {
            AppiumServer.close(udId);
        }
    }

    public void waitDevice(int waitCount) {
        log.sendStepLog(StepType.INFO, "???????????????????????????" + waitCount + "???????????????...", "");
    }

    public void waitDeviceTimeOut() {
        log.sendStepLog(StepType.ERROR, "????????????????????????????????????", "");
        //?????????????????????
        setResultDetailStatus(ResultDetailStatus.WARN);
    }

    public IOSDriver getDriver() {
        return iosDriver;
    }

    public void setResultDetailStatus(int status) {
        if (status > this.status) {
            this.status = status;
        }
    }

    public void sendStatus() {
        log.sendStatusLog(status);
    }

    //??????????????????
    public int getStatus() {
        return status;
    }

    //????????????????????????
    public void resetResultDetailStatus() {
        status = 1;
    }

    public boolean getBattery() {
        double battery = iosDriver.getBatteryInfo().getLevel();
        if (battery <= 0.1) {
            log.sendStepLog(StepType.ERROR, "??????????????????!", "??????????????????...");
            return true;
        } else {
            return false;
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

    public void startRecord() {
        try {
            IOSStartScreenRecordingOptions recordOption = new IOSStartScreenRecordingOptions();
            recordOption.withTimeLimit(Duration.ofMinutes(30));
            recordOption.withVideoQuality(IOSStartScreenRecordingOptions.VideoQuality.LOW);
            recordOption.enableForcedRestart();
            recordOption.withFps(20);
            recordOption.withVideoType("h264");
            iosDriver.startRecordingScreen(recordOption);
        } catch (Exception e) {
            log.sendRecordLog(false, "", "");
        }
    }

    public void stopRecord() {
        File recordDir = new File("./test-output/record");
        if (!recordDir.exists()) {//??????????????????????????????
            recordDir.mkdirs();
        }
        long timeMillis = Calendar.getInstance().getTimeInMillis();
        String fileName = timeMillis + "_" + udId.substring(0, 4) + ".mp4";
        File uploadFile = new File(recordDir + File.separator + fileName);
        try {
            synchronized (IOSStepHandler.class) {
                FileOutputStream fileOutputStream = new FileOutputStream(uploadFile);
                byte[] bytes = Base64Utils.decodeFromString((iosDriver.stopRecordingScreen()));
                fileOutputStream.write(bytes);
                fileOutputStream.close();
            }
            log.sendRecordLog(true, fileName, UploadTools.uploadPatchRecord(uploadFile));
        } catch (Exception e) {
            log.sendRecordLog(false, fileName, "");
        }
    }

    public void install(HandleDes handleDes, String path) {
        handleDes.setStepDes("????????????");
        path = TextHandler.replaceTrans(path, globalParams);
        handleDes.setDetail("App??????????????? " + path);
        try {
            iosDriver.installApp(path, new BaseInstallApplicationOptions() {
                @Override
                public Map<String, Object> build() {
                    Map<String, Object> map = new HashMap<>();
                    map.put("timeout", 180000);
                    return map;
                }
            });
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void uninstall(HandleDes handleDes, String appPackage) {
        handleDes.setStepDes("????????????");
        appPackage = TextHandler.replaceTrans(appPackage, globalParams);
        handleDes.setDetail("App????????? " + appPackage);
        try {
            iosDriver.removeApp(appPackage);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void terminate(HandleDes handleDes, String packageName) {
        handleDes.setStepDes("????????????");
        packageName = TextHandler.replaceTrans(packageName, globalParams);
        handleDes.setDetail("??????????????? " + packageName);
        try {
            iosDriver.terminateApp(packageName, new BaseTerminateApplicationOptions() {
                @Override
                public Map<String, Object> build() {
                    Map<String, Object> map = new HashMap<>();
                    map.put("timeout", 2000);
                    return map;
                }
            });
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void runBackground(HandleDes handleDes, long time) {
        handleDes.setStepDes("??????????????????");
        handleDes.setDetail("????????????App " + time + " ms");
        try {
            iosDriver.runAppInBackground(Duration.ofMillis(time));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void openApp(HandleDes handleDes, String appPackage) {
        handleDes.setStepDes("????????????");
        appPackage = TextHandler.replaceTrans(appPackage, globalParams);
        handleDes.setDetail("App????????? " + appPackage);
        try {
            testPackage = appPackage;
            iosDriver.activateApp(appPackage);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void lock(HandleDes handleDes) {
        handleDes.setStepDes("????????????");
        handleDes.setDetail("");
        try {
            iosDriver.lockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void unLock(HandleDes handleDes) {
        handleDes.setStepDes("????????????");
        handleDes.setDetail("");
        try {
            iosDriver.unlockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void asserts(HandleDes handleDes, String actual, String expect, String type) {
        handleDes.setDetail("???????????? " + actual + " ???????????? " + expect);
        handleDes.setStepDes("");
        try {
            switch (type) {
                case "assertEquals":
                    handleDes.setStepDes("????????????(??????)");
                    assertEquals(actual, expect);
                    break;
                case "assertTrue":
                    handleDes.setStepDes("????????????(??????)");
                    assertTrue(actual.contains(expect));
                    break;
                case "assertNotTrue":
                    handleDes.setStepDes("????????????(?????????)");
                    assertFalse(actual.contains(expect));
                    break;
            }
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public String getText(HandleDes handleDes, String des, String selector, String pathValue) {
        String s = "";
        handleDes.setStepDes("??????" + des + "??????");
        handleDes.setDetail("??????" + selector + ":" + pathValue + "??????");
        try {
            s = findEle(selector, pathValue).getText();
            log.sendStepLog(StepType.INFO, "", "??????????????????: " + s);
        } catch (Exception e) {
            handleDes.setE(e);
        }
        return s;
    }

    public void hideKey(HandleDes handleDes) {
        handleDes.setStepDes("????????????");
        handleDes.setDetail("??????????????????");
        try {
            iosDriver.hideKeyboard();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void click(HandleDes handleDes, String des, String selector, String pathValue) {
        handleDes.setStepDes("??????" + des);
        handleDes.setDetail("??????" + selector + ": " + pathValue);
        try {
            findEle(selector, pathValue).click();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void sendKeys(HandleDes handleDes, String des, String selector, String pathValue, String keys) {
        keys = TextHandler.replaceTrans(keys, globalParams);
        handleDes.setStepDes("???" + des + "????????????");
        handleDes.setDetail("???" + selector + ": " + pathValue + " ??????: " + keys);
        try {
            findEle(selector, pathValue).sendKeys(keys);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void getTextAndAssert(HandleDes handleDes, String des, String selector, String pathValue, String expect) {
        handleDes.setStepDes("??????" + des + "??????");
        handleDes.setDetail("??????" + selector + ":" + pathValue + "??????");
        try {
            String s = findEle(selector, pathValue).getText();
            log.sendStepLog(StepType.INFO, "", "??????????????????: " + s);
            try {
                expect = TextHandler.replaceTrans(expect, globalParams);
                assertEquals(s, expect);
                log.sendStepLog(StepType.INFO, "????????????", "???????????? " + s + " ???????????? " + expect);
            } catch (AssertionError e) {
                log.sendStepLog(StepType.ERROR, "??????" + des + "???????????????", "");
                handleDes.setE(e);
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void longPressPoint(HandleDes handleDes, String des, String xy, int time) {
        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
        handleDes.setStepDes("??????" + des);
        handleDes.setDetail("????????????" + time + "?????? (" + x + "," + y + ")");
        try {
            TouchAction ta = new TouchAction(iosDriver);
            ta.longPress(PointOption.point(x, y)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(time))).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void keyCode(HandleDes handleDes, String key) {
        handleDes.setStepDes("???????????????" + key + "???");
        handleDes.setDetail("");
        try {
            iosDriver.executeScript("mobile:pressButton", JSON.parse("{name: \"" + key + "\"}"));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void multiAction(HandleDes handleDes, String des1, String xy1, String des2, String xy2, String des3, String xy3, String des4, String xy4) {
        int x1 = Integer.parseInt(xy1.substring(0, xy1.indexOf(",")));
        int y1 = Integer.parseInt(xy1.substring(xy1.indexOf(",") + 1));
        int x2 = Integer.parseInt(xy2.substring(0, xy2.indexOf(",")));
        int y2 = Integer.parseInt(xy2.substring(xy2.indexOf(",") + 1));
        int x3 = Integer.parseInt(xy3.substring(0, xy3.indexOf(",")));
        int y3 = Integer.parseInt(xy3.substring(xy3.indexOf(",") + 1));
        int x4 = Integer.parseInt(xy4.substring(0, xy4.indexOf(",")));
        int y4 = Integer.parseInt(xy4.substring(xy4.indexOf(",") + 1));
        String detail = "??????" + des1 + "( " + x1 + ", " + y1 + " )???????????????" + des2 + "( " + x2 + ", " + y2 + " ),????????????" + des3 + "( " + x3 + ", " + y3 + " )???????????????" + des4 + "( " + x4 + ", " + y4 + " )";
        handleDes.setStepDes("????????????");
        handleDes.setDetail(detail);
        try {
            TouchAction hand1 = new TouchAction(iosDriver);
            TouchAction hand2 = new TouchAction(iosDriver);
            MultiTouchAction multiTouchAction = new MultiTouchAction(iosDriver);
            hand1.press(PointOption.point(x1, y1)).moveTo(PointOption.point(x2, y2)).release();
            hand2.press(PointOption.point(x3, y3)).moveTo(PointOption.point(x4, y4)).release();
            multiTouchAction.add(hand1);
            multiTouchAction.add(hand2);
            multiTouchAction.perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void tap(HandleDes handleDes, String des, String xy) {
        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
        handleDes.setStepDes("??????" + des);
        handleDes.setDetail("????????????(" + x + "," + y + ")");
        try {
            TouchAction ta = new TouchAction(iosDriver);
            ta.tap(PointOption.point(x, y)).perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void swipePoint(HandleDes handleDes, String des1, String xy1, String des2, String xy2) {
        int x1 = Integer.parseInt(xy1.substring(0, xy1.indexOf(",")));
        int y1 = Integer.parseInt(xy1.substring(xy1.indexOf(",") + 1));
        int x2 = Integer.parseInt(xy2.substring(0, xy2.indexOf(",")));
        int y2 = Integer.parseInt(xy2.substring(xy2.indexOf(",") + 1));
        handleDes.setStepDes("????????????" + des1 + "???" + des2);
        handleDes.setDetail("????????????(" + x1 + "," + y1 + ")???(" + x2 + "," + y2 + ")");
        try {
            TouchAction ta = new TouchAction(iosDriver);
            ta.press(PointOption.point(x1, y1)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(300))).moveTo(PointOption.point(x2, y2)).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void swipe(HandleDes handleDes, String des, String selector, String pathValue, String des2, String selector2, String pathValue2) {
        WebElement webElement = findEle(selector, pathValue);
        WebElement webElement2 = findEle(selector2, pathValue2);
        int x1 = webElement.getLocation().getX();
        int y1 = webElement.getLocation().getY();
        int x2 = webElement2.getLocation().getX();
        int y2 = webElement2.getLocation().getY();
        handleDes.setStepDes("????????????" + des + "???" + des2);
        handleDes.setDetail("????????????(" + x1 + "," + y1 + ")???(" + x2 + "," + y2 + ")");
        try {
            TouchAction ta = new TouchAction(iosDriver);
            ta.press(PointOption.point(x1, y1)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(300))).moveTo(PointOption.point(x2, y2)).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void longPress(HandleDes handleDes, String des, String selector, String pathValue, int time) {
        handleDes.setStepDes("??????" + des);
        handleDes.setDetail("??????????????????" + time + "?????? ");
        try {
            TouchAction ta = new TouchAction(iosDriver);
            WebElement webElement = findEle(selector, pathValue);
            int x = webElement.getLocation().getX();
            int y = webElement.getLocation().getY();
            Duration duration = Duration.ofMillis(time);
            ta.longPress(PointOption.point(x, y)).waitAction(WaitOptions.waitOptions(duration)).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void clear(HandleDes handleDes, String des, String selector, String pathValue) {
        handleDes.setStepDes("??????" + des);
        handleDes.setDetail("??????" + selector + ": " + pathValue);
        try {
            findEle(selector, pathValue).clear();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void isExistEle(HandleDes handleDes, String des, String selector, String pathValue, boolean expect) {
        handleDes.setStepDes("???????????? " + des + " ????????????");
        handleDes.setDetail("????????????" + (expect ? "??????" : "?????????"));
        boolean hasEle = false;
        try {
            WebElement w = findEle(selector, pathValue);
            if (w != null) {
                hasEle = true;
            }
        } catch (Exception e) {
        }
        try {
            assertEquals(hasEle, expect);
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public void getTitle(HandleDes handleDes, String expect) {
        String title = iosDriver.getTitle();
        handleDes.setStepDes("??????????????????");
        handleDes.setDetail("?????????" + title + "???????????????" + expect);
        try {
            assertEquals(title, expect);
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public void clickByImg(HandleDes handleDes, String des, String pathValue) throws Exception {
        handleDes.setStepDes("????????????" + des);
        handleDes.setDetail(pathValue);
        File file = null;
        if (pathValue.startsWith("http")) {
            try {
                file = DownloadTool.download(pathValue);
            } catch (Exception e) {
                handleDes.setE(e);
                return;
            }
        }
        FindResult findResult = null;
        try {
            SIFTFinder siftFinder = new SIFTFinder();
            findResult = siftFinder.getSIFTFindResult(file, getScreenToLocal());
        } catch (Exception e) {
            log.sendStepLog(StepType.WARN, "SIFT????????????????????????????????????...",
                    "");
        }
        if (findResult != null) {
            log.sendStepLog(StepType.INFO, "????????????????????????(" + findResult.getX() + "," + findResult.getY() + ")  ?????????" + findResult.getTime() + " ms",
                    findResult.getUrl());
        } else {
            log.sendStepLog(StepType.INFO, "SIFT?????????????????????????????????AKAZE?????????...",
                    "");
            try {
                AKAZEFinder akazeFinder = new AKAZEFinder();
                findResult = akazeFinder.getAKAZEFindResult(file, getScreenToLocal());
            } catch (Exception e) {
                log.sendStepLog(StepType.WARN, "AKAZE????????????????????????????????????????????????...",
                        "");
            }
            if (findResult != null) {
                log.sendStepLog(StepType.INFO, "????????????????????????(" + findResult.getX() + "," + findResult.getY() + ")  ?????????" + findResult.getTime() + " ms",
                        findResult.getUrl());
            } else {
                log.sendStepLog(StepType.INFO, "AKAZE??????????????????????????????????????????????????????...",
                        "");
                try {
                    TemMatcher temMatcher = new TemMatcher();
                    findResult = temMatcher.getTemMatchResult(file, getScreenToLocal());
                } catch (Exception e) {
                    log.sendStepLog(StepType.WARN, "????????????????????????",
                            "");
                }
                if (findResult != null) {
                    log.sendStepLog(StepType.INFO, "????????????????????????(" + findResult.getX() + "," + findResult.getY() + ")  ?????????" + findResult.getTime() + " ms",
                            findResult.getUrl());
                } else {
                    handleDes.setE(new Exception("?????????????????????"));
                }
            }
        }
        if (findResult != null) {
            try {
                TouchAction ta = new TouchAction(iosDriver);
                ta.tap(PointOption.point(findResult.getX(), findResult.getY())).perform();
            } catch (Exception e) {
                log.sendStepLog(StepType.ERROR, "??????" + des + "?????????", "");
                handleDes.setE(e);
            }
        }
    }


    public void readText(HandleDes handleDes, String language, String text) throws Exception {
//        TextReader textReader = new TextReader();
//        String result = textReader.getTessResult(getScreenToLocal(), language);
//        log.sendStepLog(StepType.INFO, "",
//                "???????????????????????????<br>" + result);
//        String filter = result.replaceAll(" ", "");
        handleDes.setStepDes("??????????????????");
        handleDes.setDetail("????????????????????????????????????????????????" + text);
//        if (!filter.contains(text)) {
//            handleDes.setE(new Exception("??????????????????????????????"));
//        }
    }

    public File getScreenToLocal() {
        File file = ((TakesScreenshot) iosDriver).getScreenshotAs(OutputType.FILE);
        File resultFile = new File("test-output/" + log.udId + Calendar.getInstance().getTimeInMillis() + ".jpg");
        try {
            FileCopyUtils.copy(file, resultFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultFile;
    }

    public void checkImage(HandleDes handleDes, String des, String pathValue, double matchThreshold) throws Exception {
        log.sendStepLog(StepType.INFO, "????????????" + des + "??????", "?????????????????????????????????????????????????????????" + matchThreshold + "%");
        File file = null;
        if (pathValue.startsWith("http")) {
            file = DownloadTool.download(pathValue);
        }
        double score = SimilarityChecker.getSimilarMSSIMScore(file, getScreenToLocal(), true);
        handleDes.setStepDes("??????" + des + "???????????????");
        handleDes.setDetail("????????????" + score * 100 + "%");
        if (score == 0) {
            handleDes.setE(new Exception("??????????????????????????????????????????????????????????????????"));
        } else if (score < (matchThreshold / 100)) {
            handleDes.setE(new Exception("?????????????????????????????????expect " + matchThreshold + " but " + score * 100));
        }
    }

    public void siriCommand(HandleDes handleDes, String command) {
        handleDes.setStepDes("siri??????");
        handleDes.setDetail("???siri??????????????? " + command);
        try {
            iosDriver.executeScript("mobile:siriCommand", JSON.parse("{text: \"" + command + "\"}"));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void exceptionLog(Throwable e) {
        log.sendStepLog(StepType.WARN, "", "??????????????? " + e.fillInStackTrace().toString());
    }

    public void errorScreen() {
        try {
            iosDriver.context("NATIVE_APP");//????????????app
            log.sendStepLog(StepType.WARN, "??????????????????", UploadTools
                    .upload(((TakesScreenshot) iosDriver).getScreenshotAs(OutputType.FILE), "imageFiles"));
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "??????????????????", "");
        }
    }

    public String stepScreen(HandleDes handleDes) {
        handleDes.setStepDes("????????????");
        handleDes.setDetail("");
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

    public void pause(HandleDes handleDes, int time) {
        handleDes.setStepDes("????????????");
        handleDes.setDetail("??????" + time + " ms");
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            handleDes.setE(e);
        }
    }

    public void publicStep(HandleDes handleDes, String name, JSONArray stepArray) {
        handleDes.setStepDes("?????????????????? " + name);
        handleDes.setDetail("");
        log.sendStepLog(StepType.WARN, "???????????????" + name + "???????????????", "");
        for (Object publicStep : stepArray) {
            JSONObject stepDetail = (JSONObject) publicStep;
            try {
                SpringTool.getBean(StepHandlers.class)
                        .runStep(stepDetail, handleDes, (RunStepThread) Thread.currentThread());
            } catch (Throwable e) {
                handleDes.setE(e);
                break;
            }
        }
        log.sendStepLog(StepType.WARN, "???????????????" + name + "???????????????", "");
    }

    public WebElement findEle(String selector, String pathValue) {
        WebElement we = null;
        pathValue = TextHandler.replaceTrans(pathValue, globalParams);
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
                log.sendStepLog(StepType.ERROR, "????????????????????????", "????????????????????????: " + selector + " ?????????!!!");
                break;
        }
        return we;
    }

    public void stepHold(HandleDes handleDes, int time) {
        handleDes.setStepDes("????????????????????????");
        handleDes.setDetail("??????" + time + " ms");
        holdTime = time;
    }

    private int holdTime = 0;

    public void runStep(JSONObject stepJSON, HandleDes handleDes) throws Throwable {
        JSONObject step = stepJSON.getJSONObject("step");
        JSONArray eleList = step.getJSONArray("elements");
        Thread.sleep(holdTime);
        switch (step.getString("stepType")) {
            case "stepHold":
                stepHold(handleDes, Integer.parseInt(step.getString("content")));
                break;
            case "siriCommand":
                siriCommand(handleDes, step.getString("content"));
                break;
            case "readText":
                readText(handleDes, step.getString("content"), step.getString("text"));
                break;
            case "clickByImg":
                clickByImg(handleDes, eleList.getJSONObject(0).getString("eleName")
                        , eleList.getJSONObject(0).getString("eleValue"));
                break;
            case "click":
                click(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"));
                break;
            case "getTitle":
                getTitle(handleDes, step.getString("content"));
                break;
            case "sendKeys":
                sendKeys(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
                break;
            case "getText":
                getTextAndAssert(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
                break;
            case "isExistEle":
                isExistEle(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getBoolean("content"));
                break;
            case "clear":
                clear(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"));
                break;
            case "longPress":
                longPress(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), Integer.parseInt(step.getString("content")));
                break;
            case "swipe":
                swipePoint(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                        , eleList.getJSONObject(1).getString("eleName"), eleList.getJSONObject(1).getString("eleValue"));
                break;
            case "swipe2":
                swipe(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue")
                        , eleList.getJSONObject(1).getString("eleName"), eleList.getJSONObject(1).getString("eleType"), eleList.getJSONObject(1).getString("eleValue"));
                break;
            case "tap":
                tap(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue"));
                break;
            case "longPressPoint":
                longPressPoint(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                        , Integer.parseInt(step.getString("content")));
                break;
            case "pause":
                pause(handleDes, Integer.parseInt(step.getString("content")));
                break;
            case "checkImage":
                checkImage(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                        , step.getDouble("content"));
                break;
            case "stepScreen":
                stepScreen(handleDes);
                break;
            case "openApp":
                openApp(handleDes, step.getString("text"));
                break;
            case "terminate":
                terminate(handleDes, step.getString("text"));
                break;
            case "install":
                install(handleDes, step.getString("text"));
                break;
            case "uninstall":
                uninstall(handleDes, step.getString("text"));
                break;
            case "runBack":
                runBackground(handleDes, Long.parseLong(step.getString("content")));
                break;
            case "lock":
                lock(handleDes);
                break;
            case "unLock":
                unLock(handleDes);
                break;
            case "zoom":
                multiAction(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                        , eleList.getJSONObject(1).getString("eleName"), eleList.getJSONObject(1).getString("eleValue")
                        , eleList.getJSONObject(2).getString("eleName"), eleList.getJSONObject(2).getString("eleValue")
                        , eleList.getJSONObject(3).getString("eleName"), eleList.getJSONObject(3).getString("eleValue"));
                break;
            case "keyCode":
                keyCode(handleDes, step.getString("content"));
                break;
            case "assertEquals":
            case "assertTrue":
            case "assertNotTrue":
                String actual = TextHandler.replaceTrans(step.getString("text"), globalParams);
                String expect = TextHandler.replaceTrans(step.getString("content"), globalParams);
                asserts(handleDes, actual, expect, step.getString("stepType"));
                break;
            case "getTextValue":
                globalParams.put(step.getString("content"), getText(handleDes, eleList.getJSONObject(0).getString("eleName")
                        , eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue")));
                break;
            case "hideKey":
                hideKey(handleDes);
                break;
//            case "monkey":
//                runMonkey(handleDes, step.getJSONObject("content"), step.getJSONArray("text").toJavaList(JSONObject.class));
//                break;
            case "publicStep":
                publicStep(handleDes, step.getString("content"), stepJSON.getJSONArray("pubSteps"));
                return;
        }
        switchType(step, handleDes);
    }

    public void switchType(JSONObject stepJson, HandleDes handleDes) throws Throwable {
        Integer error = stepJson.getInteger("error");
        String stepDes = handleDes.getStepDes();
        String detail = handleDes.getDetail();
        Throwable e = handleDes.getE();
        if (e != null) {
            switch (error) {
                case ErrorType.IGNORE:
                    if (stepJson.getInteger("conditionType").equals(ConditionEnum.NONE.getValue())) {
                        log.sendStepLog(StepType.PASS, stepDes + "??????????????????...", detail);
                    } else {
                        ConditionEnum conditionType =
                                SonicEnum.valueToEnum(ConditionEnum.class, stepJson.getInteger("conditionType"));
                        String des = "???%s????????????%s?????????".formatted(conditionType.getName(), stepDes);
                        log.sendStepLog(StepType.ERROR, des, detail);
                        exceptionLog(e);
                    }
                    break;
                case ErrorType.WARNING:
                    log.sendStepLog(StepType.WARN, stepDes + "?????????", detail);
                    setResultDetailStatus(ResultDetailStatus.WARN);
                    errorScreen();
                    exceptionLog(e);
                    break;
                case ErrorType.SHUTDOWN:
                    log.sendStepLog(StepType.ERROR, stepDes + "?????????", detail);
                    setResultDetailStatus(ResultDetailStatus.FAIL);
                    errorScreen();
                    exceptionLog(e);
                    throw e;
            }
            // ?????????????????????????????????
            if (stepJson.getInteger("conditionType").equals(ConditionEnum.NONE.getValue())) {
                handleDes.clear();
            }
        } else {
            log.sendStepLog(StepType.PASS, stepDes, detail);
        }
    }
}