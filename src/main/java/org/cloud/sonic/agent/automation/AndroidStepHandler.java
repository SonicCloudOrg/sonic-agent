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

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import io.appium.java_client.MultiTouchAction;
import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidStartScreenRecordingOptions;
import io.appium.java_client.android.appmanagement.AndroidInstallApplicationOptions;
import io.appium.java_client.android.appmanagement.AndroidTerminateApplicationOptions;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.remote.AndroidMobileCapabilityType;
import io.appium.java_client.remote.AutomationName;
import io.appium.java_client.remote.MobileCapabilityType;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.PointOption;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.cloud.sonic.agent.common.interfaces.ErrorType;
import org.cloud.sonic.agent.common.interfaces.ResultDetailStatus;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.enums.ConditionEnum;
import org.cloud.sonic.agent.enums.SonicEnum;
import org.cloud.sonic.agent.models.FindResult;
import org.cloud.sonic.agent.models.HandleDes;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.StepHandlers;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.SpringTool;
import org.cloud.sonic.agent.tools.cv.AKAZEFinder;
import org.cloud.sonic.agent.tools.cv.SIFTFinder;
import org.cloud.sonic.agent.tools.cv.SimilarityChecker;
import org.cloud.sonic.agent.tools.cv.TemMatcher;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.util.Base64Utils;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

/**
 * @author ZhouYiXun
 * @des ????????????????????????
 * @date 2021/8/16 20:10
 */
public class AndroidStepHandler {
    public LogUtil log = new LogUtil();
    private AndroidDriver androidDriver;
    private JSONObject globalParams = new JSONObject();
    //?????????
//    private String version = "";
    //??????????????????
    private long startTime;
    //???????????????
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

    /**
     * @return
     * @author ZhouYiXun
     * @des new???????????????
     * @date 2021/8/16 20:01
     */
    public AndroidStepHandler() {
        startTime = Calendar.getInstance().getTimeInMillis();
    }

    /**
     * @param udId
     * @return void
     * @author ZhouYiXun
     * @des ?????????????????????????????????
     * @date 2021/8/16 20:01
     */
    public void startAndroidDriver(String udId) throws InterruptedException {
        this.udId = udId;
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        //??????webView??????
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.setExperimentalOption("androidProcess", "com.tencent.mm:tools");
        desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
        //webView????????????????????????????????????driver
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.RECREATE_CHROME_DRIVER_SESSIONS, true);
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.CHROMEDRIVER_EXECUTABLE_DIR, "webview");
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.CHROMEDRIVER_CHROME_MAPPING_FILE, "webview/version.json");
        //??????
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.PLATFORM_NAME, Platform.ANDROID);
        //????????????????????????
        desiredCapabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, AutomationName.ANDROID_UIAUTOMATOR2);
        //???????????????????????????Accessibility??????????????????????????????????????????
        desiredCapabilities.setCapability("disableSuppressAccessibilityService", true);
        //adb??????????????????
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.ADB_EXEC_TIMEOUT, 7200000);
        //UIA2??????????????????
        desiredCapabilities.setCapability("uiautomator2ServerInstallTimeout", 600000);

        //io.appium.uiautomator2.server io.appium.uiautomator2.server.test //io.appium.settings
//        desiredCapabilities.setCapability("skipServerInstallation",true);
//        desiredCapabilities.setCapability("disableWindowAnimation",true);
//        desiredCapabilities.setCapability("skipDeviceInitialization",true);
        //???????????????????????????
        desiredCapabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, 7200);
        //???????????????
        desiredCapabilities.setCapability(MobileCapabilityType.NO_RESET, true);
        //?????????????????????????????????????????????????????????
        desiredCapabilities.setCapability(MobileCapabilityType.BROWSER_NAME, "");
        //?????????????????????
        desiredCapabilities.setCapability(MobileCapabilityType.UDID, udId);
        //??????systemPort
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.SYSTEM_PORT, PortTool.getPort());
        desiredCapabilities.setCapability("skipLogcatCapture", true);
        try {
            AppiumServer.start(udId);
            androidDriver = new AndroidDriver(AppiumServer.serviceMap.get(udId).getUrl(), desiredCapabilities);
            androidDriver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
            log.sendStepLog(StepType.PASS, "????????????????????????", "");
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "???????????????????????????", "");
            //?????????????????????
            setResultDetailStatus(ResultDetailStatus.FAIL);
            throw e;
        }
        Capabilities capabilities = androidDriver.getCapabilities();
        Thread.sleep(100);
        log.androidInfo("Android", capabilities.getCapability("platformVersion").toString(),
                udId, capabilities.getCapability("deviceManufacturer").toString(),
                capabilities.getCapability("deviceModel").toString(),
                capabilities.getCapability("deviceApiLevel").toString(),
                capabilities.getCapability("deviceScreenSize").toString());
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des ??????driver
     * @date 2021/8/16 20:21
     */
    public void closeAndroidDriver() {
        try {
            if (androidDriver != null) {
                androidDriver.quit();
                log.sendStepLog(StepType.PASS, "??????????????????", "");
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

    public String getUdId() {
        return udId;
    }

    public AndroidDriver getAndroidDriver() {
        return androidDriver;
    }

    /**
     * @param status
     * @return void
     * @author ZhouYiXun
     * @des ??????????????????
     * @date 2021/8/16 23:46
     */
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

    /**
     * @return boolean
     * @author ZhouYiXun
     * @des ?????????????????????
     * @date 2021/8/16 23:16
     */
    public boolean getBattery() {
        double battery = androidDriver.getBatteryInfo().getLevel();
        if (battery <= 0.1) {
            log.sendStepLog(StepType.ERROR, "??????????????????!", "??????????????????...");
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des ??????????????????(Appium?????????cpu???network???????????????bug, ???????????????)
     * @date 2021/8/16 23:16
     */
    public void getPerform() {
        if (!testPackage.equals("")) {
            List<String> performanceData = Arrays.asList("memoryinfo", "batteryinfo");
            for (String performName : performanceData) {
                List<List<Object>> re = androidDriver.getPerformanceData(testPackage, performName, 1);
                List<Integer> mem;
                if (performName.equals("memoryinfo")) {
                    mem = Arrays.asList(0, 1, 2, 5, 6, 7);
                } else {
                    mem = Collections.singletonList(0);
                }
                JSONObject perform = new JSONObject();
                for (Integer memNum : mem) {
                    perform.put(re.get(0).get(memNum).toString(), re.get(1).get(memNum));
                }
                log.sendPerLog(testPackage, performName.equals("memoryinfo") ? 1 : 2, perform);
            }
        }
    }

    //?????????????????????????????????????????????id
    private int xpathId = 1;

    /**
     * @return com.alibaba.fastjson.JSONArray
     * @author ZhouYiXun
     * @des ????????????xpath??????
     * @date 2021/8/16 23:16
     */
    public JSONArray getResource() {
        androidDriver.context("NATIVE_APP");
        JSONArray elementList = new JSONArray();
        Document doc = Jsoup.parse(androidDriver.getPageSource());
        String xpath = "/hierarchy";
        elementList.addAll(getChildren(doc.body().children().get(0).children(), xpath));
        xpathId = 1;
        return elementList;
    }

    /**
     * @param elements
     * @param xpath    ????????????xpath
     * @return com.alibaba.fastjson.JSONArray
     * @author ZhouYiXun
     * @des ?????????????????????
     * @date 2021/8/16 23:36
     */
    public JSONArray getChildren(org.jsoup.select.Elements elements, String xpath) {
        JSONArray elementList = new JSONArray();
        for (int i = 0; i < elements.size(); i++) {
            JSONObject ele = new JSONObject();
            //tag??????
            int tagCount = 0;
            //????????????index
            int siblingIndex = 0;
            String indexXpath;
            for (int j = 0; j < elements.size(); j++) {
                if (elements.get(j).attr("class").equals(elements.get(i).attr("class"))) {
                    tagCount++;
                }
                //???i==j?????????????????????index??????tag?????????????????????xpath??????tag?????????,[]??????????????????1??????
                if (i == j) {
                    siblingIndex = tagCount;
                }
            }
            //??????tag??????????????????1???xpath???????????????[]
            if (tagCount == 1) {
                indexXpath = xpath + "/" + elements.get(i).attr("class");
            } else {
                indexXpath = xpath + "/" + elements.get(i).attr("class") + "[" + siblingIndex + "]";
            }
            ele.put("id", xpathId);
            xpathId++;
            ele.put("label", "<" + elements.get(i).attr("class") + ">");
            JSONObject detail = new JSONObject();
            detail.put("xpath", indexXpath);
            for (Attribute attr : elements.get(i).attributes()) {
                //???bounds????????????????????????????????????????????????
                if (attr.getKey().equals("bounds")) {
                    String bounds = attr.getValue().replace("][", ":");
                    String pointStart = bounds.substring(1, bounds.indexOf(":"));
                    String pointEnd = bounds.substring(bounds.indexOf(":") + 1, bounds.indexOf("]"));
                    detail.put("bStart", pointStart);
                    detail.put("bEnd", pointEnd);
                }
                detail.put(attr.getKey(), attr.getValue());
            }
            ele.put("detail", detail);
            if (elements.get(i).children().size() > 0) {
                ele.put("children", getChildren(elements.get(i).children(), indexXpath));
            }
            elementList.add(ele);
        }
        return elementList;
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des ????????????
     * @date 2021/8/16 23:56
     */
    public void startRecord() {
        try {
            AndroidStartScreenRecordingOptions recordOption = new AndroidStartScreenRecordingOptions();
            //??????30?????????appium?????????????????????
            recordOption.withTimeLimit(Duration.ofMinutes(30));
            //??????bugReport??????????????????????????????????????????
            recordOption.enableBugReport();
            //???????????????????????????????????????????????????
            recordOption.enableForcedRestart();
            //?????????????????????????????????
            recordOption.withBitRate(3000000);
            androidDriver.startRecordingScreen(recordOption);
        } catch (Exception e) {
            log.sendRecordLog(false, "", "");
        }
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des ????????????
     * @date 2021/8/16 23:56
     */
    public void stopRecord() {
        File recordDir = new File("test-output/record");
        if (!recordDir.exists()) {
            recordDir.mkdirs();
        }
        long timeMillis = Calendar.getInstance().getTimeInMillis();
        String fileName = timeMillis + "_" + udId.substring(0, 4) + ".mp4";
        File uploadFile = new File(recordDir + File.separator + fileName);
        try {
            //????????????????????????
            synchronized (AndroidStepHandler.class) {
                FileOutputStream fileOutputStream = new FileOutputStream(uploadFile);
                byte[] bytes = Base64Utils.decodeFromString((androidDriver.stopRecordingScreen()));
                fileOutputStream.write(bytes);
                fileOutputStream.close();
            }
            log.sendRecordLog(true, fileName, UploadTools.uploadPatchRecord(uploadFile));
        } catch (Exception e) {
            log.sendRecordLog(false, fileName, "");
        }
    }

//    public void settingSonicPlugins(IDevice iDevice) {
//        try {
//            androidDriver.activateApp("com.sonic.plugins");
//            try {
//                Thread.sleep(1000);
//            } catch (Exception e) {
//            }
//            log.sendStepLog(StepType.INFO, "?????????Sonic?????????", "");
//        } catch (Exception e) {
//            log.sendStepLog(StepType.ERROR, "?????????Sonic?????????", "");
//            throw e;
//        }
//        try {
//            if (!androidDriver.currentActivity().equals("com.sonic.plugins.MainActivity")) {
//                try {
//                    AndroidDeviceBridgeTool.executeCommand(iDevice, "input keyevent 4");
//                    Thread.sleep(1000);
//                } catch (Exception e) {
//                }
//            }
//            findEle("xpath", "//android.widget.TextView[@text='????????????????????????']");
//        } catch (Exception e) {
//            log.sendStepLog(StepType.ERROR, "?????????Sonic???????????????????????????????????????????????????", "");
//            throw e;
//        }
//        try {
//            findEle("id", "com.sonic.plugins:id/password_edit").clear();
//            if (AndroidPasswordMap.getMap().get(log.udId) != null
//                    && (AndroidPasswordMap.getMap().get(log.udId) != null)
//                    && (!AndroidPasswordMap.getMap().get(log.udId).equals(""))) {
//                findEle("id", "com.sonic.plugins:id/password_edit").sendKeys(AndroidPasswordMap.getMap().get(log.udId));
//            } else {
//                findEle("id", "com.sonic.plugins:id/password_edit").sendKeys("sonic123456");
//            }
//            findEle("id", "com.sonic.plugins:id/save").click();
//        } catch (Exception e) {
//            log.sendStepLog(StepType.ERROR, "??????Sonic?????????????????????", "");
//            throw e;
//        }
//    }

    public void install(HandleDes handleDes, String path) {
        handleDes.setStepDes("????????????");
        path = TextHandler.replaceTrans(path, globalParams);
        handleDes.setDetail("App??????????????? " + path);
//        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(log.udId);
//        String manufacturer = iDevice.getProperty(IDevice.PROP_DEVICE_MANUFACTURER);
        try {
            androidDriver.unlockDevice();
            if (androidDriver.getConnection().isAirplaneModeEnabled()) {
                androidDriver.toggleAirplaneMode();
            }
            if (!androidDriver.getConnection().isWiFiEnabled()) {
                androidDriver.toggleWifi();
            }
        } catch (Exception e) {
            log.sendStepLog(StepType.WARN, "?????????????????????...", "");
        }
        log.sendStepLog(StepType.INFO, "", "????????????App????????????...");
//        if (manufacturer.equals("OPPO") || manufacturer.equals("vivo") || manufacturer.equals("Meizu")) {
//            settingSonicPlugins(iDevice);
//            AndroidDeviceBridgeTool.executeCommand(iDevice, "input keyevent 3");
//        }
//        //??????????????????oppo
//        if (manufacturer.equals("OPPO")) {
//            try {
//                androidDriver.installApp(path, new AndroidInstallApplicationOptions()
//                        .withAllowTestPackagesEnabled().withReplaceEnabled()
//                        .withGrantPermissionsEnabled().withTimeout(Duration.ofMillis(60000)));
//            } catch (Exception e) {
//            }
//            //???????????????colorOs
//            if (androidDriver.currentActivity().equals(".verification.login.AccountActivity")) {
//                try {
//                    if (AndroidPasswordMap.getMap().get(log.udId) != null
//                            && (AndroidPasswordMap.getMap().get(log.udId) != null)
//                            && (!AndroidPasswordMap.getMap().get(log.udId).equals(""))) {
//                        findEle("id", "com.coloros.safecenter:id/et_login_passwd_edit"
//                        ).sendKeys(AndroidPasswordMap.getMap().get(log.udId));
//                    } else {
//                        findEle("id", "com.coloros.safecenter:id/et_login_passwd_edit"
//                        ).sendKeys("sonic123456");
//                    }
//                    findEle("id", "android:id/button1").click();
//                } catch (Exception e) {
//                }
//            }
//            AtomicInteger tryTime = new AtomicInteger(0);
//            AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
//                while (tryTime.get() < 20) {
//                    tryTime.getAndIncrement();
//                    //??????oppo???????????????
//                    try {
//                        WebElement getContinueButton = findEle("id", "com.android.packageinstaller:id/virus_scan_panel");
//                        Thread.sleep(2000);
//                        AndroidDeviceBridgeTool.executeCommand(iDevice,
//                                String.format("input tap %d %d", (getContinueButton.getRect().width) / 2
//                                        , getContinueButton.getRect().y + getContinueButton.getRect().height));
//                        Thread.sleep(2000);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    //?????????oppo?????????????????????
//                    try {
//                        findEle("id", "com.android.packageinstaller:id/install_confirm_panel");
//                        WebElement getInstallButton = findEle("id", "com.android.packageinstaller:id/bottom_button_layout");
//                        Thread.sleep(2000);
//                        AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input tap %d %d"
//                                , ((getInstallButton.getRect().width) / 4) * 3
//                                , getInstallButton.getRect().y + (getInstallButton.getRect().height) / 2));
//                        Thread.sleep(2000);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    //??????oppo????????????
//                    try {
//                        findEle("xpath", "//*[@text='????????????']");
//                        WebElement getInstallButton = findEle("id", "com.android.packageinstaller:id/install_confirm_panel");
//                        Thread.sleep(2000);
//                        AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input tap %d %d"
//                                , (getInstallButton.getRect().width) / 2, getInstallButton.getRect().y + getInstallButton.getRect().height));
//                        Thread.sleep(2000);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    if (!androidDriver.getCurrentPackage().equals("com.android.packageinstaller")) {
//                        break;
//                    }
//                }
//            });
//            while (androidDriver.getCurrentPackage().equals("com.android.packageinstaller") && tryTime.get() < 20) {
//                try {
//                    findEle("xpath", "//*[@text='??????']").click();
//                } catch (Exception e) {
//                }
//            }
//        } else {
        try {
            androidDriver.installApp(path, new AndroidInstallApplicationOptions()
                    .withAllowTestPackagesEnabled().withReplaceEnabled()
                    .withGrantPermissionsEnabled().withTimeout(Duration.ofMillis(600000)));
        } catch (Exception e) {
            handleDes.setE(e);
            return;
        }
//        }
    }

    public void uninstall(HandleDes handleDes, String appPackage) {
        handleDes.setStepDes("????????????");
        appPackage = TextHandler.replaceTrans(appPackage, globalParams);
        handleDes.setDetail("App????????? " + appPackage);
        try {
            androidDriver.removeApp(appPackage);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    /**
     * @param packageName
     * @return void
     * @author ZhouYiXun
     * @des ??????app
     * @date 2021/8/16 23:46
     */
    public void terminate(HandleDes handleDes, String packageName) {
        handleDes.setStepDes("????????????");
        packageName = TextHandler.replaceTrans(packageName, globalParams);
        handleDes.setDetail("??????????????? " + packageName);
        try {
            androidDriver.terminateApp(packageName, new AndroidTerminateApplicationOptions().withTimeout(Duration.ofMillis(1000)));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void runBackground(HandleDes handleDes, long time) {
        handleDes.setStepDes("??????????????????");
        handleDes.setDetail("????????????App " + time + " ms");
        try {
            androidDriver.runAppInBackground(Duration.ofMillis(time));
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
            androidDriver.activateApp(appPackage);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void rotateDevice(HandleDes handleDes, String text) {
        try {
            String s = "";
            handleDes.setDetail("");
            switch (text) {
                case "screenSub":
                    s = "sub";
                    handleDes.setStepDes("????????????");
                    break;
                case "screenAdd":
                    s = "add";
                    handleDes.setStepDes("????????????");
                    break;
                case "screenAbort":
                    s = "abort";
                    handleDes.setStepDes("??????????????????");
                    break;
            }
            AndroidDeviceBridgeTool.screen(AndroidDeviceBridgeTool.getIDeviceByUdId(udId), s);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void lock(HandleDes handleDes) {
        handleDes.setStepDes("????????????");
        handleDes.setDetail("");
        try {
            androidDriver.lockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void unLock(HandleDes handleDes) {
        handleDes.setStepDes("????????????");
        handleDes.setDetail("");
        try {
            androidDriver.unlockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void airPlaneMode(HandleDes handleDes) {
        handleDes.setStepDes("??????????????????");
        handleDes.setDetail("");
        try {
            androidDriver.toggleAirplaneMode();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void wifiMode(HandleDes handleDes) {
        handleDes.setStepDes("??????WIFI??????");
        handleDes.setDetail("");
        try {
            if (!androidDriver.getConnection().isWiFiEnabled()) {
                androidDriver.toggleWifi();
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void locationMode(HandleDes handleDes) {
        handleDes.setStepDes("??????????????????");
        handleDes.setDetail("");
        try {
            androidDriver.toggleLocationServices();
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
            androidDriver.hideKeyboard();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void toWebView(HandleDes handleDes, String webViewName) {
        handleDes.setStepDes("?????????" + webViewName);
        handleDes.setDetail("");
        try {
            androidDriver.context(webViewName);
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

    public void sendKeysByActions(HandleDes handleDes, String des, String selector, String pathValue, String keys) {
        keys = TextHandler.replaceTrans(keys, globalParams);
        handleDes.setStepDes("???" + des + "????????????");
        handleDes.setDetail("???" + selector + ": " + pathValue + " ??????: " + keys);
        try {
            // ??????flutter?????????????????????sendKey?????????
            new Actions(androidDriver).sendKeys(findEle(selector, pathValue),keys).perform();
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
            TouchAction ta = new TouchAction(androidDriver);
            ta.longPress(PointOption.point(x, y)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(time))).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void keyCode(HandleDes handleDes, String key) {
        handleDes.setStepDes("???????????????" + key + "???");
        handleDes.setDetail("");
        try {
            androidDriver.pressKey(new KeyEvent().withKey(AndroidKey.valueOf(key)));
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
            TouchAction hand1 = new TouchAction(androidDriver);
            TouchAction hand2 = new TouchAction(androidDriver);
            MultiTouchAction multiTouchAction = new MultiTouchAction(androidDriver);
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
            TouchAction ta = new TouchAction(androidDriver);
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
            TouchAction ta = new TouchAction(androidDriver);
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
            TouchAction ta = new TouchAction(androidDriver);
            ta.press(PointOption.point(x1, y1)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(300))).moveTo(PointOption.point(x2, y2)).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void longPress(HandleDes handleDes, String des, String selector, String pathValue, int time) {
        handleDes.setStepDes("??????" + des);
        handleDes.setDetail("??????????????????" + time + "?????? ");
        try {
            TouchAction ta = new TouchAction(androidDriver);
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
        String title = androidDriver.getTitle();
        handleDes.setStepDes("??????????????????");
        handleDes.setDetail("?????????" + title + "???????????????" + expect);
        try {
            assertEquals(title, expect);
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public void getActivity(HandleDes handleDes, String expect) {
        expect = TextHandler.replaceTrans(expect, globalParams);
        String currentActivity = getCurrentActivity();
        handleDes.setStepDes("????????????Activity");
        handleDes.setDetail("activity???" + currentActivity + "???????????????" + expect);
        try {
            assertEquals(currentActivity, expect);
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public void getElementAttr(HandleDes handleDes, String des, String selector, String pathValue, String attr, String expect) {
        handleDes.setStepDes("???????????? " + des + " ??????");
        handleDes.setDetail("?????????" + attr + "???????????????" + expect);
        try {
            String attrValue = findEle(selector, pathValue).getAttribute(attr);
            log.sendStepLog(StepType.INFO, "", attr + " ??????????????????: " + attrValue);
            try {
                assertEquals(attrValue, expect);
            } catch (AssertionError e) {
                handleDes.setE(e);
            }
        } catch (Exception e) {
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
                TouchAction ta = new TouchAction(androidDriver);
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

    public void toHandle(HandleDes handleDes, String titleName) throws Exception {
        handleDes.setStepDes("??????Handle");
        handleDes.setDetail("");
        Thread.sleep(1000);
        Set<String> handle = androidDriver.getWindowHandles();//??????handles
        String ha;
        for (int i = 1; i <= handle.size(); i++) {
            ha = (String) handle.toArray()[handle.size() - i];//??????handle
            try {
                androidDriver.switchTo().window(ha);//?????????????????????handle
            } catch (Exception e) {
            }
            if (androidDriver.getTitle().equals(titleName)) {
                handleDes.setDetail("?????????Handle:" + ha);
                log.sendStepLog(StepType.INFO, "????????????:" + androidDriver.getTitle(), "");
                break;
            }
        }
    }

    public File getScreenToLocal() {
        File file = ((TakesScreenshot) androidDriver).getScreenshotAs(OutputType.FILE);
        File resultFile = new File("test-output/" + log.udId + Calendar.getInstance().getTimeInMillis() + ".jpg");
        try {
            FileCopyUtils.copy(file, resultFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultFile;
    }

    public void checkImage(HandleDes handleDes, String des, String pathValue, double matchThreshold) throws Exception {
        try {
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
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void exceptionLog(Throwable e) {
        log.sendStepLog(StepType.WARN, "", "??????????????? " + e.fillInStackTrace().toString());
    }

    public void errorScreen() {
        try {
            androidDriver.context("NATIVE_APP");//????????????app
            log.sendStepLog(StepType.WARN, "??????????????????", UploadTools
                    .upload(((TakesScreenshot) androidDriver).getScreenshotAs(OutputType.FILE), "imageFiles"));
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "??????????????????", "");
        }
    }

    public String stepScreen(HandleDes handleDes) {
        handleDes.setStepDes("????????????");
        handleDes.setDetail("");
        String url = "";
        try {
            androidDriver.context("NATIVE_APP");//????????????app
            url = UploadTools.upload(((TakesScreenshot) androidDriver)
                    .getScreenshotAs(OutputType.FILE), "imageFiles");
            handleDes.setDetail(url);
        } catch (Exception e) {
            handleDes.setE(e);
        }
        return url;
    }

    public Set<String> getWebView() {
        Set<String> contextNames = androidDriver.getContextHandles();
        return contextNames;
    }

    public String getCurrentActivity() {
        return androidDriver.currentActivity();
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

    public void runMonkey(HandleDes handleDes, JSONObject content, List<JSONObject> text) {
        handleDes.setStepDes("??????????????????????????????");
        handleDes.setDetail("");
        String packageName = content.getString("packageName");
        int pctNum = content.getInteger("pctNum");
        if (!androidDriver.isAppInstalled(packageName)) {
            log.sendStepLog(StepType.ERROR, "??????????????????", "??????????????? " + packageName);
            handleDes.setE(new Exception("???????????????"));
            return;
        }
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        JSONArray options = content.getJSONArray("options");
        int width = androidDriver.manage().window().getSize().width;
        int height = androidDriver.manage().window().getSize().height;
        int sleepTime = 50;
        int systemEvent = 0;
        int tapEvent = 0;
        int longPressEvent = 0;
        int swipeEvent = 0;
        int zoomEvent = 0;
        int navEvent = 0;
        boolean isOpenH5Listener = false;
        boolean isOpenPackageListener = false;
        boolean isOpenActivityListener = false;
        boolean isOpenNetworkListener = false;
        if (!options.isEmpty()) {
            for (int i = options.size() - 1; i >= 0; i--) {
                JSONObject jsonOption = (JSONObject) options.get(i);
                if (jsonOption.getString("name").equals("sleepTime")) {
                    sleepTime = jsonOption.getInteger("value");
                }
                if (jsonOption.getString("name").equals("systemEvent")) {
                    systemEvent = jsonOption.getInteger("value");
                }
                if (jsonOption.getString("name").equals("tapEvent")) {
                    tapEvent = jsonOption.getInteger("value");
                }
                if (jsonOption.getString("name").equals("longPressEvent")) {
                    longPressEvent = jsonOption.getInteger("value");
                }
                if (jsonOption.getString("name").equals("swipeEvent")) {
                    swipeEvent = jsonOption.getInteger("value");
                }
                if (jsonOption.getString("name").equals("zoomEvent")) {
                    zoomEvent = jsonOption.getInteger("value");
                }
                if (jsonOption.getString("name").equals("navEvent")) {
                    navEvent = jsonOption.getInteger("value");
                }
                if (jsonOption.getString("name").equals("isOpenH5Listener")) {
                    isOpenH5Listener = jsonOption.getBoolean("value");
                }
                if (jsonOption.getString("name").equals("isOpenPackageListener")) {
                    isOpenPackageListener = jsonOption.getBoolean("value");
                }
                if (jsonOption.getString("name").equals("isOpenActivityListener")) {
                    isOpenActivityListener = jsonOption.getBoolean("value");
                }
                if (jsonOption.getString("name").equals("isOpenNetworkListener")) {
                    isOpenNetworkListener = jsonOption.getBoolean("value");
                }
                options.remove(options.get(i));
            }
        }
        int finalSleepTime = sleepTime;
        int finalTapEvent = tapEvent;
        int finalLongPressEvent = longPressEvent;
        int finalSwipeEvent = swipeEvent;
        int finalZoomEvent = zoomEvent;
        int finalSystemEvent = systemEvent;
        int finalNavEvent = navEvent;
        Future<?> randomThread = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    log.sendStepLog(StepType.INFO, "", "??????????????????" + pctNum +
                            "<br>???????????????" + packageName
                            + "<br>?????????????????????" + finalSleepTime + " ms"
                            + "<br>?????????????????????" + finalTapEvent
                            + "<br>?????????????????????" + finalLongPressEvent
                            + "<br>?????????????????????" + finalSwipeEvent
                            + "<br>???????????????????????????" + finalZoomEvent
                            + "<br>???????????????????????????" + finalSystemEvent
                            + "<br>?????????????????????" + finalNavEvent
                    );
                    openApp(new HandleDes(), packageName);
                    TouchAction ta = new TouchAction(androidDriver);
                    TouchAction ta2 = new TouchAction(androidDriver);
                    MultiTouchAction multiTouchAction = new MultiTouchAction(androidDriver);
                    int totalCount = finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent + finalZoomEvent + finalNavEvent;
                    for (int i = 0; i < pctNum; i++) {
                        try {
                            int random = new Random().nextInt(totalCount);
                            if (random >= 0 && random < finalSystemEvent) {
                                int key = new Random().nextInt(9);
                                String keyType = "";
                                switch (key) {
                                    case 0:
                                        keyType = "HOME";
                                        break;
                                    case 1:
                                        keyType = "BACK";
                                        break;
                                    case 2:
                                        keyType = "MENU";
                                        break;
                                    case 3:
                                        keyType = "APP_SWITCH";
                                        break;
                                    case 4:
                                        keyType = "BRIGHTNESS_DOWN";
                                        break;
                                    case 5:
                                        keyType = "BRIGHTNESS_UP";
                                        break;
                                    case 6:
                                        keyType = "VOLUME_UP";
                                        break;
                                    case 7:
                                        keyType = "VOLUME_DOWN";
                                        break;
                                    case 8:
                                        keyType = "VOLUME_MUTE";
                                        break;
                                }
                                androidDriver.pressKey(new KeyEvent(AndroidKey.valueOf(keyType)));
                            }
                            if (random >= finalSystemEvent && random < (finalSystemEvent + finalTapEvent)) {
                                int x = new Random().nextInt(width - 60) + 60;
                                int y = new Random().nextInt(height - 60) + 60;
                                ta.tap(PointOption.point(x, y)).perform();
                            }
                            if (random >= (finalSystemEvent + finalTapEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent)) {
                                int x = new Random().nextInt(width - 60) + 60;
                                int y = new Random().nextInt(height - 60) + 60;
                                ta.longPress(PointOption.point(x, y)).waitAction(WaitOptions.waitOptions(Duration.ofSeconds(new Random().nextInt(3) + 1))).release().perform();
                            }
                            if (random >= (finalSystemEvent + finalTapEvent + finalLongPressEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent)) {
                                int x1 = new Random().nextInt(width - 60) + 60;
                                int y1 = new Random().nextInt(height - 80) + 80;
                                int x2 = new Random().nextInt(width - 60) + 60;
                                int y2 = new Random().nextInt(height - 80) + 80;
                                ta.press(PointOption.point(x1, y1)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(200))).moveTo(PointOption.point(x2, y2)).release().perform();
                            }
                            if (random >= (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent + finalZoomEvent)) {
                                int x1 = new Random().nextInt(width - 80);
                                int y1 = new Random().nextInt(height - 80);
                                int x2 = new Random().nextInt(width - 100);
                                int y2 = new Random().nextInt(height - 80);
                                int x3 = new Random().nextInt(width - 100);
                                int y3 = new Random().nextInt(height - 80);
                                int x4 = new Random().nextInt(width - 100);
                                int y4 = new Random().nextInt(height - 80);
                                ta.press(PointOption.point(x1, y1)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(200))).moveTo(PointOption.point(x2, y2)).release();
                                ta2.press(PointOption.point(x3, y3)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(200))).moveTo(PointOption.point(x4, y4)).release();
                                multiTouchAction.add(ta);
                                multiTouchAction.add(ta2);
                                multiTouchAction.perform();
                            }
                            if (random >= (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent + finalZoomEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent + finalZoomEvent + finalNavEvent)) {
                                androidDriver.toggleWifi();
                            }
                            Thread.sleep(finalSleepTime);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }
        );
        Boolean finalIsOpenH5Listener = isOpenH5Listener;
        Future<?> H5Listener = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    if (finalIsOpenH5Listener) {
                        int h5Time = 0;
                        while (!randomThread.isDone()) {
                            try {
                                Thread.sleep(8000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            try {
                                if (androidDriver.findElementsByClassName("android.webkit.WebView").size() > 0) {
                                    h5Time++;
                                    AndroidDeviceBridgeTool.executeCommand(iDevice, "input keyevent 4");
                                } else {
                                    h5Time = 0;
                                }
                                if (h5Time >= 12) {
                                    androidDriver.terminateApp(packageName, new AndroidTerminateApplicationOptions().withTimeout(Duration.ofMillis(1000)));
                                    h5Time = 0;
                                }
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
        );
        boolean finalIsOpenPackageListener = isOpenPackageListener;
        Future<?> packageListener = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    if (finalIsOpenPackageListener) {
                        while (!randomThread.isDone()) {
                            int waitTime = 0;
                            while (waitTime <= 10 && (!randomThread.isDone())) {
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if (!androidDriver.getCurrentPackage().equals(packageName)) {
                                    androidDriver.activateApp(packageName);
                                }
                                waitTime++;
                            }
                            androidDriver.activateApp(packageName);
                        }
                    }
                }
        );
        boolean finalIsOpenActivityListener = isOpenActivityListener;
        Future<?> activityListener = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    if (finalIsOpenActivityListener) {
                        if (text.isEmpty()) {
                            return;
                        }
                        Set<String> blackList = new HashSet<>();
                        for (JSONObject activities : text) {
                            blackList.add(activities.getString("name"));
                        }
                        while (!randomThread.isDone()) {
                            try {
                                Thread.sleep(8000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (blackList.contains(getCurrentActivity())) {
                                AndroidDeviceBridgeTool.executeCommand(iDevice, "input keyevent 4");
                            } else continue;
                            try {
                                Thread.sleep(8000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (blackList.contains(getCurrentActivity())) {
                                androidDriver.terminateApp(packageName, new AndroidTerminateApplicationOptions().withTimeout(Duration.ofMillis(1000)));
                            }
                        }
                    }
                }
        );
        boolean finalIsOpenNetworkListener = isOpenNetworkListener;
        Future<?> networkListener = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    if (finalIsOpenNetworkListener) {
                        while (!randomThread.isDone()) {
                            try {
                                Thread.sleep(8000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (androidDriver.getConnection().isAirplaneModeEnabled()) {
                                androidDriver.toggleAirplaneMode();
                            }
                            try {
                                Thread.sleep(8000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (!androidDriver.getConnection().isWiFiEnabled()) {
                                androidDriver.toggleWifi();
                            }
                        }
                    }
                }
        );
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        if (version.length() == 0) {
//            version = AndroidDeviceBridgeTool.getAppOnlyVersion(udId, packageName);
//        }
        log.sendStepLog(StepType.INFO, "", "??????????????????" + packageName +
                (isOpenPackageListener ? "<br>??????????????????????????????..." : "") +
                (isOpenH5Listener ? "<br>H5????????????????????????..." : "") +
                (isOpenActivityListener ? "<br>?????????Activity?????????..." : "") +
                (isOpenNetworkListener ? "<br>??????????????????????????????..." : ""));
        while (!randomThread.isDone() || (!packageListener.isDone()) || (!activityListener.isDone()) || (!networkListener.isDone()) || (!H5Listener.isDone())) {
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
                we = androidDriver.findElementById(pathValue);
                break;
            case "accessibilityId":
                we = androidDriver.findElementByAccessibilityId(pathValue);
                break;
            case "name":
                we = androidDriver.findElementByName(pathValue);
                break;
            case "xpath":
                we = androidDriver.findElementByXPath(pathValue);
                break;
            case "cssSelector":
                we = androidDriver.findElement(By.cssSelector(pathValue));
                break;
            case "className":
                we = androidDriver.findElementByClassName(pathValue);
                break;
            case "tagName":
                we = androidDriver.findElementByTagName(pathValue);
                break;
            case "linkText":
                we = androidDriver.findElementByLinkText(pathValue);
                break;
            case "partialLinkText":
                we = androidDriver.findElementByPartialLinkText(pathValue);
                break;
            case "cssSelectorAndText":
                we = getWebElementByCssAndText(pathValue);
                break;
            case "androidUIAutomator":
                //????????????????????????UIAutomator2?????????
                we = androidDriver.findElementByAndroidUIAutomator(pathValue);
            default:
                log.sendStepLog(StepType.ERROR, "????????????????????????", "????????????????????????: " + selector + " ?????????!!!");
                break;
        }
        return we;
    }

    private WebElement getWebElementByCssAndText(String pathValue) {
        // ??????H5????????????className+text??????????????????
        // value?????????van-button--default,?????????
        WebElement element = null;
        List<String> values = new ArrayList<>(Arrays.asList(pathValue.split(",")));
        if(values.size() >= 2) {
            // findElementsByClassName???????????????chromedriver???bug????????????cssSelector????????????????????????
            List<WebElement> els =   androidDriver.findElements(By.cssSelector(values.get(0)));
            for(WebElement el: els) {
                if(el.getText().equals(values.get(1))) {
                    element = el;
                    break;
                }
            }
        }
        return element;
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
            case "toWebView":
                toWebView(handleDes, step.getString("content"));
                break;
            case "toHandle":
                toHandle(handleDes, step.getString("content"));
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
            case "getActivity":
                getActivity(handleDes, step.getString("content"));
                break;
            case "getElementAttr":
                getElementAttr(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getString("text"), step.getString("content"));
                break;
            case "sendKeys":
                sendKeys(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
                break;
            case "sendKeysByActions":
                sendKeysByActions(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
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
            case "screenSub":
            case "screenAdd":
            case "screenAbort":
                rotateDevice(handleDes, step.getString("stepType"));
                break;
            case "lock":
                lock(handleDes);
                break;
            case "unLock":
                unLock(handleDes);
                break;
            case "airPlaneMode":
                airPlaneMode(handleDes);
                break;
            case "wifiMode":
                wifiMode(handleDes);
                break;
            case "locationMode":
                locationMode(handleDes);
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
            case "monkey":
                runMonkey(handleDes, step.getJSONObject("content"), step.getJSONArray("text").toJavaList(JSONObject.class));
                break;
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
            if (stepJson.getInteger("conditionType").equals(0)) {
                handleDes.clear();
            }
        } else {
            log.sendStepLog(StepType.PASS, stepDes, detail);
        }
    }
}
