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
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.cloud.sonic.agent.common.enums.AndroidKey;
import org.cloud.sonic.agent.common.enums.ConditionEnum;
import org.cloud.sonic.agent.common.enums.SonicEnum;
import org.cloud.sonic.agent.common.interfaces.ErrorType;
import org.cloud.sonic.agent.common.interfaces.ResultDetailStatus;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.maps.AndroidThreadMap;
import org.cloud.sonic.agent.common.models.HandleDes;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.StepHandlers;
import org.cloud.sonic.agent.tests.script.GroovyScript;
import org.cloud.sonic.agent.tests.script.GroovyScriptImpl;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.SpringTool;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.cloud.sonic.driver.android.AndroidDriver;
import org.cloud.sonic.driver.android.enmus.AndroidSelector;
import org.cloud.sonic.driver.android.service.AndroidElement;
import org.cloud.sonic.driver.common.models.WindowSize;
import org.cloud.sonic.driver.common.tool.PocoXYTransformer;
import org.cloud.sonic.driver.common.tool.SonicRespException;
import org.cloud.sonic.driver.poco.PocoDriver;
import org.cloud.sonic.driver.poco.enums.PocoEngine;
import org.cloud.sonic.driver.poco.models.PocoElement;
import org.cloud.sonic.vision.cv.AKAZEFinder;
import org.cloud.sonic.vision.cv.SIFTFinder;
import org.cloud.sonic.vision.cv.SimilarityChecker;
import org.cloud.sonic.vision.cv.TemMatcher;
import org.cloud.sonic.vision.models.FindResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;

import javax.imageio.stream.FileImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.Future;

import static org.testng.Assert.*;

/**
 * @author ZhouYiXun
 * @des 安卓自动化处理类
 * @date 2021/8/16 20:10
 */
public class AndroidStepHandler {
    public LogUtil log = new LogUtil();
    private AndroidDriver androidDriver;
    private ChromeDriver chromeDriver;
    private PocoDriver pocoDriver = null;
    private JSONObject globalParams = new JSONObject();
    private IDevice iDevice;
    private int status = ResultDetailStatus.PASS;
    private double[] screenOffset = {0,0};

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
     * @param iDevice
     * @return void
     * @author ZhouYiXun
     * @des 启动安卓驱动，连接设备
     * @date 2021/8/16 20:01
     */
    public void startAndroidDriver(IDevice iDevice, int uiaPort) throws Exception {
        this.iDevice = iDevice;
        try {
            androidDriver = new AndroidDriver("http://127.0.0.1:" + uiaPort);
            androidDriver.disableLog();
            log.sendStepLog(StepType.PASS, "连接设备驱动成功", "");
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "连接设备驱动失败！", "");
            setResultDetailStatus(ResultDetailStatus.FAIL);
            throw e;
        }
        log.androidInfo("Android", iDevice.getProperty(IDevice.PROP_BUILD_VERSION),
                iDevice.getSerialNumber(), iDevice.getProperty(IDevice.PROP_DEVICE_MANUFACTURER),
                iDevice.getProperty(IDevice.PROP_DEVICE_MODEL),
                AndroidDeviceBridgeTool.getScreenSize(iDevice));
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des 关闭driver
     * @date 2021/8/16 20:21
     */
    public void closeAndroidDriver() {
        try {
            if (chromeDriver != null) {
                chromeDriver.quit();
            }
            if (pocoDriver != null) {
                pocoDriver.closeDriver();
                pocoDriver = null;
            }
            if (androidDriver != null) {
                androidDriver.closeDriver();
                log.sendStepLog(StepType.PASS, "退出连接设备", "");
            }
        } catch (Exception e) {
            log.sendStepLog(StepType.WARN, "测试终止异常！请检查设备连接状态", "");
            setResultDetailStatus(ResultDetailStatus.WARN);
            e.printStackTrace();
        } finally {
            Thread s = AndroidThreadMap.getMap().get(String.format("%s-uia-thread", iDevice.getSerialNumber()));
            if (s != null) {
                s.interrupt();
            }
            AndroidDeviceBridgeTool.clearWebView(iDevice);
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
        return iDevice.getSerialNumber();
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
        String battery = AndroidDeviceBridgeTool.executeCommand(iDevice, "dumpsys battery");
        String realLevel = battery.substring(battery.indexOf("level")).trim();
        int level = BytesTool.getInt(realLevel.substring(7, realLevel.indexOf("\n")));
        if (level <= 10) {
            log.sendStepLog(StepType.ERROR, "设备电量过低!", "跳过本次测试...");
            return true;
        } else {
            return false;
        }
    }

    private int xpathId = 1;

    /**
     * @return com.alibaba.fastjson.JSONArray
     * @author ZhouYiXun
     * @des 获取页面xpath信息
     * @date 2021/8/16 23:16
     */
    public JSONArray getResource() {
        try {
            JSONArray elementList = new JSONArray();
            Document doc = Jsoup.parse(androidDriver.getPageSource());
            String xpath = "/hierarchy";
            elementList.addAll(getChildren(doc.body().children().get(0).children(), xpath));
            xpathId = 1;
            return elementList;
        } catch (SonicRespException e) {
            e.printStackTrace();
            return null;
        }
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
            int tagCount = 0;
            int siblingIndex = 0;
            String indexXpath;
            for (int j = 0; j < elements.size(); j++) {
                if (elements.get(j).attr("class").equals(elements.get(i).attr("class"))) {
                    tagCount++;
                }
                if (i == j) {
                    siblingIndex = tagCount;
                }
            }
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

    public void install(HandleDes handleDes, String path) {
        handleDes.setStepDes("安装应用");
        path = TextHandler.replaceTrans(path, globalParams);
        handleDes.setDetail("App安装路径： " + path);
        File localFile = new File(path);
        try {
            if (path.contains("http")) {
                localFile = DownloadTool.download(path);
            }
            log.sendStepLog(StepType.INFO, "", "开始安装App，请稍后...");
            AndroidDeviceBridgeTool.install(iDevice, localFile.getAbsolutePath());
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void uninstall(HandleDes handleDes, String appPackage) {
        handleDes.setStepDes("卸载应用");
        appPackage = TextHandler.replaceTrans(appPackage, globalParams);
        handleDes.setDetail("App包名： " + appPackage);
        try {
            AndroidDeviceBridgeTool.uninstall(iDevice, appPackage);
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
        packageName = TextHandler.replaceTrans(packageName, globalParams);
        handleDes.setDetail("应用包名： " + packageName);
        try {
            AndroidDeviceBridgeTool.forceStop(iDevice, packageName);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void appReset(HandleDes handleDes, String bundleId) {
        handleDes.setStepDes("清空App内存缓存");
        bundleId = TextHandler.replaceTrans(bundleId, globalParams);
        handleDes.setDetail("清空 " + bundleId);
        if (iDevice != null) {
            AndroidDeviceBridgeTool.executeCommand(iDevice, "pm clear " + bundleId);
        }
    }

    public void openApp(HandleDes handleDes, String appPackage) {
        handleDes.setStepDes("打开应用");
        appPackage = TextHandler.replaceTrans(appPackage, globalParams);
        handleDes.setDetail("App包名： " + appPackage);
        try {
            AndroidDeviceBridgeTool.activateApp(iDevice, appPackage);
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
            AndroidDeviceBridgeTool.screen(iDevice, s);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void lock(HandleDes handleDes) {
        handleDes.setStepDes("锁定屏幕");
        handleDes.setDetail("");
        try {
            AndroidDeviceBridgeTool.pressKey(iDevice, 26);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void unLock(HandleDes handleDes) {
        handleDes.setStepDes("解锁屏幕");
        handleDes.setDetail("");
        try {
            AndroidDeviceBridgeTool.pressKey(iDevice, 26);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void airPlaneMode(HandleDes handleDes, boolean enable) {
        handleDes.setStepDes("切换飞行模式");
        handleDes.setDetail(enable ? "打开" : "关闭");
        try {
            if (enable) {
                AndroidDeviceBridgeTool.executeCommand(iDevice, "settings put global airplane_mode_on 1");
            } else {
                AndroidDeviceBridgeTool.executeCommand(iDevice, "settings put global airplane_mode_on 0");
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void wifiMode(HandleDes handleDes, boolean enable) {
        handleDes.setStepDes("开关WIFI");
        handleDes.setDetail(enable ? "打开" : "关闭");
        try {
            if (enable) {
                AndroidDeviceBridgeTool.executeCommand(iDevice, "svc wifi enable");
            } else {
                AndroidDeviceBridgeTool.executeCommand(iDevice, "svc wifi disable");
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void locationMode(HandleDes handleDes, boolean enable) {
        handleDes.setStepDes("切换位置服务");
        handleDes.setDetail("");
        try {
            if (enable) {
                AndroidDeviceBridgeTool.executeCommand(iDevice, "settings put secure location_providers_allowed +gps");
            } else {
                AndroidDeviceBridgeTool.executeCommand(iDevice, "settings put secure location_providers_allowed -gps");
            }
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

    public void toWebView(HandleDes handleDes, String packageName, String process) {
        handleDes.setStepDes("切换到" + packageName + " WebView");
        handleDes.setDetail("AndroidProcess: " + process);
        try {
            if (chromeDriver != null) {
                chromeDriver.quit();
            }
            ChromeDriverService chromeDriverService = new ChromeDriverService.Builder().usingAnyFreePort()
                    .usingDriverExecutable(AndroidDeviceBridgeTool.getChromeDriver(iDevice, packageName)).build();
            ChromeOptions chromeOptions = new ChromeOptions();
            chromeOptions.setExperimentalOption("androidDeviceSerial", iDevice.getSerialNumber());
            chromeOptions.setExperimentalOption("androidPackage", packageName);
            if (process != null && process.length() > 0) {
                chromeOptions.setExperimentalOption("androidProcess", process);
            }
            chromeOptions.setExperimentalOption("androidUseRunningApp", true);
            chromeDriver = new ChromeDriver(chromeDriverService, chromeOptions);
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
        keys = TextHandler.replaceTrans(keys, globalParams);
        handleDes.setStepDes("对" + des + "输入内容");
        handleDes.setDetail("对" + selector + ": " + pathValue + " 输入: " + keys);
        try {
            findEle(selector, pathValue).sendKeys(keys);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void sendKeysByActions(HandleDes handleDes, String des, String selector, String pathValue, String keys) {
        keys = TextHandler.replaceTrans(keys, globalParams);
        handleDes.setStepDes("对" + des + "输入内容");
        handleDes.setDetail("对" + selector + ": " + pathValue + " 输入: " + keys);
        try {
            AndroidElement androidElement = findEle(selector, pathValue);
            if (androidElement != null) {
                androidElement.click();
                androidDriver.sendKeys(keys);
            }
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
                expect = TextHandler.replaceTrans(expect, globalParams);
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
        double x = Double.parseDouble(xy.substring(0, xy.indexOf(",")));
        double y = Double.parseDouble(xy.substring(xy.indexOf(",") + 1));
        int[] point = computedPoint(x, y);
        handleDes.setStepDes("长按" + des);
        handleDes.setDetail("长按坐标" + time + "毫秒 (" + point[0] + "," + point[1] + ")");
        try {
            AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", point[0], point[1], point[0], point[1], time));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void keyCode(HandleDes handleDes, String key) {
        keyCode(handleDes, AndroidKey.valueOf(key).getCode());
    }

    public void keyCode(HandleDes handleDes, int key) {
        handleDes.setStepDes("按系统按键" + key + "键");
        handleDes.setDetail("");
        try {
            if (iDevice != null) {
                AndroidDeviceBridgeTool.pressKey(iDevice, key);
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void tap(HandleDes handleDes, String des, String xy) {
        double x = Double.parseDouble(xy.substring(0, xy.indexOf(",")));
        double y = Double.parseDouble(xy.substring(xy.indexOf(",") + 1));
        int[] point = computedPoint(x, y);
        handleDes.setStepDes("点击" + des);
        handleDes.setDetail("点击坐标(" + point[0] + "," + point[1] + ")");
        try {
            AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input tap %d %d", point[0], point[1]));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void swipePoint(HandleDes handleDes, String des1, String xy1, String des2, String xy2) {
        double x1 = Double.parseDouble(xy1.substring(0, xy1.indexOf(",")));
        double y1 = Double.parseDouble(xy1.substring(xy1.indexOf(",") + 1));
        int[] point1 = computedPoint(x1, y1);
        double x2 = Double.parseDouble(xy2.substring(0, xy2.indexOf(",")));
        double y2 = Double.parseDouble(xy2.substring(xy2.indexOf(",") + 1));
        int[] point2 = computedPoint(x2, y2);
        handleDes.setStepDes("滑动拖拽" + des1 + "到" + des2);
        handleDes.setDetail("拖动坐标(" + point1[0] + "," + point1[1] + ")到(" + point2[0] + "," + point2[1] + ")");
        try {
            AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", point1[0], point1[1], point2[0], point2[1], 300));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void swipe(HandleDes handleDes, String des, String selector, String pathValue, String des2, String selector2, String pathValue2) {
        try {
            AndroidElement webElement = findEle(selector, pathValue);
            AndroidElement webElement2 = findEle(selector2, pathValue2);
            int x1 = webElement.getRect().getX();
            int y1 = webElement.getRect().getY();
            int x2 = webElement2.getRect().getX();
            int y2 = webElement2.getRect().getY();
            handleDes.setStepDes("滑动拖拽" + des + "到" + des2);
            handleDes.setDetail("拖动坐标(" + x1 + "," + y1 + ")到(" + x2 + "," + y2 + ")");
            AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", x1, y1, x2, y2, 300));
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void longPress(HandleDes handleDes, String des, String selector, String pathValue, int time) {
        handleDes.setStepDes("长按" + des);
        handleDes.setDetail("长按控件元素" + time + "毫秒 ");
        try {
            AndroidElement webElement = findEle(selector, pathValue);
            int x = webElement.getRect().getX();
            int y = webElement.getRect().getY();
            AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", x, y, x, y, time));
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
            AndroidElement w = findEle(selector, pathValue);
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
        String title = chromeDriver.getTitle();
        handleDes.setStepDes("验证网页标题");
        handleDes.setDetail("标题：" + title + "，期望值：" + expect);
        try {
            assertEquals(title, expect);
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public void getActivity(HandleDes handleDes, String expect) {
        expect = TextHandler.replaceTrans(expect, globalParams);
        String currentActivity = AndroidDeviceBridgeTool.getCurrentActivity(iDevice);
        handleDes.setStepDes("验证当前Activity");
        handleDes.setDetail("activity：" + currentActivity + "，期望值：" + expect);
        try {
            assertEquals(currentActivity, expect);
        } catch (AssertionError e) {
            handleDes.setE(e);
        }
    }

    public void getElementAttr(HandleDes handleDes, String des, String selector, String pathValue, String attr, String expect) {
        handleDes.setStepDes("验证控件 " + des + " 属性");
        handleDes.setDetail("属性：" + attr + "，期望值：" + expect);
        try {
            String attrValue = findEle(selector, pathValue).getAttribute(attr);
            log.sendStepLog(StepType.INFO, "", attr + " 属性获取结果: " + attrValue);
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
            findResult = siftFinder.getSIFTFindResult(file, getScreenToLocal(), true);
        } catch (Exception e) {
            log.sendStepLog(StepType.WARN, "SIFT图像算法出错，切换算法中...",
                    "");
        }
        if (findResult != null) {
            String url = UploadTools.upload(findResult.getFile(), "imageFiles");
            log.sendStepLog(StepType.INFO, "图片定位到坐标：(" + findResult.getX() + "," + findResult.getY() + ")  耗时：" + findResult.getTime() + " ms",
                    url);
        } else {
            log.sendStepLog(StepType.INFO, "SIFT算法无法定位图片，切换AKAZE算法中...",
                    "");
            try {
                AKAZEFinder akazeFinder = new AKAZEFinder();
                findResult = akazeFinder.getAKAZEFindResult(file, getScreenToLocal(), true);
            } catch (Exception e) {
                log.sendStepLog(StepType.WARN, "AKAZE图像算法出错，切换模版匹配算法中...",
                        "");
            }
            if (findResult != null) {
                String url = UploadTools.upload(findResult.getFile(), "imageFiles");
                log.sendStepLog(StepType.INFO, "图片定位到坐标：(" + findResult.getX() + "," + findResult.getY() + ")  耗时：" + findResult.getTime() + " ms",
                        url);
            } else {
                log.sendStepLog(StepType.INFO, "AKAZE算法无法定位图片，切换模版匹配算法中...",
                        "");
                try {
                    TemMatcher temMatcher = new TemMatcher();
                    findResult = temMatcher.getTemMatchResult(file, getScreenToLocal(), true);
                } catch (Exception e) {
                    log.sendStepLog(StepType.WARN, "模版匹配算法出错",
                            "");
                }
                if (findResult != null) {
                    String url = UploadTools.upload(findResult.getFile(), "imageFiles");
                    log.sendStepLog(StepType.INFO, "图片定位到坐标：(" + findResult.getX() + "," + findResult.getY() + ")  耗时：" + findResult.getTime() + " ms",
                            url);
                } else {
                    handleDes.setE(new Exception("图片定位失败！"));
                }
            }
        }
        if (findResult != null) {
            try {
                AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input tap %d %d", findResult.getX(), findResult.getY()));
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
        Set<String> handle = chromeDriver.getWindowHandles();
        String ha;
        for (int i = 1; i <= handle.size(); i++) {
            ha = (String) handle.toArray()[handle.size() - i];
            try {
                chromeDriver.switchTo().window(ha);
            } catch (Exception e) {
            }
            if (chromeDriver.getTitle().equals(titleName)) {
                handleDes.setDetail("切换到Handle:" + ha);
                log.sendStepLog(StepType.INFO, "页面标题:" + chromeDriver.getTitle(), "");
                break;
            }
        }
    }

    public File getScreenToLocal() {
        File folder = new File("test-output");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File output = new File(folder + File.separator + log.udId + Calendar.getInstance().getTimeInMillis() + ".png");
        try {
            byte[] bt = androidDriver.screenshot();
            FileImageOutputStream imageOutput = new FileImageOutputStream(output);
            imageOutput.write(bt, 0, bt.length);
            imageOutput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    public void checkImage(HandleDes handleDes, String des, String pathValue, double matchThreshold) throws Exception {
        try {
            log.sendStepLog(StepType.INFO, "开始检测" + des + "兼容", "检测与当前设备截图相似度，期望相似度为" + matchThreshold + "%");
            File file = null;
            if (pathValue.startsWith("http")) {
                file = DownloadTool.download(pathValue);
            }
            SimilarityChecker similarityChecker = new SimilarityChecker();
            double score = similarityChecker.getSimilarMSSIMScore(file, getScreenToLocal(), true);
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
            File folder = new File("test-output");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            byte[] bt = androidDriver.screenshot();
            File output = new File(folder + File.separator + UUID.randomUUID() + ".png");
            FileImageOutputStream imageOutput = new FileImageOutputStream(output);
            imageOutput.write(bt, 0, bt.length);
            imageOutput.close();
            log.sendStepLog(StepType.WARN, "获取异常截图", UploadTools
                    .upload(output, "imageFiles"));
        } catch (Exception e) {
            log.sendStepLog(StepType.ERROR, "捕获截图失败", "");
        }
    }

    public String stepScreen(HandleDes handleDes) {
        handleDes.setStepDes("获取截图");
        handleDes.setDetail("");
        String url = "";
        try {
            File folder = new File("test-output");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File output = new File(folder + File.separator + iDevice.getSerialNumber() + Calendar.getInstance().getTimeInMillis() + ".png");
            byte[] bt = androidDriver.screenshot();
            FileImageOutputStream imageOutput = new FileImageOutputStream(output);
            imageOutput.write(bt, 0, bt.length);
            imageOutput.close();
            url = UploadTools.upload(output, "imageFiles");
            handleDes.setDetail(url);
        } catch (Exception e) {
            handleDes.setE(e);
        }
        return url;
    }

    public Set<String> getWebView() {
        Set<String> webView = new HashSet<>();
        List<JSONObject> result = AndroidDeviceBridgeTool.getWebView(iDevice);
        if (result.size() > 0) {
            for (JSONObject j : result) {
                webView.add(j.getString("package"));
            }
        }
        AndroidDeviceBridgeTool.clearWebView(iDevice);
        return webView;
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
        if (!AndroidDeviceBridgeTool.executeCommand(iDevice, "pm list package").contains(packageName)) {
            log.sendStepLog(StepType.ERROR, "应用未安装！", "设备未安装 " + packageName);
            handleDes.setE(new Exception("未安装应用"));
            return;
        }
        JSONArray options = content.getJSONArray("options");
        WindowSize windowSize = null;
        try {
            windowSize = androidDriver.getWindowSize();
        } catch (SonicRespException e) {
            e.printStackTrace();
        }
        int width = windowSize.getWidth();
        int height = windowSize.getHeight();
        int sleepTime = 50;
        int systemEvent = 0;
        int tapEvent = 0;
        int longPressEvent = 0;
        int swipeEvent = 0;
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
        int finalSystemEvent = systemEvent;
        int finalNavEvent = navEvent;
        Future<?> randomThread = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
                    log.sendStepLog(StepType.INFO, "", "随机事件数：" + pctNum +
                            "<br>目标应用：" + packageName
                            + "<br>用户操作时延：" + finalSleepTime + " ms"
                            + "<br>轻触事件权重：" + finalTapEvent
                            + "<br>长按事件权重：" + finalLongPressEvent
                            + "<br>滑动事件权重：" + finalSwipeEvent
                            + "<br>物理按键事件权重：" + finalSystemEvent
                            + "<br>系统事件权重：" + finalNavEvent
                    );
                    openApp(new HandleDes(), packageName);
                    int totalCount = finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent + finalNavEvent;
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
                                AndroidDeviceBridgeTool.pressKey(iDevice, AndroidKey.valueOf(keyType).getCode());
                            }
                            if (random >= finalSystemEvent && random < (finalSystemEvent + finalTapEvent)) {
                                int x = new Random().nextInt(width - 60) + 60;
                                int y = new Random().nextInt(height - 60) + 60;
                                AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input tap %d %d", x, y));
                            }
                            if (random >= (finalSystemEvent + finalTapEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent)) {
                                int x = new Random().nextInt(width - 60) + 60;
                                int y = new Random().nextInt(height - 60) + 60;
                                AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", x, y, x, y, (new Random().nextInt(3) + 1) * 1000));
                            }
                            if (random >= (finalSystemEvent + finalTapEvent + finalLongPressEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent)) {
                                int x1 = new Random().nextInt(width - 60) + 60;
                                int y1 = new Random().nextInt(height - 80) + 80;
                                int x2 = new Random().nextInt(width - 60) + 60;
                                int y2 = new Random().nextInt(height - 80) + 80;
                                AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", x1, y1, x2, y2, 200));
                            }
                            if (random >= (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent + finalNavEvent)) {
                                int a = new Random().nextInt(1);
                                if (a == 1) {
                                    AndroidDeviceBridgeTool.executeCommand(iDevice, "svc wifi enable");
                                } else {
                                    AndroidDeviceBridgeTool.executeCommand(iDevice, "svc wifi disable");
                                }
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
                                if (androidDriver.findElementList(AndroidSelector.CLASS_NAME, "android.webkit.WebView").size() > 0) {
                                    h5Time++;
                                    AndroidDeviceBridgeTool.executeCommand(iDevice, "input keyevent 4");
                                } else {
                                    h5Time = 0;
                                }
                                if (h5Time >= 12) {
                                    AndroidDeviceBridgeTool.forceStop(iDevice, packageName);
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
                                if (!AndroidDeviceBridgeTool.getCurrentActivity(iDevice).contains(packageName)) {
                                    AndroidDeviceBridgeTool.activateApp(iDevice, packageName);
                                }
                                waitTime++;
                            }
                            AndroidDeviceBridgeTool.activateApp(iDevice, packageName);
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
                            if (blackList.contains(AndroidDeviceBridgeTool.getCurrentActivity(iDevice))) {
                                AndroidDeviceBridgeTool.executeCommand(iDevice, "input keyevent 4");
                            } else continue;
                            try {
                                Thread.sleep(8000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (blackList.contains(AndroidDeviceBridgeTool.getCurrentActivity(iDevice))) {
                                AndroidDeviceBridgeTool.forceStop(iDevice, packageName);
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
                            AndroidDeviceBridgeTool.executeCommand(iDevice, "settings put global airplane_mode_on 0");
                            try {
                                Thread.sleep(8000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            AndroidDeviceBridgeTool.executeCommand(iDevice, "svc wifi enable");
                        }
                    }
                }
        );
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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

    public void startPocoDriver(HandleDes handleDes, String engine, int port) {
        handleDes.setStepDes("启动PocoDriver");
        handleDes.setDetail("");
        int newPort = PortTool.getPort();
        AndroidDeviceBridgeTool.forward(iDevice, newPort, port);
        pocoDriver = new PocoDriver(PocoEngine.valueOf(engine), newPort);
    }

    public PocoElement findPocoEle(String expression) throws SonicRespException {
        return pocoDriver.findElement(expression);
    }

    public void isExistPocoEle(HandleDes handleDes, String des, String value, boolean expect) {
        handleDes.setStepDes("判断控件 " + des + " 是否存在");
        handleDes.setDetail("期望值：" + (expect ? "存在" : "不存在"));
        boolean hasEle = false;
        try {
            PocoElement w = findPocoEle(value);
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

    public void pocoClick(HandleDes handleDes, String des, String value) {
        handleDes.setStepDes("点击" + des);
        handleDes.setDetail("");
        try {
            PocoElement w = findPocoEle(value);
            if (w != null) {
                  List<Float> pos = w.getPayload().getPos();
                  int[] realCoordinates = getTheRealCoordinatesOfPoco(pos.get(0), pos.get(1));
                  AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input tap %d %d", realCoordinates[0], realCoordinates[1]));
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void pocoLongPress(HandleDes handleDes, String des, String value, int time) {
        handleDes.setStepDes("长按" + des);
        handleDes.setDetail("");
        try {
            PocoElement w = findPocoEle(value);
            if (w != null) {
                List<Float> pos = w.getPayload().getPos();
                int[] realCoordinates = getTheRealCoordinatesOfPoco(pos.get(0), pos.get(1));
                AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", realCoordinates[0], realCoordinates[1], realCoordinates[0], realCoordinates[1], time));
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void pocoSwipe(HandleDes handleDes, String des, String value, String des2, String value2) {
        handleDes.setStepDes("滑动拖拽" + des + "到" + des2);
        handleDes.setDetail("");
        try {
            PocoElement w1 = findPocoEle(value);
            PocoElement w2 = findPocoEle(value2);
            if (w1 != null && w2 != null) {
                List<Float> pos1 = w1.getPayload().getPos();
                int[] realCoordinates1 = getTheRealCoordinatesOfPoco(pos1.get(0), pos1.get(1));

                List<Float> pos2 = w1.getPayload().getPos();
                int[] realCoordinate2 = getTheRealCoordinatesOfPoco(pos2.get(0), pos2.get(1));
                AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", realCoordinates1[0], realCoordinates1[1], realCoordinate2[0], realCoordinate2[1], 300));
            }
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void setOffset(int offsetWidth,int offsetHeight){
        this.screenOffset[0] = offsetWidth;
        this.screenOffset[1] = offsetHeight;
    }

    public int[] getTheRealCoordinatesOfPoco(double pocoX, double pocoY) {

        int screenOrientation = AndroidDeviceBridgeTool.getOrientation(iDevice);

        double[] normalizedInADBCoordinate = PocoXYTransformer.PocoTransformerVertical(pocoX, pocoY, 1.0, 1.0, screenOrientation*90);

        int[] pos = computedPoint(normalizedInADBCoordinate[0], normalizedInADBCoordinate[1]);
        // x
        pos[0] += this.screenOffset[0];
        // y
        pos[1] += this.screenOffset[1];

        return pos;
    }

    public void freezeSource(HandleDes handleDes) {
        handleDes.setStepDes("冻结控件树");
        handleDes.setDetail("");
        pocoDriver.freezeSource();
    }

    public void thawSource(HandleDes handleDes) {
        handleDes.setStepDes("解冻控件树");
        handleDes.setDetail("");
        pocoDriver.thawSource();
    }

    public void closePocoDriver(HandleDes handleDes) {
        handleDes.setStepDes("关闭PocoDriver");
        handleDes.setDetail("");
        if (pocoDriver != null) {
            pocoDriver.closeDriver();
            pocoDriver = null;
        }
    }

    public PocoDriver getPocoDriver() {
        return pocoDriver;
    }

    public WebElement findWebEle(String selector, String pathValue) {
        WebElement we = null;
        pathValue = TextHandler.replaceTrans(pathValue, globalParams);
        switch (selector) {
            case "id":
                we = chromeDriver.findElementById(pathValue);
                break;
            case "name":
                we = chromeDriver.findElementByName(pathValue);
                break;
            case "xpath":
                we = chromeDriver.findElementByXPath(pathValue);
                break;
            case "cssSelector":
                we = chromeDriver.findElementByCssSelector(pathValue);
                break;
            case "className":
                we = chromeDriver.findElementByClassName(pathValue);
                break;
            case "tagName":
                we = chromeDriver.findElementByTagName(pathValue);
                break;
            case "linkText":
                we = chromeDriver.findElementByLinkText(pathValue);
                break;
            case "partialLinkText":
                we = chromeDriver.findElementByPartialLinkText(pathValue);
                break;
            case "cssSelectorAndText":
                we = getWebElementByCssAndText(pathValue);
                break;
            default:
                log.sendStepLog(StepType.ERROR, "查找控件元素失败", "这个控件元素类型: " + selector + " 不存在!!!");
                break;
        }
        return we;
    }

    public AndroidElement findEle(String selector, String pathValue) throws SonicRespException {
        AndroidElement we = null;
        pathValue = TextHandler.replaceTrans(pathValue, globalParams);
        switch (selector) {
            case "id":
                we = androidDriver.findElement(AndroidSelector.Id, pathValue);
                break;
            case "accessibilityId":
                we = androidDriver.findElement(AndroidSelector.ACCESSIBILITY_ID, pathValue);
                break;
            case "xpath":
                we = androidDriver.findElement(AndroidSelector.XPATH, pathValue);
                break;
            case "className":
                we = androidDriver.findElement(AndroidSelector.CLASS_NAME, pathValue);
                break;
            case "androidUIAutomator":
                we = androidDriver.findElement(AndroidSelector.UIAUTOMATOR, pathValue);
                break;
            default:
                log.sendStepLog(StepType.ERROR, "查找控件元素失败", "这个控件元素类型: " + selector + " 不存在!!!");
                break;
        }
        return we;
    }

    public void setFindElementInterval(HandleDes handleDes, int retry, int interval) {
        handleDes.setStepDes("Set Global Find Element Interval");
        handleDes.setDetail(String.format("Retry count: %d, retry interval: %d ms", retry, interval));
        androidDriver.setDefaultFindElementInterval(retry, interval);
    }

    private WebElement getWebElementByCssAndText(String pathValue) {
        // 新增H5页面通过className+text定位控件元素
        // value格式：van-button--default,购物车
        WebElement element = null;
        List<String> values = new ArrayList<>(Arrays.asList(pathValue.split(",")));
        if (values.size() >= 2) {
            // findElementsByClassName在高版本的chromedriver有bug，只能用cssSelector才能找到控件元素
            List<WebElement> els = chromeDriver.findElements(By.cssSelector(values.get(0)));
            for (WebElement el : els) {
                if (el.getText().equals(values.get(1))) {
                    element = el;
                    break;
                }
            }
        }
        return element;
    }

    public void isExistWebViewEle(HandleDes handleDes, String des, String selector, String pathValue, boolean expect) {
        handleDes.setStepDes("判断控件 " + des + " 是否存在");
        handleDes.setDetail("期望值：" + (expect ? "存在" : "不存在"));
        boolean hasEle = false;
        try {
            WebElement w = findWebEle(selector, pathValue);
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

    public void getWebViewTextAndAssert(HandleDes handleDes, String des, String selector, String pathValue, String expect) {
        handleDes.setStepDes("获取" + des + "文本");
        handleDes.setDetail("获取" + selector + ":" + pathValue + "文本");
        try {
            String s = findWebEle(selector, pathValue).getText();
            log.sendStepLog(StepType.INFO, "", "文本获取结果: " + s);
            try {
                expect = TextHandler.replaceTrans(expect, globalParams);
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

    public void webViewClick(HandleDes handleDes, String des, String selector, String pathValue) {
        handleDes.setStepDes("点击" + des);
        handleDes.setDetail("点击" + selector + ": " + pathValue);
        try {
            findWebEle(selector, pathValue).click();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void webViewSendKeys(HandleDes handleDes, String des, String selector, String pathValue, String keys) {
        keys = TextHandler.replaceTrans(keys, globalParams);
        handleDes.setStepDes("对" + des + "输入内容");
        handleDes.setDetail("对" + selector + ": " + pathValue + " 输入: " + keys);
        try {
            findWebEle(selector, pathValue).sendKeys(keys);
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public void webViewClear(HandleDes handleDes, String des, String selector, String pathValue) {
        handleDes.setStepDes("清空" + des);
        handleDes.setDetail("清空" + selector + ": " + pathValue);
        try {
            findWebEle(selector, pathValue).clear();
        } catch (Exception e) {
            handleDes.setE(e);
        }
    }

    public String getWebViewText(HandleDes handleDes, String des, String selector, String pathValue) {
        String s = "";
        handleDes.setStepDes("获取" + des + "文本");
        handleDes.setDetail("获取" + selector + ":" + pathValue + "文本");
        try {
            s = findWebEle(selector, pathValue).getText();
            log.sendStepLog(StepType.INFO, "", "文本获取结果: " + s);
        } catch (Exception e) {
            handleDes.setE(e);
        }
        return s;
    }

    public void stepHold(HandleDes handleDes, int time) {
        handleDes.setStepDes("设置全局步骤间隔");
        handleDes.setDetail("间隔" + time + " ms");
        holdTime = time;
    }

    public void sendKeyForce(HandleDes handleDes, String text) {
        text = TextHandler.replaceTrans(text, globalParams);
        handleDes.setStepDes("键盘输入文本");
        handleDes.setDetail("键盘输入" + text);
        try {
            androidDriver.sendKeys(text);
        } catch (SonicRespException e) {
            handleDes.setE(e);
        }
    }

    private int holdTime = 0;

    private int[] computedPoint(double x, double y) {
        if (x <= 1 && y <= 1) {
            String size = AndroidDeviceBridgeTool.getScreenSize(iDevice);
            String[] winSize = size.split("x");
            x = BytesTool.getInt(winSize[0]) * x;
            y = BytesTool.getInt(winSize[1]) * y;
        }
        return new int[]{(int) x, (int) y};
    }

    public void runScript(HandleDes handleDes, String script, String type) {
        handleDes.setStepDes("Run Custom Scripts");
        handleDes.setDetail("Script: <br>" + script);
        try {
            switch (type) {
                case "Groovy":
                    GroovyScript groovyScript = new GroovyScriptImpl();
                    groovyScript.runAndroid(androidDriver, iDevice, globalParams, log, script);
                    break;
                case "Python":
                    File temp = new File("test-output" + File.separator + UUID.randomUUID() + ".py");
                    if (!temp.exists()) {
                        temp.createNewFile();
                        FileWriter fileWriter = new FileWriter(temp);
                        fileWriter.write(script);
                        fileWriter.close();
                    }
                    CommandLine cmdLine = new CommandLine(String.format("python %s", temp.getAbsolutePath()));
                    cmdLine.addArgument(androidDriver.getSessionId(), false);
                    cmdLine.addArgument(iDevice.getSerialNumber(), false);
                    cmdLine.addArgument(globalParams.toJSONString(), false);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
                    try {
                        DefaultExecutor executor = new DefaultExecutor();
                        executor.setStreamHandler(streamHandler);
                        int exit = executor.execute(cmdLine);
                        log.sendStepLog(StepType.INFO, "", "Run result: <br>" + outputStream);
                        Assert.assertEquals(exit, 0);
                    } catch (Exception e) {
                        handleDes.setE(e);
                    } finally {
                        outputStream.close();
                        streamHandler.stop();
                        temp.delete();
                    }
                    break;
            }
        } catch (Throwable e) {
            handleDes.setE(e);
        }
    }

    public void runStep(JSONObject stepJSON, HandleDes handleDes) throws Throwable {
        JSONObject step = stepJSON.getJSONObject("step");
        JSONArray eleList = step.getJSONArray("elements");
        Thread.sleep(holdTime);
        switch (step.getString("stepType")) {
            case "appReset":
                appReset(handleDes, step.getString("text"));
                break;
            case "stepHold":
                stepHold(handleDes, Integer.parseInt(step.getString("content")));
                break;
            case "toWebView":
                toWebView(handleDes, step.getString("content"), step.getString("text"));
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
                airPlaneMode(handleDes, step.getBoolean("content"));
                break;
            case "wifiMode":
                wifiMode(handleDes, step.getBoolean("content"));
                break;
            case "locationMode":
                locationMode(handleDes, step.getBoolean("content"));
                break;
            case "keyCode":
                keyCode(handleDes, step.getString("content"));
                break;
            case "keyCodeSelf":
                keyCode(handleDes, step.getInteger("content"));
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
            case "sendKeyForce":
                sendKeyForce(handleDes, step.getString("content"));
                break;
            case "monkey":
                runMonkey(handleDes, step.getJSONObject("content"), step.getJSONArray("text").toJavaList(JSONObject.class));
                break;
            case "publicStep":
                publicStep(handleDes, step.getString("content"), stepJSON.getJSONArray("pubSteps"));
                return;
            case "getWebViewText":
                getWebViewTextAndAssert(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
                break;
            case "isExistWebViewEle":
                isExistWebViewEle(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getBoolean("content"));
                break;
            case "webViewClear":
                webViewClear(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"));
                break;
            case "webViewSendKeys":
                webViewSendKeys(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
                break;
            case "webViewClick":
                webViewClick(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                        , eleList.getJSONObject(0).getString("eleValue"));
                break;
            case "getWebViewTextValue":
                globalParams.put(step.getString("content"), getWebViewText(handleDes, eleList.getJSONObject(0).getString("eleName")
                        , eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue")));
                break;
            case "findElementInterval":
                setFindElementInterval(handleDes, step.getInteger("content"), step.getInteger("text"));
                break;
            case "runScript":
                runScript(handleDes, step.getString("content"), step.getString("text"));
                break;
            case "startPocoDriver":
                startPocoDriver(handleDes, step.getString("content"), step.getInteger("text"));
                break;
            case "isExistPocoEle":
                isExistPocoEle(handleDes, eleList.getJSONObject(0).getString("eleName")
                        , eleList.getJSONObject(0).getString("eleValue"), step.getBoolean("content"));
                break;
            case "pocoClick":
                pocoClick(handleDes, eleList.getJSONObject(0).getString("eleName")
                        , eleList.getJSONObject(0).getString("eleValue"));
                break;
            case "pocoLongPress":
                pocoLongPress(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                        , Integer.parseInt(step.getString("content")));
                break;
            case "pocoSwipe":
                pocoSwipe(handleDes, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                        , eleList.getJSONObject(1).getString("eleName"), eleList.getJSONObject(1).getString("eleValue"));
                break;
            case "freezeSource":
                freezeSource(handleDes);
                break;
            case "thawSource":
                thawSource(handleDes);
                break;
            case "closePocoDriver":
                closePocoDriver(handleDes);
                break;
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
            if (stepJson.getInteger("conditionType").equals(0)) {
                handleDes.clear();
            }
        } else {
            log.sendStepLog(StepType.PASS, stepDes, detail);
        }
    }
}
