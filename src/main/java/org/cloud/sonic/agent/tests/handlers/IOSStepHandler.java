/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.bridge.ios.IOSDeviceThreadPool;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.enums.ConditionEnum;
import org.cloud.sonic.agent.common.enums.SonicEnum;
import org.cloud.sonic.agent.common.interfaces.ErrorType;
import org.cloud.sonic.agent.common.interfaces.ResultDetailStatus;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.maps.IOSProcessMap;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tests.RunStepThread;
import org.cloud.sonic.agent.tests.script.GroovyScriptImpl;
import org.cloud.sonic.agent.tests.script.PythonScriptImpl;
import org.cloud.sonic.agent.tests.script.ScriptRunner;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.SpringTool;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.cloud.sonic.driver.common.enums.PasteboardType;
import org.cloud.sonic.driver.common.models.BaseElement;
import org.cloud.sonic.driver.common.models.WindowSize;
import org.cloud.sonic.driver.common.tool.SonicRespException;
import org.cloud.sonic.driver.ios.IOSDriver;
import org.cloud.sonic.driver.ios.enums.IOSSelector;
import org.cloud.sonic.driver.ios.enums.SystemButton;
import org.cloud.sonic.driver.ios.service.IOSElement;
import org.cloud.sonic.driver.poco.PocoDriver;
import org.cloud.sonic.driver.poco.enums.PocoEngine;
import org.cloud.sonic.driver.poco.enums.PocoSelector;
import org.cloud.sonic.driver.poco.models.PocoElement;
import org.cloud.sonic.vision.cv.AKAZEFinder;
import org.cloud.sonic.vision.cv.SIFTFinder;
import org.cloud.sonic.vision.cv.SimilarityChecker;
import org.cloud.sonic.vision.cv.TemMatcher;
import org.cloud.sonic.vision.models.FindResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.springframework.util.CollectionUtils;

import javax.imageio.stream.FileImageOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Future;

import static org.testng.Assert.*;

/**
 * @author ZhouYiXun
 * @des iOS自动化处理类
 * @date 2021/8/16 20:10
 */
public class IOSStepHandler {
    public LogUtil log = new LogUtil();
    private IOSDriver iosDriver;
    private PocoDriver pocoDriver = null;
    private JSONObject globalParams = new JSONObject();
    private String udId = "";
    private int status = ResultDetailStatus.PASS;
    private int[] screenWindowPosition = {0, 0, 0, 0};
    private int pocoPort = 0;
    private int targetPort = 0;
    private String targetPackage = "";

    private static final int IOS_ELEMENT_TYPE = 1004;
    private static final int POCO_ELEMENT_TYPE = 1005;

    // 屏幕的宽度与高度信息
    private int screenWidth = 0;
    private int screenHeight = 0;

    public String getTargetPackage() {
        return targetPackage;
    }

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

    public void startIOSDriver(String udId, int wdaPort) throws Exception {
        this.udId = udId;
        int retry = 0;
        Exception out = null;
        while (retry <= 4) {
            try {
                iosDriver = new IOSDriver("http://127.0.0.1:" + wdaPort);
                break;
            } catch (Exception e) {
                log.sendStepLog(StepType.WARN, String.format("连接 WebDriverAgent 失败！重试第 %d 次...", retry + 1), "");
                out = e;
            }
            retry++;
            Thread.sleep(2000);
        }
        if (iosDriver == null) {
            log.sendStepLog(StepType.ERROR, "连接 WebDriverAgent 失败！", "");
            setResultDetailStatus(ResultDetailStatus.FAIL);
            throw out;
        }
        iosDriver.getWdaClient().setGlobalTimeOut(5 * 60 * 1000);
        WindowSize size = iosDriver.getWindowSize();
        screenWidth = size.getWidth();
        screenHeight = size.getHeight();
        log.sendStepLog(StepType.PASS, "连接 WebDriverAgent 成功", "");
    }

    public void setSnapshotMaxDepth(HandleContext handleContext, int depth) {
        handleContext.setStepDes("设置控件获取最大遍历深度");
        handleContext.setDetail("设置为 " + depth + " 层");
        try {
            JSONObject settings = new JSONObject();
            settings.put("snapshotMaxDepth", depth);
            appiumSettings(settings);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void closeIOSDriver() {
        try {
            if (iosDriver != null) {
                iosDriver.closeDriver();
                log.sendStepLog(StepType.PASS, "退出连接设备", "");
            }
            if (pocoDriver != null) {
                pocoDriver.closeDriver();
                SibTool.stopProxy(udId, targetPort);
                pocoDriver = null;
            }
        } catch (Exception e) {
            log.sendStepLog(StepType.WARN, "测试终止异常！请检查设备连接状态", e.fillInStackTrace().toString());
            //测试异常
            setResultDetailStatus(ResultDetailStatus.WARN);
        } finally {
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
    }

    public void appiumSettings(JSONObject jsonObject) throws SonicRespException {
        iosDriver.setAppiumSettings(jsonObject);
    }

    public void waitDevice(int waitCount) {
        log.sendStepLog(StepType.INFO, "设备非空闲状态！第" + waitCount + "次等待连接...", "");
    }

    public void waitDeviceTimeOut() {
        log.sendStepLog(StepType.ERROR, "等待设备超时！测试跳过！", "");
        //测试标记为异常
        setResultDetailStatus(ResultDetailStatus.WARN);
    }

    public IOSDriver getIOSDriver() {
        return iosDriver;
    }

    public JSONObject getGlobalParams() {
        return globalParams;
    }

    public String getUdId() {
        return udId;
    }

    private boolean isLockStatus = false;

    public void setResultDetailStatus(int status) {
        if (!isLockStatus && status > this.status) {
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
        int battery = SibTool.battery(udId);
        if (battery <= 10) {
            log.sendStepLog(StepType.ERROR, "设备电量过低!", "跳过本次测试...");
            return true;
        } else {
            return false;
        }
    }

    private int xpathId = 1;

    public JSONArray getResource() {
        try {
            JSONArray elementList = new JSONArray();
            Document doc = Jsoup.parse(iosDriver.getPageSource());
            String xpath = "";
            elementList.addAll(getChild(doc.body().children(), xpath));
            xpathId = 1;
            return elementList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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

//    public void startRecord() {
//        try {
//            IOSStartScreenRecordingOptions recordOption = new IOSStartScreenRecordingOptions();
//            recordOption.withTimeLimit(Duration.ofMinutes(30));
//            recordOption.withVideoQuality(IOSStartScreenRecordingOptions.VideoQuality.LOW);
//            recordOption.enableForcedRestart();
//            recordOption.withFps(20);
//            recordOption.withVideoType("h264");
//            iosDriver.startRecordingScreen(recordOption);
//        } catch (Exception e) {
//            log.sendRecordLog(false, "", "");
//        }
//    }
//
//    public void stopRecord() {
//        File recordDir = new File("./test-output/record");
//        if (!recordDir.exists()) {//判断文件目录是否存在
//            recordDir.mkdirs();
//        }
//        long timeMillis = Calendar.getInstance().getTimeInMillis();
//        String fileName = timeMillis + "_" + udId.substring(0, 4) + ".mp4";
//        File uploadFile = new File(recordDir + File.separator + fileName);
//        try {
//            synchronized (IOSStepHandler.class) {
//                FileOutputStream fileOutputStream = new FileOutputStream(uploadFile);
//                byte[] bytes = Base64Utils.decodeFromString((iosDriver.stopRecordingScreen()));
//                fileOutputStream.write(bytes);
//                fileOutputStream.close();
//            }
//            log.sendRecordLog(true, fileName, UploadTools.uploadPatchRecord(uploadFile));
//        } catch (Exception e) {
//            log.sendRecordLog(false, fileName, "");
//        }
//    }

    public void install(HandleContext handleContext, String path) {
        handleContext.setStepDes("安装应用");
        path = TextHandler.replaceTrans(path, globalParams);
        handleContext.setDetail("App安装路径： " + path);
        File localFile = new File(path);
        try {
            if (path.contains("http")) {
                localFile = DownloadTool.download(path);
            }
            log.sendStepLog(StepType.INFO, "", "开始安装App，请稍后...");
            SibTool.install(udId, localFile.getAbsolutePath());
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void uninstall(HandleContext handleContext, String appPackage) {
        handleContext.setStepDes("卸载应用");
        appPackage = TextHandler.replaceTrans(appPackage, globalParams);
        handleContext.setDetail("App包名： " + appPackage);
        try {
            SibTool.uninstall(udId, appPackage);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void terminate(HandleContext handleContext, String packageName) {
        handleContext.setStepDes("终止应用");
        packageName = TextHandler.replaceTrans(packageName, globalParams);
        handleContext.setDetail("应用包名： " + packageName);
        try {
            iosDriver.appTerminate(packageName);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void runBackground(HandleContext handleContext, long time) {
        handleContext.setStepDes("后台运行应用");
        handleContext.setDetail("后台运行App " + time + " ms");
        try {
            iosDriver.appRunBackground((int) (time / 1000));
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void openApp(HandleContext handleContext, String appPackage) {
        handleContext.setStepDes("打开应用");
        appPackage = TextHandler.replaceTrans(appPackage, globalParams);
        handleContext.setDetail("App包名： " + appPackage);
        try {
            iosDriver.appActivate(appPackage);
            targetPackage = appPackage;
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void lock(HandleContext handleContext) {
        handleContext.setStepDes("锁定屏幕");
        handleContext.setDetail("");
        try {
            iosDriver.lock();
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void unLock(HandleContext handleContext) {
        handleContext.setStepDes("解锁屏幕");
        handleContext.setDetail("");
        try {
            iosDriver.unlock();
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void asserts(HandleContext handleContext, String actual, String expect, String type) {
        handleContext.setStepDes("");
        try {
            switch (type) {
                case "assertEquals" -> {
                    handleContext.setDetail("真实值： " + actual + " 期望等于： " + expect);
                    handleContext.setStepDes("断言验证(相等)");
                    assertEquals(actual, expect);
                }
                case "assertNotEquals" -> {
                    handleContext.setDetail("真实值： " + actual + " 期望不等于： " + expect);
                    handleContext.setStepDes("断言验证(不相等)");
                    assertNotEquals(actual, expect);
                }
                case "assertTrue" -> {
                    handleContext.setDetail("真实值： " + actual + " 期望包含： " + expect);
                    handleContext.setStepDes("断言验证(包含)");
                    assertTrue(actual.contains(expect));
                }
                case "assertNotTrue" -> {
                    handleContext.setDetail("真实值： " + actual + " 期望不包含： " + expect);
                    handleContext.setStepDes("断言验证(不包含)");
                    assertFalse(actual.contains(expect));
                }
            }
        } catch (AssertionError e) {
            handleContext.setE(e);
        }
    }

    public String getText(HandleContext handleContext, String des, String selector, String pathValue) {
        String s = "";
        handleContext.setStepDes("获取" + des + "文本");
        handleContext.setDetail("获取" + selector + ":" + pathValue + "文本");
        try {
            s = findEle(selector, pathValue).getText();
            log.sendStepLog(StepType.INFO, "", "文本获取结果: " + s);
        } catch (Exception e) {
            handleContext.setE(e);
        }
        return s;
    }

    public void click(HandleContext handleContext, String des, String selector, String pathValue) {
        handleContext.setStepDes("点击" + des);
        handleContext.setDetail("点击" + selector + ": " + pathValue);
        try {
            findEle(selector, pathValue).click();
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void sendKeys(HandleContext handleContext, String des, String selector, String pathValue, String keys) {
        keys = TextHandler.replaceTrans(keys, globalParams);
        handleContext.setStepDes("对" + des + "输入内容");
        handleContext.setDetail("对" + selector + ": " + pathValue + " 输入: " + keys);
        try {
            findEle(selector, pathValue).sendKeys(keys);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void sendKeysByActions(HandleContext handleContext, String des, String selector, String pathValue, String keys) {
        keys = TextHandler.replaceTrans(keys, globalParams);
        handleContext.setStepDes("对" + des + "输入内容");
        handleContext.setDetail("对" + selector + ": " + pathValue + " 输入: " + keys);
        try {
            IOSElement iosElement = findEle(selector, pathValue);
            if (iosElement != null) {
                iosElement.click();
                iosDriver.sendKeys(keys);
            }
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void getTextAndAssert(HandleContext handleContext, String des, String selector, String pathValue, String expect) {
        try {
            String s = getText(handleContext, des, selector, pathValue);
            if (handleContext.getE() != null) {
                return;
            }
            handleContext.setStepDes("验证" + des + "文本");
            handleContext.setDetail("验证" + selector + ":" + pathValue + "文本");
            try {
                expect = TextHandler.replaceTrans(expect, globalParams);
                assertEquals(s, expect);
                log.sendStepLog(StepType.INFO, "验证文本", "真实值： " + s + " 期望值： " + expect);
            } catch (AssertionError e) {
                handleContext.setE(e);
            }
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void longPressPoint(HandleContext handleContext, String des, String xy, int time) {
        try {
            xy = TextHandler.replaceTrans(xy, globalParams);
            double x = Double.parseDouble(xy.substring(0, xy.indexOf(",")));
            double y = Double.parseDouble(xy.substring(xy.indexOf(",") + 1));
            int[] point = computedPoint(x, y);
            handleContext.setStepDes("长按" + des);
            handleContext.setDetail("长按坐标" + time + "毫秒 (" + point[0] + "," + point[1] + ")");
            iosDriver.longPress(point[0], point[1], time);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void keyCode(HandleContext handleContext, String key) {
        handleContext.setStepDes("按系统按键" + key + "键");
        handleContext.setDetail("");
        try {
            iosDriver.pressButton(key);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void tap(HandleContext handleContext, String des, String xy) {
        try {
            xy = TextHandler.replaceTrans(xy, globalParams);
            double x = Double.parseDouble(xy.substring(0, xy.indexOf(",")));
            double y = Double.parseDouble(xy.substring(xy.indexOf(",") + 1));
            int[] point = computedPoint(x, y);
            handleContext.setStepDes("点击" + des);
            handleContext.setDetail("点击坐标(" + point[0] + "," + point[1] + ")");
            iosDriver.tap(point[0], point[1]);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void swipePoint(HandleContext handleContext, String des1, String xy1, String des2, String xy2) {
        try {
            xy1 = TextHandler.replaceTrans(xy1, globalParams);
            xy2 = TextHandler.replaceTrans(xy2, globalParams);
            double x1 = Double.parseDouble(xy1.substring(0, xy1.indexOf(",")));
            double y1 = Double.parseDouble(xy1.substring(xy1.indexOf(",") + 1));
            int[] point1 = computedPoint(x1, y1);
            double x2 = Double.parseDouble(xy2.substring(0, xy2.indexOf(",")));
            double y2 = Double.parseDouble(xy2.substring(xy2.indexOf(",") + 1));
            int[] point2 = computedPoint(x2, y2);
            handleContext.setStepDes("滑动拖拽" + des1 + "到" + des2);
            handleContext.setDetail("拖动坐标(" + point1[0] + "," + point1[1] + ")到(" + point2[0] + "," + point2[1] + ")");
            iosDriver.swipe(point1[0], point1[1], point2[0], point2[1]);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void swipe(HandleContext handleContext, String des, String selector, String pathValue, String des2, String selector2, String pathValue2) {
        try {
            IOSElement webElement = findEle(selector, pathValue);
            IOSElement webElement2 = findEle(selector2, pathValue2);
            int x1 = webElement.getRect().getCenter().getX();
            int y1 = webElement.getRect().getCenter().getY();
            int x2 = webElement2.getRect().getCenter().getX();
            int y2 = webElement2.getRect().getCenter().getY();
            handleContext.setStepDes("滑动拖拽" + des + "到" + des2);
            handleContext.setDetail("拖动坐标(" + x1 + "," + y1 + ")到(" + x2 + "," + y2 + ")");
            iosDriver.swipe(x1, y1, x2, y2);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void swipeByDefinedDirection(HandleContext handleContext, String slideDirection, int distance) throws Exception {
        WindowSize size = iosDriver.getWindowSize();
        int width = size.getWidth();
        int height = size.getHeight();
        log.sendStepLog(StepType.INFO, "", "设备逻辑分辨率为：" + width + "x" + height);

        int centerX = (int) Math.ceil(width / 2.0);
        int centerY = (int) Math.ceil(height / 2.0);
        int targetY;
        int targetX;

        switch (slideDirection) {
            case "up" -> {
                handleContext.setStepDes("从设备中心位置向上滑动" + distance + "像素");
                targetY = centerY - distance;
                if (targetY < 0) {
                    targetY = 0;
                    log.sendStepLog(StepType.INFO, "", "滑动距离超出设备顶部，默认取顶部边界值" + "<" + targetY + ">");
                }
                try {
                    iosDriver.swipe(centerX, centerY, centerX, targetY);
                    log.sendStepLog(StepType.INFO, "", centerX + "," + targetY);
                } catch (Exception e) {
                    handleContext.setE(e);
                }
                handleContext.setDetail("拖动坐标(" + centerX + "," + centerY + ")到(" + centerX + "," + targetY + ")");
            }
            case "down" -> {
                handleContext.setStepDes("从设备中心位置向下滑动" + distance + "像素");
                targetY = centerY + distance;
                if (targetY > height) {
                    targetY = height;
                    log.sendStepLog(StepType.INFO, "", "滑动距离超出设备底部，默认取底部边界值" + "<" + targetY + ">");
                }
                try {
                    iosDriver.swipe(centerX, centerY, centerX, targetY);
                } catch (Exception e) {
                    handleContext.setE(e);
                }
                handleContext.setDetail("拖动坐标(" + centerX + "," + centerY + ")到(" + centerX + "," + targetY + ")");

            }
            // The coordinate of the starting point x of left or right sliding is the maximum or minimum value, which is used to solve problems such as too short sliding distance to turn pages.
            case "left" -> {
                handleContext.setStepDes("从设备中心位置向左滑动" + distance + "像素");
                targetX = centerX - distance;
                if (targetX < 0) {
                    targetX = 0;
                    log.sendStepLog(StepType.INFO, "", "滑动距离超出设备左侧，默认取左侧边界值" + "<" + targetX + ">");
                }
                try {
                    iosDriver.swipe(width, centerY, targetX, centerY);
                } catch (Exception e) {
                    handleContext.setE(e);
                }
                handleContext.setDetail("拖动坐标(" + width + "," + centerY + ")到(" + targetX + "," + centerY + ")");

            }
            case "right" -> {
                handleContext.setStepDes("从设备中心位置向右滑动" + distance + "像素");
                targetX = centerX + distance;
                if (targetX > width) {
                    targetX = width;
                    log.sendStepLog(StepType.INFO, "", "滑动距离超出设备右侧，默认取右侧边界值" + "<" + targetX + ">");
                }
                try {
                    iosDriver.swipe(0, centerY, targetX, centerY);
                } catch (Exception e) {
                    handleContext.setE(e);
                }
                handleContext.setDetail("拖动坐标(" + 0 + "," + centerY + ")到(" + targetX + "," + centerY + ")");
            }
            default ->
                    throw new Exception("Sliding in this direction is not supported. Only up/down/left/right are supported!");
        }
    }

    public void longPress(HandleContext handleContext, String des, String selector, String pathValue, int time) {
        handleContext.setStepDes("长按" + des);
        handleContext.setDetail("长按控件元素" + time + "毫秒 ");
        try {
            IOSElement webElement = findEle(selector, pathValue);
            int x = webElement.getRect().getCenter().getX();
            int y = webElement.getRect().getCenter().getY();
            iosDriver.longPress(x, y, time);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void clear(HandleContext handleContext, String des, String selector, String pathValue) {
        handleContext.setStepDes("清空" + des);
        handleContext.setDetail("清空" + selector + ": " + pathValue);
        try {
            findEle(selector, pathValue).clear();
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void isExistEle(HandleContext handleContext, String des, String selector, String pathValue, boolean expect) {
        handleContext.setStepDes("判断控件 " + des + " 是否存在");
        handleContext.setDetail("期望值：" + (expect ? "存在" : "不存在"));
        boolean hasEle = false;
        try {
            IOSElement w = findEle(selector, pathValue);
            if (w != null) {
                hasEle = true;
            }
        } catch (Exception ignored) {
        }
        try {
            assertEquals(hasEle, expect);
        } catch (AssertionError e) {
            handleContext.setE(e);
        }
    }

    /**
     * 断言元素存在个数的方法
     *
     * @param handleContext HandleContext
     * @param des           元素名称
     * @param selector      定位方式
     * @param pathValue     定位值
     * @param operation     操作类型
     * @param expectedCount 期望数量
     * @param elementType   元素的类型
     */
    public void isExistEleNum(HandleContext handleContext, String des, String selector, String pathValue, String operation
            , int expectedCount, int elementType) {
        handleContext.setStepDes("判断控件 " + des + " 存在的个数");
        List elementList = new ArrayList<>();
        switch (elementType) {
            case IOS_ELEMENT_TYPE:
                try {
                    elementList = findEleList(selector, pathValue);
                } catch (SonicRespException e) {
                    // 查找元素不存在时会抛异常
                } catch (Exception ignored) {
                }
                break;
            case POCO_ELEMENT_TYPE:
                try {
                    elementList = findPocoEleList(selector, pathValue);
                } catch (Throwable e) {
                    // 查找元素不存在时会抛异常
                }
                break;
            default:
                handleContext.setE(new AssertionError("未知的元素类型" + elementType + "，无法断言元素个数"));
                break;
        }
        String runDetail = "期望个数：" + operation + " " + expectedCount + "，实际个数：" + " " + (elementList == null ? 0 : elementList.size());
        handleContext.setDetail(runDetail);
        AssertUtil assertUtil = new AssertUtil();
        boolean isSuccess = assertUtil.assertElementNum(operation, expectedCount, elementList);
        try {
            assertTrue(isSuccess);
        } catch (AssertionError e) {
            handleContext.setE(e);
        }
    }

    public void scrollToEle(HandleContext handleContext, String des, String selector, String pathValue, int maxTryTime,
                            String direction) {
        String directionStr = "down".equals(direction) ? "向下" : "向上";
        handleContext.setStepDes(directionStr + "滚动到控件 " + des + " 可见");

        final int xOffset = 20;
        boolean scrollToSuccess = false;
        int tryScrollNums = 0;

        while (tryScrollNums < maxTryTime) {
            try {
                IOSElement w = findEle(selector, pathValue, 1);
                if (w != null) {
                    scrollToSuccess = true;
                    break;
                }
            } catch (Exception ignored) {
            }

            try {
                if ("up".equals(direction)) {
                    iosDriver.swipe(xOffset, screenHeight / 3, xOffset, screenHeight * 2 / 3);
                } else if ("down".equals(direction)) {
                    iosDriver.swipe(xOffset, screenHeight * 2 / 3, xOffset, screenHeight / 3);
                } else {
                    handleContext.setE(new Exception("未知的滚动到方向类型设置"));
                }
            } catch (Exception e) {
                handleContext.setE(e);
            }

            tryScrollNums++;
        }

        if (scrollToSuccess) {
            handleContext.setDetail("实际滚动：" + tryScrollNums + "次后控件" + des + "可见");
        } else {
            handleContext.setE(new Exception("尝试滚动：" + maxTryTime + "次后控件" + des + "依然不可见"));
        }
    }

    public void getElementAttr(HandleContext handleContext, String des, String selector, String pathValue, String attr, String expect) {
        handleContext.setStepDes("验证控件 " + des + " 属性");
        handleContext.setDetail("属性：" + attr + "，期望值：" + expect);
        try {
            String attrValue = findEle(selector, pathValue).getAttribute(attr);
            log.sendStepLog(StepType.INFO, "", attr + " 属性获取结果: " + attrValue);
            try {
                assertEquals(attrValue, expect);
            } catch (AssertionError e) {
                handleContext.setE(e);
            }
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void obtainElementAttr(HandleContext handleContext, String des, String selector, String pathValue,
                                  String attr, String variable) {
        handleContext.setStepDes("获取控件 " + des + " 属性到变量");
        handleContext.setDetail("目标属性：" + attr + "，目标变量：" + variable);
        try {
            String attrValue = findEle(selector, pathValue).getAttribute(attr);
            log.sendStepLog(StepType.INFO, "", attr + " 属性获取结果: " + attrValue);
            globalParams.put(variable, attrValue);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void logElementAttr(HandleContext handleContext, String des, String selector, String pathValue, String attr) {
        handleContext.setStepDes("日志输出控件 " + des + " 属性");
        handleContext.setDetail("目标属性：" + attr);
        List<String> attrs = JSON.parseArray(attr, String.class);
        StringBuilder logs = new StringBuilder();
        for (String a : attrs) {
            try {
                String attrValue = findEle(selector, pathValue).getAttribute(a);
                logs.append(String.format(" %s=%s,", a, attrValue));
            } catch (Exception e) {
                handleContext.setE(e);
            }
        }
        if (logs.length() > 0) {
            logs = new StringBuilder(logs.substring(0, logs.length() - 1));
        }
        log.sendStepLog(StepType.INFO, "", "属性获取结果:" + logs);
    }

    public void logPocoElementAttr(HandleContext handleContext, String des, String selector, String pathValue, String attr) {
        handleContext.setStepDes("日志输出控件 " + des + " 属性");
        handleContext.setDetail("目标属性：" + attr);
        List<String> attrs = JSON.parseArray(attr, String.class);
        StringBuilder logs = new StringBuilder();
        for (String a : attrs) {
            try {
                String attrValue = findPocoEle(selector, pathValue).getAttribute(a);
                logs.append(String.format(" %s=%s,", a, attrValue));
            } catch (Throwable e) {
                handleContext.setE(e);
            }
        }
        if (logs.length() > 0) {
            logs = new StringBuilder(logs.substring(0, logs.length() - 1));
        }
        log.sendStepLog(StepType.INFO, "", "属性获取结果:" + logs);
    }

    public void clickByImg(HandleContext handleContext, String des, String pathValue) {
        handleContext.setStepDes("点击图片" + des);
        handleContext.setDetail(pathValue);
        File file = null;
        if (pathValue.startsWith("http")) {
            try {
                file = DownloadTool.download(pathValue);
            } catch (Exception e) {
                handleContext.setE(e);
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
                    handleContext.setE(new Exception("图片定位失败！"));
                }
            }
        }
        if (findResult != null) {
            try {
                double scale = SibTool.getScreenScale(udId);
                iosDriver.tap((int) (findResult.getX() * 1.0 / scale),
                        (int) (findResult.getY() * 1.0 / scale));
            } catch (Exception e) {
                log.sendStepLog(StepType.ERROR, "点击" + des + "失败！", "");
                handleContext.setE(e);
            }
        }
    }


    public void readText(HandleContext handleContext, String language, String text) {
//        TextReader textReader = new TextReader();
//        String result = textReader.getTessResult(getScreenToLocal(), language);
//        log.sendStepLog(StepType.INFO, "",
//                "图像文字识别结果：<br>" + result);
//        String filter = result.replaceAll(" ", "");
        handleContext.setStepDes("图像文字识别");
        handleContext.setDetail("（该功能暂时关闭）期望包含文本：" + text);
//        if (!filter.contains(text)) {
//            handleDes.setE(new Exception("图像文字识别不通过！"));
//        }
    }

    public File getScreenToLocal() {
        File folder = new File("test-output");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File output = new File(folder + File.separator + log.udId + Calendar.getInstance().getTimeInMillis() + ".png");
        try {
            byte[] bt = iosDriver.screenshot();
            FileImageOutputStream imageOutput = new FileImageOutputStream(output);
            imageOutput.write(bt, 0, bt.length);
            imageOutput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    public void checkImage(HandleContext handleContext, String des, String pathValue, double matchThreshold) throws Exception {
        log.sendStepLog(StepType.INFO, "开始检测" + des + "兼容", "检测与当前设备截图相似度，期望相似度为" + matchThreshold + "%");
        File file = null;
        if (pathValue.startsWith("http")) {
            file = DownloadTool.download(pathValue);
        }
        SimilarityChecker similarityChecker = new SimilarityChecker();
        double score = similarityChecker.getSimilarMSSIMScore(file, getScreenToLocal(), true);
        handleContext.setStepDes("检测" + des + "图片相似度");
        handleContext.setDetail("相似度为" + score * 100 + "%");
        if (score == 0) {
            handleContext.setE(new Exception("图片相似度检测不通过！比对图片分辨率不一致！"));
        } else if (score < (matchThreshold / 100)) {
            handleContext.setE(new Exception("图片相似度检测不通过！expect " + matchThreshold + " but " + score * 100));
        }
    }

    public void siriCommand(HandleContext handleContext, String command) {
        handleContext.setStepDes("siri指令");
        handleContext.setDetail("对siri发送指令： " + command);
        try {
            iosDriver.sendSiriCommand(command);
        } catch (Exception e) {
            handleContext.setE(e);
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
            byte[] bt = iosDriver.screenshot();
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

    public String stepScreen(HandleContext handleContext) {
        handleContext.setStepDes("获取截图");
        handleContext.setDetail("");
        String url = "";
        try {
            File folder = new File("test-output");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File output = new File(folder + File.separator + udId + Calendar.getInstance().getTimeInMillis() + ".png");
            byte[] bt = iosDriver.screenshot();
            FileImageOutputStream imageOutput = new FileImageOutputStream(output);
            imageOutput.write(bt, 0, bt.length);
            imageOutput.close();
            url = UploadTools.upload(output, "imageFiles");
            handleContext.setDetail(url);
        } catch (Exception e) {
            handleContext.setE(e);
        }
        return url;
    }

    public void pause(HandleContext handleContext, int time) {
        handleContext.setStepDes("强制等待");
        handleContext.setDetail("等待" + time + " ms");
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            handleContext.setE(e);
        }
    }

    public void runMonkey(HandleContext handleContext, JSONObject content, List<JSONObject> text) {
        handleContext.setStepDes("运行随机事件测试完毕");
        handleContext.setDetail("");
        String packageName = content.getString("packageName");
        int pctNum = content.getInteger("pctNum");
        if (!SibTool.getAppList(udId).contains(packageName)) {
            log.sendStepLog(StepType.ERROR, "应用未安装！", "设备未安装 " + packageName);
            handleContext.setE(new Exception("未安装应用"));
            return;
        }
        JSONArray options = content.getJSONArray("options");
        WindowSize windowSize = null;
        try {
            windowSize = iosDriver.getWindowSize();
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
        boolean isOpenPackageListener = false;
        if (!options.isEmpty()) {
            for (Object j : options) {
                JSONObject jsonOption = JSON.parseObject(j.toString());
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
                if (jsonOption.getString("name").equals("isOpenPackageListener")) {
                    isOpenPackageListener = jsonOption.getBoolean("value");
                }
            }
        }
        int finalSleepTime = sleepTime;
        int finalTapEvent = tapEvent;
        int finalLongPressEvent = longPressEvent;
        int finalSwipeEvent = swipeEvent;
        int finalSystemEvent = systemEvent;
        int finalNavEvent = navEvent;
        Future<?> randomThread = IOSDeviceThreadPool.cachedThreadPool.submit(() -> {
                    log.sendStepLog(StepType.INFO, "", "随机事件数：" + pctNum +
                            "<br>目标应用：" + packageName
                            + "<br>用户操作时延：" + finalSleepTime + " ms"
                            + "<br>轻触事件权重：" + finalTapEvent
                            + "<br>长按事件权重：" + finalLongPressEvent
                            + "<br>滑动事件权重：" + finalSwipeEvent
                            + "<br>物理按键事件权重：" + finalSystemEvent
                            + "<br>系统事件权重：" + finalNavEvent
                    );
                    openApp(new HandleContext(), packageName);
                    int totalCount = finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent + finalNavEvent;
                    for (int i = 0; i < pctNum; i++) {
                        try {
                            int random = new Random().nextInt(totalCount);
                            if (random < finalSystemEvent) {
                                int key = new Random().nextInt(3);
                                SystemButton keyType = switch (key) {
                                    case 0 -> SystemButton.VOLUME_DOWN;
                                    case 1 -> SystemButton.VOLUME_UP;
                                    case 2 -> SystemButton.HOME;
                                    default -> throw new IllegalStateException("Unexpected value: " + key);
                                };
                                iosDriver.pressButton(keyType);
                            }
                            if (random >= finalSystemEvent && random < (finalSystemEvent + finalTapEvent)) {
                                int x = new Random().nextInt(width - 60) + 60;
                                int y = new Random().nextInt(height - 60) + 60;
                                iosDriver.tap(x, y);
                            }
                            if (random >= (finalSystemEvent + finalTapEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent)) {
                                int x = new Random().nextInt(width - 60) + 60;
                                int y = new Random().nextInt(height - 60) + 60;
                                iosDriver.longPress(x, y, (new Random().nextInt(3) + 1) * 1000);
                            }
                            if (random >= (finalSystemEvent + finalTapEvent + finalLongPressEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent)) {
                                int x1 = new Random().nextInt(width - 60) + 60;
                                int y1 = new Random().nextInt(height - 80) + 80;
                                int x2 = new Random().nextInt(width - 60) + 60;
                                int y2 = new Random().nextInt(height - 80) + 80;
                                iosDriver.swipe(x1, y1, x2, y2);
                            }
                            if (random >= (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent) && random < (finalSystemEvent + finalTapEvent + finalLongPressEvent + finalSwipeEvent + finalNavEvent)) {
                                iosDriver.appRunBackground((new Random().nextInt(3) + 1) * 1000);
                            }
                            Thread.sleep(finalSleepTime);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        boolean finalIsOpenPackageListener = isOpenPackageListener;
        Future<?> packageListener = IOSDeviceThreadPool.cachedThreadPool.submit(() -> {
                    if (finalIsOpenPackageListener) {
                        while (!randomThread.isDone()) {
                            int waitTime = 0;
                            while (waitTime <= 10 && (!randomThread.isDone())) {
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                SibTool.launch(udId, packageName);
                                waitTime++;
                            }
                            SibTool.launch(udId, packageName);
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
                (isOpenPackageListener ? "<br>应用包名监听器已开启..." : ""));
        while (!randomThread.isDone() || (!packageListener.isDone())) {
        }
    }

    public void publicStep(HandleContext handleContext, String name, JSONArray stepArray) {
        log.sendStepLog(StepType.WARN, "公共步骤「" + name + "」开始执行", "");
        isLockStatus = true;
        for (Object publicStep : stepArray) {
            JSONObject stepDetail = (JSONObject) publicStep;
            try {
                SpringTool.getBean(StepHandlers.class)
                        .runStep(stepDetail, handleContext, (RunStepThread) Thread.currentThread());
            } catch (Throwable e) {
                handleContext.setE(e);
                break;
            }
        }
        isLockStatus = false;
        handleContext.setStepDes("IGNORE");
        if (handleContext.getE() != null && (!handleContext.getE().getMessage().startsWith("IGNORE:"))) {
            handleContext.setStepDes("执行公共步骤 " + name);
            handleContext.setDetail("");
            handleContext.setE(new SonicRespException("Exception thrown during child step running."));
        }
        log.sendStepLog(StepType.WARN, "公共步骤「" + name + "」执行完毕", "");
    }

    public void startPocoDriver(HandleContext handleContext, String engine, int port) {
        handleContext.setStepDes("启动PocoDriver");
        handleContext.setDetail("");
        if (pocoPort == 0) {
            pocoPort = PortTool.getPort();
        }
        targetPort = port;
        SibTool.proxy(udId, pocoPort, targetPort);
        pocoDriver = new PocoDriver(PocoEngine.valueOf(engine), pocoPort);
    }

    private int intervalInit = 3000;
    private int retryInit = 3;

    public void setDefaultFindPocoElementInterval(HandleContext handleContext, Integer retry, Integer interval) {
        handleContext.setStepDes("设置查找POCO控件策略");
        handleContext.setDetail(String.format("Retry count: %d, retry interval: %d ms", retry, interval));
        if (retry != null) {
            retryInit = retry;
        }
        if (interval != null) {
            intervalInit = interval;
        }
    }

    public PocoElement findPocoEle(String selector, String pathValue) throws Throwable {
        PocoElement pocoElement = null;
        pathValue = TextHandler.replaceTrans(pathValue, globalParams);
        int wait = 0;
        String errMsg = "";
        while (wait < retryInit) {
            wait++;
            pocoDriver.getPageSourceForXmlElement();
            try {
                switch (selector) {
                    case "poco" -> pocoElement = pocoDriver.findElement(PocoSelector.POCO, pathValue);
                    case "xpath" -> pocoElement = pocoDriver.findElement(PocoSelector.XPATH, pathValue);
                    case "cssSelector", "pocoIterator" ->
                            pocoElement = pocoDriver.findElement(PocoSelector.CSS_SELECTOR, pathValue);
                    default ->
                            log.sendStepLog(StepType.ERROR, "查找控件元素失败", "这个控件元素类型: " + selector + " 不存在!!!");
                }
                if (pocoElement != null) {
                    break;
                }
            } catch (Throwable e) {
                errMsg = e.getMessage();
            }
            if (wait < retryInit) {
                try {
                    Thread.sleep(intervalInit);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (pocoElement == null) {
            throw new SonicRespException(errMsg);
        }
        return pocoElement;
    }

    public void isExistPocoEle(HandleContext handleContext, String des, String selector, String value, boolean expect) {
        handleContext.setStepDes("判断控件 " + des + " 是否存在");
        handleContext.setDetail("期望值：" + (expect ? "存在" : "不存在"));
        boolean hasEle = false;
        try {
            PocoElement w = findPocoEle(selector, value);
            if (w != null) {
                hasEle = true;
            }
        } catch (Throwable ignored) {
        }
        try {
            assertEquals(hasEle, expect);
        } catch (AssertionError e) {
            handleContext.setE(e);
        }
    }

    public void getPocoElementAttr(HandleContext handleContext, String des, String selector, String pathValue, String attr, String expect) {
        handleContext.setStepDes("验证控件 " + des + " 属性");
        handleContext.setDetail("属性：" + attr + "，期望值：" + expect);
        String attrValue = getPocoAttrValue(handleContext, selector, pathValue, attr);
        log.sendStepLog(StepType.INFO, "", attr + " 属性获取结果: " + attrValue);
        try {
            assertEquals(attrValue, expect);
        } catch (AssertionError e) {
            handleContext.setE(e);
        }
    }

    public void obtainPocoElementAttr(HandleContext handleContext, String des, String selector, String pathValue,
                                      String attr, String variable) {
        handleContext.setStepDes("获取控件 " + des + " 属性到变量");
        handleContext.setDetail("目标属性：" + attr + "，目标变量：" + variable);
        try {
            String attrValue = getPocoAttrValue(handleContext, selector, pathValue, attr);
            log.sendStepLog(StepType.INFO, "", attr + " 属性获取结果: " + attrValue);
            globalParams.put(variable, attrValue);
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    private String getPocoAttrValue(HandleContext handleContext, String selector, String pathValue, String attr) {
        String attrValue = "";
        try {
            PocoElement pocoElement = findPocoEle(selector, pathValue);
            switch (attr) {
                case "type" -> attrValue = pocoElement.getPayload().getType();
                case "layer" -> attrValue = pocoElement.getPayload().getLayer();
                case "tag" -> attrValue = pocoElement.getPayload().getTag();
                case "text" -> attrValue = pocoElement.getPayload().getText();
                case "texture" -> attrValue = pocoElement.getPayload().getTexture();
                case "_instanceId" -> attrValue = pocoElement.getPayload().get_instanceId() + "";
                case "name" -> attrValue = pocoElement.getPayload().getName();
                case "visible" -> attrValue = pocoElement.getPayload().getVisible().toString();
                case "clickable" -> attrValue = pocoElement.getPayload().getClickable().toString();
                case "_ilayer" -> attrValue = pocoElement.getPayload().get_ilayer() + "";
                case "global" -> attrValue = pocoElement.getPayload().getZOrders().getGlobal() + "";
                case "local" -> attrValue = pocoElement.getPayload().getZOrders().getLocal() + "";
                case "components" -> attrValue = pocoElement.getPayload().getComponents().toString();
                case "anchorPoint" -> attrValue = pocoElement.getPayload().getAnchorPoint().toString();
                case "scale" -> attrValue = pocoElement.getPayload().getScale().toString();
                case "size" -> attrValue = pocoElement.getPayload().getSize().toString();
                case "pos" -> attrValue = pocoElement.getPayload().getPos().toString();
            }
        } catch (Throwable e) {
            handleContext.setE(e);
        }
        return attrValue;
    }

    public String getPocoText(HandleContext handleContext, String des, String selector, String pathValue) {
        String s = "";
        handleContext.setStepDes("获取" + des + "文本");
        handleContext.setDetail("获取" + selector + ":" + pathValue + "文本");
        try {
            PocoElement w = findPocoEle(selector, pathValue);
            if (w != null) {
                s = w.getPayload().getText();
                log.sendStepLog(StepType.INFO, "", "文本获取结果: " + s);
            } else {
                throw new SonicRespException(pathValue + " not found!");
            }
        } catch (Throwable e) {
            handleContext.setE(e);
        }
        return s;
    }

    public void getPocoTextAndAssert(HandleContext handleContext, String des, String selector, String pathValue, String expect) {
        try {
            String s = getPocoText(handleContext, des, selector, pathValue);
            if (handleContext.getE() != null) {
                return;
            }
            handleContext.setStepDes("验证" + des + "文本");
            handleContext.setDetail("验证" + selector + ":" + pathValue + "文本");
            try {
                expect = TextHandler.replaceTrans(expect, globalParams);
                assertEquals(s, expect);
                log.sendStepLog(StepType.INFO, "验证文本", "真实值： " + s + " 期望值： " + expect);
            } catch (AssertionError e) {
                handleContext.setE(e);
            }
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void pocoClick(HandleContext handleContext, String des, String selector, String value) {
        handleContext.setStepDes("点击" + des);
        handleContext.setDetail("点击 " + value);
        try {
            PocoElement w = findPocoEle(selector, value);
            if (w != null) {
                List<Float> pos = w.getPayload().getPos();
                int[] realCoordinates = getTheRealCoordinatesOfPoco(pos.get(0), pos.get(1));
                iosDriver.tap(realCoordinates[0], realCoordinates[1]);
            } else {
                throw new SonicRespException(value + " not found!");
            }
        } catch (Throwable e) {
            handleContext.setE(e);
        }
    }

    public void pocoLongPress(HandleContext handleContext, String des, String selector, String value, int time) {
        handleContext.setStepDes("长按" + des);
        handleContext.setDetail("长按 " + value);
        try {
            PocoElement w = findPocoEle(selector, value);
            if (w != null) {
                List<Float> pos = w.getPayload().getPos();
                int[] realCoordinates = getTheRealCoordinatesOfPoco(pos.get(0), pos.get(1));
                iosDriver.longPress(realCoordinates[0], realCoordinates[1], time);
            } else {
                throw new SonicRespException(value + " not found!");
            }
        } catch (Throwable e) {
            handleContext.setE(e);
        }
    }

    public void pocoSwipe(HandleContext handleContext, String des, String selector, String value, String des2, String selector2, String value2) {
        handleContext.setStepDes("滑动拖拽" + des + "到" + des2);
        handleContext.setDetail("拖拽 " + value + " 到 " + value2);
        try {
            PocoElement w1 = findPocoEle(selector, value);
            PocoElement w2 = findPocoEle(selector2, value2);
            if (w1 != null && w2 != null) {
                List<Float> pos1 = w1.getPayload().getPos();
                int[] realCoordinates1 = getTheRealCoordinatesOfPoco(pos1.get(0), pos1.get(1));

                List<Float> pos2 = w2.getPayload().getPos();
                int[] realCoordinate2 = getTheRealCoordinatesOfPoco(pos2.get(0), pos2.get(1));

                iosDriver.swipe(realCoordinates1[0], realCoordinates1[1], realCoordinate2[0], realCoordinate2[1]);
            } else {
                throw new SonicRespException(value + " or " + value2 + " not found!");
            }
        } catch (Throwable e) {
            handleContext.setE(e);
        }
    }

    public void setTheRealPositionOfTheWindow(HandleContext handleContext, String text) {
        JSONObject offsetValue = JSONObject.parseObject(text);
        handleContext.setStepDes("设置偏移量");
        handleContext.setDetail(String.format("offsetWidth: %d, offsetHeight: %d, windowWidth: %d, windowHeight: %d",
                offsetValue.getInteger("offsetWidth"),
                offsetValue.getInteger("offsetHeight"),
                offsetValue.getInteger("windowWidth"),
                offsetValue.getInteger("windowHeight")
        ));
        this.screenWindowPosition[0] = offsetValue.getInteger("offsetWidth");
        this.screenWindowPosition[1] = offsetValue.getInteger("offsetHeight");
        this.screenWindowPosition[2] = offsetValue.getInteger("windowWidth");
        this.screenWindowPosition[3] = offsetValue.getInteger("windowHeight");
    }

    public int[] getTheRealCoordinatesOfPoco(double pocoX, double pocoY) {
        int[] pos = new int[2];
        int screenOrientation = SibTool.getOrientation(udId);

        int width = screenWindowPosition[2], height = screenWindowPosition[3];

        if (width == 0 || height == 0) {
            WindowSize windowSize = null;
            try {
                windowSize = iosDriver.getWindowSize();
            } catch (SonicRespException e) {
                e.printStackTrace();
            }
            if (screenOrientation == 1 || screenOrientation == 2) {
                width = windowSize.getWidth();
                height = windowSize.getHeight();
            } else {
                height = windowSize.getWidth();
                width = windowSize.getHeight();
            }
        }

        if (screenOrientation == 3 || screenOrientation == 4) {
            // x
            pos[0] = this.screenWindowPosition[1] + (int) (height * pocoX);
            // y
            pos[1] = this.screenWindowPosition[0] + (int) (width * pocoY);
        } else {
            // x = offsetX + width*pocoPosX
            pos[0] = this.screenWindowPosition[0] + (int) (width * pocoX);
            // y = offsetY + height*pocoPosY
            pos[1] = this.screenWindowPosition[1] + (int) (height * pocoY);
        }
        return pos;
    }

    public void freezeSource(HandleContext handleContext) {
        handleContext.setStepDes("冻结控件树");
        handleContext.setDetail("");
        try {
            pocoDriver.getPageSourceForJsonString();
            pocoDriver.freezeSource();
        } catch (Throwable e) {
            handleContext.setE(e);
        }
    }

    public void thawSource(HandleContext handleContext) {
        handleContext.setStepDes("解冻控件树");
        handleContext.setDetail("");
        pocoDriver.thawSource();
    }

    public void closePocoDriver(HandleContext handleContext) {
        handleContext.setStepDes("关闭PocoDriver");
        handleContext.setDetail("");
        if (pocoDriver != null) {
            pocoDriver.closeDriver();
            SibTool.stopProxy(udId, targetPort);
            pocoDriver = null;
        }
    }

    public PocoDriver getPocoDriver() {
        return pocoDriver;
    }

    public IOSElement findEle(String selector, String pathValue) throws SonicRespException {
        return findEle(selector, pathValue, null);
    }

    public IOSElement findEle(String selector, String pathValue, Integer retryTime) throws SonicRespException {
        IOSElement we = null;
        pathValue = TextHandler.replaceTrans(pathValue, globalParams);
        switch (selector) {
            case "iOSIterator" -> we = iosDriver.findElement(pathValue);
            case "id" -> we = iosDriver.findElement(IOSSelector.Id, pathValue, retryTime);
            case "accessibilityId" -> we = iosDriver.findElement(IOSSelector.ACCESSIBILITY_ID, pathValue, retryTime);
            case "nsPredicate" -> we = iosDriver.findElement(IOSSelector.PREDICATE, pathValue, retryTime);
            case "name" -> we = iosDriver.findElement(IOSSelector.NAME, pathValue, retryTime);
            case "xpath" -> we = iosDriver.findElement(IOSSelector.XPATH, pathValue, retryTime);
            case "classChain" -> we = iosDriver.findElement(IOSSelector.CLASS_CHAIN, pathValue, retryTime);
            case "className" -> we = iosDriver.findElement(IOSSelector.CLASS_NAME, pathValue, retryTime);
            case "linkText" -> we = iosDriver.findElement(IOSSelector.LINK_TEXT, pathValue, retryTime);
            case "partialLinkText" -> we = iosDriver.findElement(IOSSelector.PARTIAL_LINK_TEXT, pathValue, retryTime);
            default ->
                    log.sendStepLog(StepType.ERROR, "查找控件元素失败", "这个控件元素类型: " + selector + " 不存在!!!");
        }
        return we;
    }

    public List<IOSElement> findEleList(String selector, String pathValue) throws SonicRespException {
        List<IOSElement> iosElements = null;
        pathValue = TextHandler.replaceTrans(pathValue, globalParams);
        switch (selector) {
            case "id" -> iosElements = iosDriver.findElementList(IOSSelector.Id, pathValue);
            case "accessibilityId" -> iosElements = iosDriver.findElementList(IOSSelector.ACCESSIBILITY_ID, pathValue);
            case "nsPredicate" -> iosElements = iosDriver.findElementList(IOSSelector.PREDICATE, pathValue);
            case "name" -> iosElements = iosDriver.findElementList(IOSSelector.NAME, pathValue);
            case "xpath" -> iosElements = iosDriver.findElementList(IOSSelector.XPATH, pathValue);
            case "classChain" -> iosElements = iosDriver.findElementList(IOSSelector.CLASS_CHAIN, pathValue);
            case "className" -> iosElements = iosDriver.findElementList(IOSSelector.CLASS_NAME, pathValue);
            case "linkText" -> iosElements = iosDriver.findElementList(IOSSelector.LINK_TEXT, pathValue);
            case "partialLinkText" -> iosElements = iosDriver.findElementList(IOSSelector.PARTIAL_LINK_TEXT, pathValue);
            default ->
                    log.sendStepLog(StepType.ERROR, "查找控件元素失败", "这个控件元素类型: " + selector + " 不存在!!!");
        }
        return iosElements;
    }

    public void setFindElementInterval(HandleContext handleContext, int retry, int interval) {
        handleContext.setStepDes("设置查找原生控件策略");
        handleContext.setDetail(String.format("Retry count: %d, retry interval: %d ms", retry, interval));
        iosDriver.setDefaultFindElementInterval(retry, interval);
    }

    public void stepHold(HandleContext handleContext, int time) {
        handleContext.setStepDes("设置全局步骤间隔");
        handleContext.setDetail("间隔" + time + " ms");
        holdTime = time;
    }

    public void sendKeyForce(HandleContext handleContext, String text) {
        text = TextHandler.replaceTrans(text, globalParams);
        handleContext.setStepDes("键盘输入文本");
        handleContext.setDetail("键盘输入" + text);
        try {
            iosDriver.sendKeys(text);
        } catch (SonicRespException e) {
            handleContext.setE(e);
        }
    }

    public void setPasteboard(HandleContext handleContext, String text) {
        text = TextHandler.replaceTrans(text, globalParams);
        handleContext.setStepDes("Set text to clipboard");
        handleContext.setDetail("Set text: " + text);
        try {
            iosDriver.appActivate("com.apple.springboard");
            iosDriver.sendSiriCommand("open WebDriverAgentRunner-Runner");
            Thread.sleep(2000);
            iosDriver.setPasteboard(PasteboardType.PLAIN_TEXT, text);
            iosDriver.pressButton(SystemButton.HOME);
        } catch (SonicRespException | InterruptedException e) {
            handleContext.setE(e);
        }
    }

    public String getPasteboard(HandleContext handleContext) {
        String text = "";
        handleContext.setStepDes("Get clipboard text");
        handleContext.setDetail("");
        try {
            iosDriver.appActivate("com.apple.springboard");
            iosDriver.sendSiriCommand("open WebDriverAgentRunner-Runner");
            Thread.sleep(2000);
            text = new String(iosDriver.getPasteboard(PasteboardType.PLAIN_TEXT), StandardCharsets.UTF_8);
            iosDriver.pressButton(SystemButton.HOME);
        } catch (SonicRespException | InterruptedException e) {
            handleContext.setE(e);
        }
        return text;
    }

    private int holdTime = 0;

    private int[] computedPoint(double x, double y) throws SonicRespException {
        if (x <= 1 && y <= 1) {
            WindowSize windowSize = iosDriver.getWindowSize();
            x = windowSize.getWidth() * x;
            y = windowSize.getHeight() * y;
        }
        return new int[]{(int) x, (int) y};
    }

    public void runScript(HandleContext handleContext, String script, String type) {
        handleContext.setStepDes("Run Custom Scripts");
        handleContext.setDetail("Script: <br>" + script);
        try {
            switch (type) {
                case "Groovy" -> {
                    ScriptRunner groovyScript = new GroovyScriptImpl();
                    groovyScript.runIOS(this, script);
                }
                case "Python" -> {
                    ScriptRunner pythonScript = new PythonScriptImpl();
                    pythonScript.runIOS(this, script);
                }
            }
        } catch (Throwable e) {
            handleContext.setE(e);
        }
    }

    public List<PocoElement> findPocoEleList(String selector, String pathValue) throws Throwable {
        List<PocoElement> pocoElements = null;
        pathValue = TextHandler.replaceTrans(pathValue, globalParams);
        int wait = 0;
        String errMsg = "";
        while (wait < retryInit) {
            wait++;
            pocoDriver.getPageSourceForXmlElement();
            try {
                switch (selector) {
                    case "poco" -> pocoElements = pocoDriver.findElements(PocoSelector.POCO, pathValue);
                    case "xpath" -> pocoElements = pocoDriver.findElements(PocoSelector.XPATH, pathValue);
                    case "cssSelector", "pocoIterator" ->
                            pocoElements = pocoDriver.findElements(PocoSelector.CSS_SELECTOR, pathValue);
                    default ->
                            log.sendStepLog(StepType.ERROR, "查找控件元素列表失败", "这个控件元素类型: " + selector + " 不存在!!!");
                }
                if (pocoElements != null) {
                    break;
                }
            } catch (Throwable e) {
                errMsg = e.getMessage();
            }
            if (wait < retryInit) {
                try {
                    Thread.sleep(intervalInit);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (pocoElements == null) {
            throw new SonicRespException(errMsg);
        }
        return pocoElements;
    }

    public void iteratorPocoElement(HandleContext handleContext, String des, String selector, String value) {

        List<PocoElement> pocoElements;

        if (handleContext.iteratorElement == null) {
            handleContext.setStepDes("迭代控件列表 " + des);
            try {
                pocoElements = findPocoEleList(selector, value);
                List<BaseElement> res = new ArrayList<>(pocoElements);
                handleContext.iteratorElement = res.iterator();
            } catch (Throwable e) {
                handleContext.setE(e);
                return;
            }
            log.sendStepLog(StepType.INFO, "", "迭代控件列表长度：" + pocoElements.size());
        }

        if (handleContext.iteratorElement.hasNext()) {
            handleContext.currentIteratorElement = handleContext.iteratorElement.next();
            PocoElement p = (PocoElement) handleContext.currentIteratorElement;
            handleContext.setStepDes("当前迭代控件：" + p.currentNodeSelector);
            handleContext.setDetail("");

        } else {
            handleContext.iteratorElement = null;
            handleContext.currentIteratorElement = null;
            log.sendStepLog(StepType.INFO, "", "迭代控件列表完毕...");
            handleContext.setStepDes("迭代控件列表 " + des);
            handleContext.setE(new Exception("exit while"));
        }
    }

    public void iteratorIOSElement(HandleContext handleContext, String des, String selector, String value) {

        List<IOSElement> iOSElements;

        if (handleContext.iteratorElement == null) {
            handleContext.setStepDes("迭代控件列表 " + des);
            try {
                iOSElements = findEleList(selector, value);
                List<BaseElement> res = new ArrayList<>(iOSElements);
                handleContext.iteratorElement = res.iterator();
            } catch (Throwable e) {
                handleContext.setE(e);
                return;
            }
            log.sendStepLog(StepType.INFO, "", "迭代控件列表长度：" + iOSElements.size());
        }

        if (handleContext.iteratorElement.hasNext()) {
            handleContext.currentIteratorElement = handleContext.iteratorElement.next();
            IOSElement i = (IOSElement) handleContext.currentIteratorElement;
            try {
                handleContext.setStepDes("当前迭代控件：" + i.getUniquelyIdentifies());
                handleContext.setDetail("");
            } catch (Exception e) {
                handleContext.setE(e);
            }

        } else {
            handleContext.iteratorElement = null;
            handleContext.currentIteratorElement = null;
            log.sendStepLog(StepType.INFO, "", "迭代控件列表完毕...");
            handleContext.setStepDes("迭代控件列表 " + des);
            handleContext.setE(new Exception("exit while"));
        }
    }

    /**
     * >2.5.0版本，增强型的文本断言能力，支持指定断言的方式
     *
     * @param handleContext HandleContext
     * @param des           元素名
     * @param selector      元素定位方式
     * @param pathValue     定位方式值
     * @param operation     断言类型(equal,notEqual,contain,notContain)
     * @param expect        期望值
     * @param elementType   元素类型(原生，web，poco)
     */
    public void getElementTextAndAssertWithOperation(HandleContext handleContext, String des, String selector,
                                                     String pathValue, String operation, String expect, int elementType) {
        try {
            String realValue = switch (elementType) {
                case IOS_ELEMENT_TYPE -> getText(handleContext, des, selector, pathValue);
                case POCO_ELEMENT_TYPE -> getPocoText(handleContext, des, selector, pathValue);
                default -> throw new SonicRespException("未支持的元素类型" + elementType + "，无法进行文本断言");
            };
            if (handleContext.getE() != null) {
                return;
            }
            handleContext.setStepDes("断言" + des + "文本");
            try {
                expect = TextHandler.replaceTrans(expect, globalParams);
                AssertUtil assertUtil = new AssertUtil();
                StringBuilder sbLog = new StringBuilder("真实值：");
                sbLog.append(realValue);
                sbLog.append("，期望 ");
                sbLog.append(assertUtil.getAssertDesc(operation));
                sbLog.append(" ");
                sbLog.append(expect);
                handleContext.setDetail(sbLog.toString());
                switch (operation) {
                    case "equal" -> assertEquals(realValue, expect);
                    case "notEqual" -> assertNotEquals(realValue, expect);
                    case "contain" -> assertTrue(realValue.contains(expect));
                    case "notContain" -> assertFalse(realValue.contains(expect));
                    default ->
                            throw new SonicRespException("未支持的文本断言操作类型" + operation + "，无法进行文本断言");
                }
            } catch (AssertionError e) {
                handleContext.setE(e);
            }
        } catch (Exception e) {
            handleContext.setE(e);
        }
    }

    public void runStep(JSONObject stepJSON, HandleContext handleContext) throws Throwable {
        JSONObject step = stepJSON.getJSONObject("step");
        if (CollectionUtils.isEmpty(step)) {
            step = stepJSON;
        }
        JSONArray eleList = step.getJSONArray("elements");
        Thread.sleep(holdTime);
        switch (step.getString("stepType")) {
            case "stepHold" -> stepHold(handleContext, step.getInteger("content"));
            case "siriCommand" -> siriCommand(handleContext, step.getString("content"));
            case "readText" -> readText(handleContext, step.getString("content"), step.getString("text"));
            case "clickByImg" -> clickByImg(handleContext, eleList.getJSONObject(0).getString("eleName")
                    , eleList.getJSONObject(0).getString("eleValue"));
            case "click" ->
                    click(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"));
            case "getElementAttr" ->
                    getElementAttr(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getString("text"), step.getString("content"));
            case "obtainElementAttr" ->
                    obtainElementAttr(handleContext, eleList.getJSONObject(0).getString("eleName"),
                            eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue"),
                            step.getString("text"), step.getString("content"));
            case "logElementAttr" ->
                    logElementAttr(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getString("text"));
            case "sendKeys" ->
                    sendKeys(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
            case "sendKeysByActions" ->
                    sendKeysByActions(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
            case "isExistEle" ->
                    isExistEle(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getBoolean("content"));
            case "isExistEleNum" -> isExistEleNum(handleContext, eleList.getJSONObject(0).getString("eleName"),
                    eleList.getJSONObject(0).getString("eleType"),
                    eleList.getJSONObject(0).getString("eleValue"),
                    step.getString("content"),
                    step.getInteger("text"), IOS_ELEMENT_TYPE);
            case "scrollToEle" -> scrollToEle(handleContext, eleList.getJSONObject(0).getString("eleName"),
                    eleList.getJSONObject(0).getString("eleType"),
                    eleList.getJSONObject(0).getString("eleValue"),
                    step.getInteger("content"),
                    step.getString("text"));
            case "clear" ->
                    clear(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"));
            case "longPress" ->
                    longPress(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getInteger("content"));
            case "swipe" ->
                    swipePoint(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                            , eleList.getJSONObject(1).getString("eleName"), eleList.getJSONObject(1).getString("eleValue"));
            case "swipeByDefinedDirection" ->
                    swipeByDefinedDirection(handleContext, step.getString("text"), step.getInteger("content"));
            case "swipe2" ->
                    swipe(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue")
                            , eleList.getJSONObject(1).getString("eleName"), eleList.getJSONObject(1).getString("eleType"), eleList.getJSONObject(1).getString("eleValue"));
            case "tap" ->
                    tap(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue"));
            case "longPressPoint" ->
                    longPressPoint(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                            , step.getInteger("content"));
            case "pause" -> pause(handleContext, step.getInteger("content"));
            case "checkImage" ->
                    checkImage(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleValue")
                            , step.getDouble("content"));
            case "stepScreen" -> stepScreen(handleContext);
            case "openApp" -> openApp(handleContext, step.getString("text"));
            case "terminate" -> terminate(handleContext, step.getString("text"));
            case "install" -> install(handleContext, step.getString("text"));
            case "uninstall" -> uninstall(handleContext, step.getString("text"));
            case "runBack" -> runBackground(handleContext, step.getLong("content"));
            case "lock" -> lock(handleContext);
            case "unLock" -> unLock(handleContext);
            case "keyCode" -> keyCode(handleContext, step.getString("content"));
            case "assertEquals", "assertNotEquals", "assertTrue", "assertNotTrue" -> {
                String actual = TextHandler.replaceTrans(step.getString("text"), globalParams);
                String expect = TextHandler.replaceTrans(step.getString("content"), globalParams);
                asserts(handleContext, actual, expect, step.getString("stepType"));
            }
            case "getTextValue" ->
                    globalParams.put(step.getString("content"), getText(handleContext, eleList.getJSONObject(0).getString("eleName")
                            , eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue")));
            case "sendKeyForce" -> sendKeyForce(handleContext, step.getString("content"));
            case "monkey" ->
                    runMonkey(handleContext, step.getJSONObject("content"), step.getJSONArray("text").toJavaList(JSONObject.class));
            case "publicStep" -> publicStep(handleContext, step.getString("content"), step.getJSONArray("pubSteps"));
            case "findElementInterval" ->
                    setFindElementInterval(handleContext, step.getInteger("content"), step.getInteger("text"));
            case "setPasteboard" -> setPasteboard(handleContext, step.getString("content"));
            case "getPasteboard" -> globalParams.put(step.getString("content"), getPasteboard(handleContext));
            case "runScript" -> runScript(handleContext, step.getString("content"), step.getString("text"));
            case "startPocoDriver" ->
                    startPocoDriver(handleContext, step.getString("content"), step.getInteger("text"));
            case "setDefaultFindPocoElementInterval" ->
                    setDefaultFindPocoElementInterval(handleContext, step.getInteger("content"), step.getInteger("text"));
            case "isExistPocoEle" ->
                    isExistPocoEle(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getBoolean("content"));
            case "isExistPocoEleNum" -> isExistEleNum(handleContext,
                    eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                    , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"),
                    step.getInteger("text"), POCO_ELEMENT_TYPE);
            case "pocoClick" ->
                    pocoClick(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"));
            case "pocoLongPress" ->
                    pocoLongPress(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue")
                            , step.getInteger("content"));
            case "pocoSwipe" ->
                    pocoSwipe(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue")
                            , eleList.getJSONObject(1).getString("eleName"), eleList.getJSONObject(1).getString("eleType"), eleList.getJSONObject(1).getString("eleValue"));
            case "setTheRealPositionOfTheWindow" ->
                    setTheRealPositionOfTheWindow(handleContext, step.getString("content"));
            case "getPocoElementAttr" ->
                    getPocoElementAttr(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getString("text"), step.getString("content"));
            case "obtainPocoElementAttr" ->
                    obtainPocoElementAttr(handleContext, eleList.getJSONObject(0).getString("eleName"),
                            eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue"),
                            step.getString("text"), step.getString("content"));
            case "logPocoElementAttr" ->
                    logPocoElementAttr(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getString("text"));
            case "getPocoTextValue" ->
                    globalParams.put(step.getString("content"), getPocoText(handleContext, eleList.getJSONObject(0).getString("eleName")
                            , eleList.getJSONObject(0).getString("eleType"), eleList.getJSONObject(0).getString("eleValue")));
            case "freezeSource" -> freezeSource(handleContext);
            case "thawSource" -> thawSource(handleContext);
            case "closePocoDriver" -> closePocoDriver(handleContext);
            case "setSnapshotMaxDepth" -> setSnapshotMaxDepth(handleContext, step.getInteger("content"));
            case "iteratorPocoElement" ->
                    iteratorPocoElement(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"));
            case "iteratorIOSElement" ->
                    iteratorIOSElement(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"));
            // <= 2.5版本的文本断言语法(包括原生，webView，Poco三类)，保留做兼容，老版本升级上来的存量用例继续可用
            case "getText" ->
                    getTextAndAssert(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
            case "getPocoText" ->
                    getPocoTextAndAssert(handleContext, eleList.getJSONObject(0).getString("eleName"), eleList.getJSONObject(0).getString("eleType")
                            , eleList.getJSONObject(0).getString("eleValue"), step.getString("content"));
            // > 2.5版本的文本断言语法，支持指定断言的方式
            case "assertText" -> getElementTextAndAssertWithOperation(handleContext,
                    eleList.getJSONObject(0).getString("eleName"),
                    eleList.getJSONObject(0).getString("eleType"),
                    eleList.getJSONObject(0).getString("eleValue"),
                    step.getString("content"), step.getString("text"),
                    IOS_ELEMENT_TYPE);
            case "assertPocoText" -> getElementTextAndAssertWithOperation(handleContext,
                    eleList.getJSONObject(0).getString("eleName"),
                    eleList.getJSONObject(0).getString("eleType"),
                    eleList.getJSONObject(0).getString("eleValue"),
                    step.getString("content"), step.getString("text"),
                    POCO_ELEMENT_TYPE);
        }
        switchType(step, handleContext);
    }

    public void switchType(JSONObject stepJson, HandleContext handleContext) throws Throwable {
        Integer error = stepJson.getInteger("error");
        String stepDes = handleContext.getStepDes();
        String detail = handleContext.getDetail();
        Throwable e = handleContext.getE();
        if (e != null && !"exit while".equals(e.getMessage()) && !e.getMessage().startsWith("IGNORE:")) {
            switch (error) {
                case ErrorType.IGNORE:
                    if (stepJson.getInteger("conditionType").equals(ConditionEnum.NONE.getValue())) {
                        log.sendStepLog(StepType.PASS, stepDes + "异常！已忽略...", detail);
                        handleContext.clear();
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
        } else if (!"IGNORE".equals(stepDes)) {
            log.sendStepLog(StepType.PASS, stepDes, detail);
        }
    }
}