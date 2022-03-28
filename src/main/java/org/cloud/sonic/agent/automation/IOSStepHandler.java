package org.cloud.sonic.agent.automation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.enums.ConditionEnum;
import org.cloud.sonic.agent.enums.SonicEnum;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.StepHandlers;
import org.cloud.sonic.agent.tools.SpringTool;
import org.cloud.sonic.agent.tools.cv.AKAZEFinder;
import org.cloud.sonic.agent.tools.cv.SIFTFinder;
import org.cloud.sonic.agent.tools.cv.SimilarityChecker;
import org.cloud.sonic.agent.tools.cv.TemMatcher;
import org.cloud.sonic.agent.common.interfaces.ErrorType;
import org.cloud.sonic.agent.common.interfaces.ResultDetailStatus;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.maps.IOSProcessMap;
import org.cloud.sonic.agent.common.maps.IOSInfoMap;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tools.file.UploadTools;
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
 * @des iOS自动化处理类
 * @date 2021/8/16 20:10
 */
public class IOSStepHandler {
    public LogUtil log = new LogUtil();
    private IOSDriver iosDriver;
    private JSONObject globalParams = new JSONObject();
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

    public void startIOSDriver(String udId, int wdaPort) throws InterruptedException, IOException {
        this.udId = udId;
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(MobileCapabilityType.PLATFORM_NAME, Platform.IOS);
        desiredCapabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, AutomationName.IOS_XCUI_TEST);
        desiredCapabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, 3600);
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
            iosDriver = new IOSDriver(AppiumServer.service.getUrl(), desiredCapabilities);
            iosDriver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
            iosDriver.setSetting(Setting.MJPEG_SERVER_FRAMERATE, 50);
            iosDriver.setSetting(Setting.MJPEG_SCALING_FACTOR, 50);
            iosDriver.setSetting(Setting.MJPEG_SERVER_SCREENSHOT_QUALITY, 10);
            iosDriver.setSetting("snapshotMaxDepth", 30);
            log.sendStepLog(StepType.PASS, "连接设备驱动成功", "");
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "连接设备驱动失败！", "");
            //测试标记为失败
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
                log.sendStepLog(StepType.PASS, "退出连接设备", "");
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
            log.sendStepLog(StepType.WARN, "测试终止异常！请检查设备连接状态", "");
            //测试异常
            setResultDetailStatus(ResultDetailStatus.WARN);
            e.printStackTrace();
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

    //判断有无出错
    public int getStatus() {
        return status;
    }

    //调试每次重设状态
    public void resetResultDetailStatus() {
        status = 1;
    }

    public boolean getBattery() {
        double battery = iosDriver.getBatteryInfo().getLevel();
        if (battery <= 0.1) {
            log.sendStepLog(StepType.ERROR, "设备电量过低!", "跳过本次测试...");
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
        if (!recordDir.exists()) {//判断文件目录是否存在
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
        handleDes.setStepDes("安装应用");
        handleDes.setDetail("App安装路径： " + path);
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
        handleDes.setStepDes("卸载应用");
        handleDes.setDetail("App包名： " + appPackage);
        try {
            iosDriver.removeApp(appPackage);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void terminate(HandleDes handleDes, String packageName) {
        handleDes.setStepDes("终止应用");
        handleDes.setDetail("应用包名： " + packageName);
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
        handleDes.setStepDes("后台运行应用");
        handleDes.setDetail("后台运行App " + time + " ms");
        try {
            iosDriver.runAppInBackground(Duration.ofMillis(time));
        } catch (Exception e) {
            handleDes.setE(e);
        }
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
        handleDes.setDetail("");
        try {
            iosDriver.lockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void unLock(HandleDes handleDes) {
        handleDes.setStepDes("解锁屏幕");
        handleDes.setDetail("");
        try {
            iosDriver.unlockDevice();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void asserts(HandleDes handleDes, String actual, String expect, String type) {
        handleDes.setDetail("真实值： " + actual + " 期望值： " + expect);
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
            iosDriver.hideKeyboard();
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
            TouchAction ta = new TouchAction(iosDriver);
            ta.longPress(PointOption.point(x, y)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(time))).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void keyCode(HandleDes handleDes, String key) {
        handleDes.setStepDes("按系统按键" + key + "键");
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
        String detail = "坐标" + des1 + "( " + x1 + ", " + y1 + " )移动到坐标" + des2 + "( " + x2 + ", " + y2 + " ),同时坐标" + des3 + "( " + x3 + ", " + y3 + " )移动到坐标" + des4 + "( " + x4 + ", " + y4 + " )";
        handleDes.setStepDes("双指操作");
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
        handleDes.setStepDes("点击" + des);
        handleDes.setDetail("点击坐标(" + x + "," + y + ")");
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
        handleDes.setStepDes("滑动拖拽" + des1 + "到" + des2);
        handleDes.setDetail("拖动坐标(" + x1 + "," + y1 + ")到(" + x2 + "," + y2 + ")");
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
        handleDes.setStepDes("滑动拖拽" + des + "到" + des2);
        handleDes.setDetail("拖动坐标(" + x1 + "," + y1 + ")到(" + x2 + "," + y2 + ")");
        try {
            TouchAction ta = new TouchAction(iosDriver);
            ta.press(PointOption.point(x1, y1)).waitAction(WaitOptions.waitOptions(Duration.ofMillis(300))).moveTo(PointOption.point(x2, y2)).release().perform();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void longPress(HandleDes handleDes, String des, String selector, String pathValue, int time) {
        handleDes.setStepDes("长按" + des);
        handleDes.setDetail("长按控件元素" + time + "毫秒 ");
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
        String title = iosDriver.getTitle();
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
                TouchAction ta = new TouchAction(iosDriver);
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

    public void exceptionLog(Throwable e) {
        log.sendStepLog(StepType.WARN, "", "异常信息： " + e.fillInStackTrace().toString());
    }

    public void errorScreen() {
        try {
            iosDriver.context("NATIVE_APP");//先切换回app
            log.sendStepLog(StepType.WARN, "获取异常截图", UploadTools
                    .upload(((TakesScreenshot) iosDriver).getScreenshotAs(OutputType.FILE), "imageFiles"));
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "捕获截图失败", "");
        }
    }

    public String stepScreen(HandleDes handleDes) {
        handleDes.setStepDes("获取截图");
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
        handleDes.setStepDes("强制等待");
        handleDes.setDetail("等待" + time + " ms");
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            handleDes.setE(e);
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
            if (stepJson.getInteger("conditionType").equals(ConditionEnum.NONE.getValue())) {
                handleDes.clear();
            }
        } else {
            log.sendStepLog(StepType.PASS, stepDes, detail);
        }
    }
}