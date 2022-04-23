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
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.StepHandlers;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.cv.AKAZEFinder;
import org.cloud.sonic.agent.tools.cv.SIFTFinder;
import org.cloud.sonic.agent.tools.cv.SimilarityChecker;
import org.cloud.sonic.agent.tools.cv.TemMatcher;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.cloud.sonic.common.tools.SpringTool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
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
 * @des 安卓自动化处理类
 * @date 2021/8/16 20:10
 */
public class AndroidStepHandler {
    public LogUtil log = new LogUtil();
    private AndroidDriver androidDriver;
    private JSONObject globalParams = new JSONObject();
    //包版本
//    private String version = "";
    //测试起始时间
    private long startTime;
    //测试的包名
    private String testPackage = "";
    private String udId = "";
    //测试状态
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
     * @des new时开始计时
     * @date 2021/8/16 20:01
     */
    public AndroidStepHandler() {
        startTime = Calendar.getInstance().getTimeInMillis();
    }

    /**
     * @param udId
     * @return void
     * @author ZhouYiXun
     * @des 启动安卓驱动，连接设备
     * @date 2021/8/16 20:01
     */
    public void startAndroidDriver(String udId) throws InterruptedException {
        this.udId = udId;
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        //微信webView配置
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.setExperimentalOption("androidProcess", "com.tencent.mm:tools");
        desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
        //webView通用配置，自动下载匹配的driver
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.RECREATE_CHROME_DRIVER_SESSIONS, true);
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.CHROMEDRIVER_EXECUTABLE_DIR, "webview");
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.CHROMEDRIVER_CHROME_MAPPING_FILE, "webview/version.json");
        //平台
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.PLATFORM_NAME, Platform.ANDROID);
        //选用的自动化框架
        desiredCapabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, AutomationName.ANDROID_UIAUTOMATOR2);
        //关闭运行时阻塞其他Accessibility服务，开启的话其他不能使用了
        desiredCapabilities.setCapability("disableSuppressAccessibilityService", true);
        //adb指令超时时间
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.ADB_EXEC_TIMEOUT, 7200000);
        //UIA2安装超时时间
        desiredCapabilities.setCapability("uiautomator2ServerInstallTimeout", 600000);

        //io.appium.uiautomator2.server io.appium.uiautomator2.server.test //io.appium.settings
//        desiredCapabilities.setCapability("skipServerInstallation",true);
//        desiredCapabilities.setCapability("disableWindowAnimation",true);
//        desiredCapabilities.setCapability("skipDeviceInitialization",true);
        //等待新命令超时时间
        desiredCapabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, 7200);
        //不重置应用
        desiredCapabilities.setCapability(MobileCapabilityType.NO_RESET, true);
        //单独唤起应用的话，这个需要设置空字符串
        desiredCapabilities.setCapability(MobileCapabilityType.BROWSER_NAME, "");
        //指定设备序列号
        desiredCapabilities.setCapability(MobileCapabilityType.UDID, udId);
        //随机systemPort
        desiredCapabilities.setCapability(AndroidMobileCapabilityType.SYSTEM_PORT, PortTool.getPort());
        desiredCapabilities.setCapability("skipLogcatCapture", true);
        try {
            AppiumServer.start(udId);
            androidDriver = new AndroidDriver(AppiumServer.serviceMap.get(udId).getUrl(), desiredCapabilities);
            androidDriver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
            log.sendStepLog(StepType.PASS, "连接设备驱动成功", "");
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "连接设备驱动失败！", "");
            //测试标记为失败
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
     * @des 关闭driver
     * @date 2021/8/16 20:21
     */
    public void closeAndroidDriver() {
        try {
            if (androidDriver != null) {
                androidDriver.quit();
                log.sendStepLog(StepType.PASS, "退出连接设备", "");
            }
        } catch (Exception e) {
            log.sendStepLog(StepType.WARN, "测试终止异常！请检查设备连接状态", "");
            //测试异常
            setResultDetailStatus(ResultDetailStatus.WARN);
            e.printStackTrace();
        }finally {
            AppiumServer.close(udId);
        }
    }

    public void waitDevice(int waitCount) {
        log.sendStepLog(StepType.INFO, "设备非空闲状态！第" + waitCount + "次等待连接...", "");
    }

    public void waitDeviceTimeOut() {
        log.sendStepLog(StepType.ERROR, "等待设备超时！测试跳过！", "");
        //测试标记为异常
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
     * @des 设置测试状态
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

    //判断有无出错
    public int getStatus() {
        return status;
    }

    //调试每次重设状态
    public void resetResultDetailStatus() {
        status = 1;
    }

    /**
     * @return boolean
     * @author ZhouYiXun
     * @des 检测是否低电量
     * @date 2021/8/16 23:16
     */
    public boolean getBattery() {
        double battery = androidDriver.getBatteryInfo().getLevel();
        if (battery <= 0.1) {
            log.sendStepLog(StepType.ERROR, "设备电量过低!", "跳过本次测试...");
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des 获取性能信息(Appium自带的cpu和network方法貌似有bug, 后续再优化)
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

    //配合前端渲染，需要每个节点加上id
    private int xpathId = 1;

    /**
     * @return com.alibaba.fastjson.JSONArray
     * @author ZhouYiXun
     * @des 获取页面xpath信息
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
     * @param xpath    父级节点xpath
     * @return com.alibaba.fastjson.JSONArray
     * @author ZhouYiXun
     * @des 获取子节点信息
     * @date 2021/8/16 23:36
     */
    public JSONArray getChildren(org.jsoup.select.Elements elements, String xpath) {
        JSONArray elementList = new JSONArray();
        for (int i = 0; i < elements.size(); i++) {
            JSONObject ele = new JSONObject();
            //tag次数
            int tagCount = 0;
            //兄弟节点index
            int siblingIndex = 0;
            String indexXpath;
            for (int j = 0; j < elements.size(); j++) {
                if (elements.get(j).attr("class").equals(elements.get(i).attr("class"))) {
                    tagCount++;
                }
                //当i==j时候，兄弟节点index等于tag出现次数，因为xpath多个tag的时候,[]里面下标是从1开始
                if (i == j) {
                    siblingIndex = tagCount;
                }
            }
            //如果tag出现次数等于1，xpath结尾不添加[]
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
                //把bounds字段拆出来解析，方便前端进行截取
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
     * @des 开始录像
     * @date 2021/8/16 23:56
     */
    public void startRecord() {
        try {
            AndroidStartScreenRecordingOptions recordOption = new AndroidStartScreenRecordingOptions();
            //限制30分钟，appium支持的最长时间
            recordOption.withTimeLimit(Duration.ofMinutes(30));
            //开启bugReport，开启后录像会有相关附加信息
            recordOption.enableBugReport();
            //是否强制终止上次录像并开始新的录像
            recordOption.enableForcedRestart();
            //限制码率，防止录像过大
            recordOption.withBitRate(3000000);
            androidDriver.startRecordingScreen(recordOption);
        } catch (Exception e) {
            log.sendRecordLog(false, "", "");
        }
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des 停止录像
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
            //加锁防止内存泄漏
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
//            log.sendStepLog(StepType.INFO, "已安装Sonic插件！", "");
//        } catch (Exception e) {
//            log.sendStepLog(StepType.ERROR, "未安装Sonic插件！", "");
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
//            findEle("xpath", "//android.widget.TextView[@text='服务状态：已开启']");
//        } catch (Exception e) {
//            log.sendStepLog(StepType.ERROR, "未开启Sonic插件服务！请到辅助功能或无障碍开启", "");
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
//            log.sendStepLog(StepType.ERROR, "配置Sonic插件服务失败！", "");
//            throw e;
//        }
//    }

    public void install(HandleDes handleDes, String path) {
        handleDes.setStepDes("安装应用");
        handleDes.setDetail("App安装路径： " + path);
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
            log.sendStepLog(StepType.WARN, "安装前准备跳过...", "");
        }
        log.sendStepLog(StepType.INFO, "", "开始安装App，请稍后...");
//        if (manufacturer.equals("OPPO") || manufacturer.equals("vivo") || manufacturer.equals("Meizu")) {
//            settingSonicPlugins(iDevice);
//            AndroidDeviceBridgeTool.executeCommand(iDevice, "input keyevent 3");
//        }
//        //单独适配一下oppo
//        if (manufacturer.equals("OPPO")) {
//            try {
//                androidDriver.installApp(path, new AndroidInstallApplicationOptions()
//                        .withAllowTestPackagesEnabled().withReplaceEnabled()
//                        .withGrantPermissionsEnabled().withTimeout(Duration.ofMillis(60000)));
//            } catch (Exception e) {
//            }
//            //单独再适配colorOs
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
//                    //部分oppo有继续安装
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
//                    //低版本oppo安装按钮在右边
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
//                    //部分oppo无法点击
//                    try {
//                        findEle("xpath", "//*[@text='应用权限']");
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
//                    findEle("xpath", "//*[@text='完成']").click();
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
        handleDes.setStepDes("卸载应用");
        handleDes.setDetail("App包名： " + appPackage);
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
     * @des 终止app
     * @date 2021/8/16 23:46
     */
    public void terminate(HandleDes handleDes, String packageName) {
        handleDes.setStepDes("终止应用");
        handleDes.setDetail("应用包名： " + packageName);
        try {
            androidDriver.terminateApp(packageName, new AndroidTerminateApplicationOptions().withTimeout(Duration.ofMillis(1000)));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void runBackground(HandleDes handleDes, long time) {
        handleDes.setStepDes("后台运行应用");
        handleDes.setDetail("后台运行App " + time + " ms");
        try {
            androidDriver.runAppInBackground(Duration.ofMillis(time));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void openApp(HandleDes handleDes, String appPackage) {
        handleDes.setStepDes("打开应用");
        handleDes.setDetail("App包名： " + appPackage);
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
                    handleDes.setStepDes("左转屏幕");
                    break;
                case "screenAdd":
                    s = "add";
                    handleDes.setStepDes("右转屏幕");
                    break;
                case "screenAbort":
                    s = "abort";
                    handleDes.setStepDes("关闭自动旋转");
                    break;
            }
            AndroidDeviceBridgeTool.screen(AndroidDeviceBridgeTool.getIDeviceByUdId(udId), s);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void lock(HandleDes handleDes) {
        handleDes.setStepDes("锁定屏幕");
        handleDes.setDetail("");
        try {
            androidDriver.lockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void unLock(HandleDes handleDes) {
        handleDes.setStepDes("解锁屏幕");
        handleDes.setDetail("");
        try {
            androidDriver.unlockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void airPlaneMode(HandleDes handleDes) {
        handleDes.setStepDes("切换飞行模式");
        handleDes.setDetail("");
        try {
            androidDriver.toggleAirplaneMode();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void wifiMode(HandleDes handleDes) {
        handleDes.setStepDes("打开WIFI网络");
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
        handleDes.setStepDes("切换位置服务");
        handleDes.setDetail("");
        try {
            androidDriver.toggleLocationServices();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void asserts(HandleDes handleDes, String actual, String expect, String type) {
        handleDes.setDetail("真实值： " + actual + " 期望值： " + expect);
        handleDes.setStepDes("");
        try {
            switch (type) {
                case "assertEquals":
                    handleDes.setStepDes("断言验证(相等)");
                    assertEquals(actual, expect);
                    break;
                case "assertTrue":
                    handleDes.setStepDes("断言验证(包含)");
                    assertTrue(actual.contains(expect));
                    break;
                case "assertNotTrue":
                    handleDes.setStepDes("断言验证(不包含)");
                    assertFalse(actual.contains(expect));
                    break;
            }
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public String getText(HandleDes handleDes, String des, String selector, String pathValue) {
        String s = "";
        handleDes.setStepDes("获取" + des + "文本");
        handleDes.setDetail("获取" + selector + ":" + pathValue + "文本");
        try {
            s = findEle(selector, pathValue).getText();
            log.sendStepLog(StepType.INFO, "", "文本获取结果: " + s);
        } catch (Exception e) {
            handleDes.setE(e);
        }
        return s;
    }

    public void hideKey(HandleDes handleDes) {
        handleDes.setStepDes("隐藏键盘");
        handleDes.setDetail("隐藏弹出键盘");
        try {
            androidDriver.hideKeyboard();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void toWebView(HandleDes handleDes, String webViewName) {
        handleDes.setStepDes("切换到" + webViewName);
        handleDes.setDetail("");
        try {
            androidDriver.context(webViewName);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void click(HandleDes handleDes, String des, String selector, String pathValue) {
        handleDes.setStepDes("点击" + des);
        handleDes.setDetail("点击" + selector + ": " + pathValue);
        try {
            findEle(selector, pathValue).click();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void sendKeys(HandleDes handleDes, String des, String selector, String pathValue, String keys) {
        if (keys.contains("{{random}}")) {
            String random = (int) (Math.random() * 10 + Math.random() * 10 * 2) + 5 + "";
            keys = keys.replace("{{random}}", random);
        }
        if (keys.contains("{{timestamp}}")) {
            String timeMillis = Calendar.getInstance().getTimeInMillis() + "";
            keys = keys.replace("{{timestamp}}", timeMillis);
        }
        keys = replaceTrans(keys);
        handleDes.setStepDes("对" + des + "输入内容");
        handleDes.setDetail("对" + selector + ": " + pathValue + " 输入: " + keys);
        try {
            findEle(selector, pathValue).sendKeys(keys);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void getTextAndAssert(HandleDes handleDes, String des, String selector, String pathValue, String expect) {
        handleDes.setStepDes("获取" + des + "文本");
        handleDes.setDetail("获取" + selector + ":" + pathValue + "文本");
        try {
            String s = findEle(selector, pathValue).getText();
            log.sendStepLog(StepType.INFO, "", "文本获取结果: " + s);
            try {
                expect = replaceTrans(expect);
                assertEquals(s, expect);
                log.sendStepLog(StepType.INFO, "验证文本", "真实值： " + s + " 期望值： " + expect);
            } catch (AssertionError e) {
                log.sendStepLog(StepType.ERROR, "验证" + des + "文本失败！", "");
                handleDes.setE(e);
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void longPressPoint(HandleDes handleDes, String des, String xy, int time) {
        int x = Integer.parseInt(xy.substring(0, xy.indexOf(",")));
        int y = Integer.parseInt(xy.substring(xy.indexOf(",") + 1));
        handleDes.setStepDes("长按" + des);
        handleDes.setDetail("长按坐标" + time + "毫秒 (" + x + "," + y + ")");
        try {
            TouchAction ta = new TouchAction(androidDriver);
            ta.longPress(PointOption.point(x, y)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(time))).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void keyCode(HandleDes handleDes, String key) {
        handleDes.setStepDes("按系统按键" + key + "键");
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
        String detail = "坐标" + des1 + "( " + x1 + ", " + y1 + " )移动到坐标" + des2 + "( " + x2 + ", " + y2 + " ),同时坐标" + des3 + "( " + x3 + ", " + y3 + " )移动到坐标" + des4 + "( " + x4 + ", " + y4 + " )";
        handleDes.setStepDes("双指操作");
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
        handleDes.setStepDes("点击" + des);
        handleDes.setDetail("点击坐标(" + x + "," + y + ")");
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
        handleDes.setStepDes("滑动拖拽" + des1 + "到" + des2);
        handleDes.setDetail("拖动坐标(" + x1 + "," + y1 + ")到(" + x2 + "," + y2 + ")");
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
        handleDes.setStepDes("滑动拖拽" + des + "到" + des2);
        handleDes.setDetail("拖动坐标(" + x1 + "," + y1 + ")到(" + x2 + "," + y2 + ")");
        try {
            TouchAction ta = new TouchAction(androidDriver);
            ta.press(PointOption.point(x1, y1)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(300))).moveTo(PointOption.point(x2, y2)).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void longPress(HandleDes handleDes, String des, String selector, String pathValue, int time) {
        handleDes.setStepDes("长按" + des);
        handleDes.setDetail("长按控件元素" + time + "毫秒 ");
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
        handleDes.setStepDes("清空" + des);
        handleDes.setDetail("清空" + selector + ": " + pathValue);
        try {
            findEle(selector, pathValue).clear();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void isExistEle(HandleDes handleDes, String des, String selector, String pathValue, boolean expect) {
        handleDes.setStepDes("判断控件 " + des + " 是否存在");
        handleDes.setDetail("期望值：" + (expect ? "存在" : "不存在"));
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
        handleDes.setStepDes("验证网页标题");
        handleDes.setDetail("标题：" + title + "，期望值：" + expect);
        try {
            assertEquals(title, expect);
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public void clickByImg(HandleDes handleDes, String des, String pathValue) throws Exception {
        handleDes.setStepDes("点击图片" + des);
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
            log.sendStepLog(StepType.WARN, "SIFT图像算法出错，切换算法中...",
                    "");
        }
        if (findResult != null) {
            log.sendStepLog(StepType.INFO, "图片定位到坐标：(" + findResult.getX() + "," + findResult.getY() + ")  耗时：" + findResult.getTime() + " ms",
                    findResult.getUrl());
        } else {
            log.sendStepLog(StepType.INFO, "SIFT算法无法定位图片，切换AKAZE算法中...",
                    "");
            try {
                AKAZEFinder akazeFinder = new AKAZEFinder();
                findResult = akazeFinder.getAKAZEFindResult(file, getScreenToLocal());
            } catch (Exception e) {
                log.sendStepLog(StepType.WARN, "AKAZE图像算法出错，切换模版匹配算法中...",
                        "");
            }
            if (findResult != null) {
                log.sendStepLog(StepType.INFO, "图片定位到坐标：(" + findResult.getX() + "," + findResult.getY() + ")  耗时：" + findResult.getTime() + " ms",
                        findResult.getUrl());
            } else {
                log.sendStepLog(StepType.INFO, "AKAZE算法无法定位图片，切换模版匹配算法中...",
                        "");
                try {
                    TemMatcher temMatcher = new TemMatcher();
                    findResult = temMatcher.getTemMatchResult(file, getScreenToLocal());
                } catch (Exception e) {
                    log.sendStepLog(StepType.WARN, "模版匹配算法出错",
                            "");
                }
                if (findResult != null) {
                    log.sendStepLog(StepType.INFO, "图片定位到坐标：(" + findResult.getX() + "," + findResult.getY() + ")  耗时：" + findResult.getTime() + " ms",
                            findResult.getUrl());
                } else {
                    handleDes.setE(new Exception("图片定位失败！"));
                }
            }
        }
        if (findResult != null) {
            try {
                TouchAction ta = new TouchAction(androidDriver);
                ta.tap(PointOption.point(findResult.getX(), findResult.getY())).perform();
            } catch (Exception e) {
                log.sendStepLog(StepType.ERROR, "点击" + des + "失败！", "");
                handleDes.setE(e);
            }
        }
    }

    public void readText(HandleDes handleDes, String language, String text) throws Exception {
//        TextReader textReader = new TextReader();
//        String result = textReader.getTessResult(getScreenToLocal(), language);
//        log.sendStepLog(StepType.INFO, "",
//                "图像文字识别结果：<br>" + result);
//        String filter = result.replaceAll(" ", "");
        handleDes.setStepDes("图像文字识别");
        handleDes.setDetail("（该功能暂时关闭）期望包含文本：" + text);
//        if (!filter.contains(text)) {
//            handleDes.setE(new Exception("图像文字识别不通过！"));
//        }
    }

    public void toHandle(HandleDes handleDes, String titleName) throws Exception {
        handleDes.setStepDes("切换Handle");
        handleDes.setDetail("");
        Thread.sleep(1000);
        Set<String> handle = androidDriver.getWindowHandles();//获取handles
        String ha;
        for (int i = 1; i <= handle.size(); i++) {
            ha = (String) handle.toArray()[handle.size() - i];//查找handle
            try {
                androidDriver.switchTo().window(ha);//切换到最后一个handle
            } catch (Exception e) {
            }
            if (androidDriver.getTitle().equals(titleName)) {
                handleDes.setDetail("切换到Handle:" + ha);
                log.sendStepLog(StepType.INFO, "页面标题:" + androidDriver.getTitle(), "");
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

    public String replaceTrans(String text) {
        if (text.contains("{{") && text.contains("}}")) {
            String tail = text.substring(text.indexOf("{{") + 2);
            if (tail.contains("}}")) {
                String child = tail.substring(tail.indexOf("}}") + 2);
                String middle = tail.substring(0, tail.indexOf("}}"));
                text = text.substring(0, text.indexOf("}}") + 2);
                if (globalParams.getString(middle) != null) {
                    text = text.replace("{{" + middle + "}}", globalParams.getString(middle));
                }
                text = text + replaceTrans(child);
            }
        }
        return text;
    }

    public void checkImage(HandleDes handleDes, String des, String pathValue, double matchThreshold) throws Exception {
        try {
            log.sendStepLog(StepType.INFO, "开始检测" + des + "兼容", "检测与当前设备截图相似度，期望相似度为" + matchThreshold + "%");
            File file = null;
            if (pathValue.startsWith("http")) {
                file = DownloadTool.download(pathValue);
            }
            double score = SimilarityChecker.getSimilarMSSIMScore(file, getScreenToLocal(), true);
            handleDes.setStepDes("检测" + des + "图片相似度");
            handleDes.setDetail("相似度为" + score * 100 + "%");
            if (score == 0) {
                handleDes.setE(new Exception("图片相似度检测不通过！比对图片分辨率不一致！"));
            } else if (score < (matchThreshold / 100)) {
                handleDes.setE(new Exception("图片相似度检测不通过！expect " + matchThreshold + " but " + score * 100));
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void exceptionLog(Throwable e) {
        log.sendStepLog(StepType.WARN, "", "异常信息： " + e.fillInStackTrace().toString());
    }

    public void errorScreen() {
        try {
            androidDriver.context("NATIVE_APP");//先切换回app
            log.sendStepLog(StepType.WARN, "获取异常截图", UploadTools
                    .upload(((TakesScreenshot) androidDriver).getScreenshotAs(OutputType.FILE), "imageFiles"));
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "捕获截图失败", "");
        }
    }

    public String stepScreen(HandleDes handleDes) {
        handleDes.setStepDes("获取截图");
        handleDes.setDetail("");
        String url = "";
        try {
            androidDriver.context("NATIVE_APP");//先切换回app
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
        handleDes.setStepDes("强制等待");
        handleDes.setDetail("等待" + time + " ms");
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            handleDes.setE(e);
        }
    }

    public void runMonkey(HandleDes handleDes, JSONObject content, List<JSONObject> text) {
        handleDes.setStepDes("运行随机事件测试完毕");
        handleDes.setDetail("");
        String packageName = content.getString("packageName");
        int pctNum = content.getInteger("pctNum");
        if (!androidDriver.isAppInstalled(packageName)) {
            log.sendStepLog(StepType.ERROR, "应用未安装！", "设备未安装 " + packageName);
            handleDes.setE(new Exception("未安装应用"));
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
                    log.sendStepLog(StepType.INFO, "", "随机事件数：" + pctNum +
                            "<br>目标应用：" + packageName
                            + "<br>用户操作时延：" + finalSleepTime + " ms"
                            + "<br>轻触事件权重：" + finalTapEvent
                            + "<br>长按事件权重：" + finalLongPressEvent
                            + "<br>滑动事件权重：" + finalSwipeEvent
                            + "<br>多点触控事件权重：" + finalZoomEvent
                            + "<br>物理按键事件权重：" + finalSystemEvent
                            + "<br>系统事件权重：" + finalNavEvent
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
        log.sendStepLog(StepType.INFO, "", "测试目标包：" + packageName +
                (isOpenPackageListener ? "<br>应用包名监听器已开启..." : "") +
                (isOpenH5Listener ? "<br>H5页面监听器已开启..." : "") +
                (isOpenActivityListener ? "<br>黑名单Activity监听器..." : "") +
                (isOpenNetworkListener ? "<br>网络状态监听器已开启..." : ""));
        while (!randomThread.isDone() || (!packageListener.isDone()) || (!activityListener.isDone()) || (!networkListener.isDone()) || (!H5Listener.isDone())) {
        }
    }

    public void publicStep(HandleDes handleDes, String name, JSONArray stepArray) {
        handleDes.setStepDes("执行公共步骤 " + name);
        handleDes.setDetail("");
        log.sendStepLog(StepType.WARN, "公共步骤「" + name + "」开始执行", "");
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
        log.sendStepLog(StepType.WARN, "公共步骤「" + name + "」执行完毕", "");
    }

    public WebElement findEle(String selector, String pathValue) {
        WebElement we = null;
        switch (selector) {
            case "id":
                we = androidDriver.findElementById(pathValue);
                break;
            case "name":
                we = androidDriver.findElementByName(pathValue);
                break;
            case "xpath":
                we = androidDriver.findElementByXPath(pathValue);
                break;
            case "cssSelector":
                we = androidDriver.findElementByCssSelector(pathValue);
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
            default:
                log.sendStepLog(StepType.ERROR, "查找控件元素失败", "这个控件元素类型: " + selector + " 不存在!!!");
                break;
        }
        return we;
    }

    public void stepHold(HandleDes handleDes, int time) {
        handleDes.setStepDes("设置全局步骤间隔");
        handleDes.setDetail("间隔" + time + " ms");
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
                String actual = replaceTrans(step.getString("text"));
                String expect = replaceTrans(step.getString("content"));
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
                        log.sendStepLog(StepType.PASS, stepDes + "异常！已忽略...", detail);
                    } else {
                        ConditionEnum conditionType =
                                SonicEnum.valueToEnum(ConditionEnum.class, stepJson.getInteger("conditionType"));
                        String des = "「%s」步骤「%s」异常".formatted(conditionType.getName(), stepDes);
                        log.sendStepLog(StepType.ERROR, des, detail);
                        exceptionLog(e);
                    }
                    break;
                case ErrorType.WARNING:
                    log.sendStepLog(StepType.WARN, stepDes + "异常！", detail);
                    setResultDetailStatus(ResultDetailStatus.WARN);
                    errorScreen();
                    exceptionLog(e);
                    break;
                case ErrorType.SHUTDOWN:
                    log.sendStepLog(StepType.ERROR, stepDes + "异常！", detail);
                    setResultDetailStatus(ResultDetailStatus.FAIL);
                    errorScreen();
                    exceptionLog(e);
                    throw e;
            }
            // 非条件步骤清除异常对象
            if (stepJson.getInteger("conditionType").equals(0)) {
                handleDes.clear();
            }
        } else {
            log.sendStepLog(StepType.PASS, stepDes, detail);
        }
    }
}
