package com.sonic.agent.automation;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.interfaces.ResultDetailStatus;
import com.sonic.agent.interfaces.LogType;
import com.sonic.agent.tools.LogTool;
import com.sonic.agent.interfaces.PlatformType;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidStartScreenRecordingOptions;
import io.appium.java_client.android.appmanagement.AndroidTerminateApplicationOptions;
import io.appium.java_client.remote.AndroidMobileCapabilityType;
import io.appium.java_client.remote.AutomationName;
import io.appium.java_client.remote.MobileCapabilityType;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.util.Base64Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

/**
 * @author ZhouYiXun
 * @des 安卓自动化处理类
 * @date 2021/8/16 20:10
 */
public class AndroidStepHandler {
    LogTool log = new LogTool();
    private AndroidDriver androidDriver;
    private AppiumDriverLocalService appiumDriverLocalService;
    //是否已经发送过测试结果
    private Boolean isSendStatus = false;
    //包版本
    private String version = "";
    //测试起始时间
    private long startTime;
    //测试的包名
    private String testPackage = "";

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
     * @return void
     * @author ZhouYiXun
     * @des 启动appium服务
     * @date 2021/8/16 20:01
     */
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
            log.sendStepLog(LogType.PASS, "连接设备驱动成功", "");
        } catch (Exception e) {
            log.sendStepLog(LogType.ERROR, "连接设备驱动失败！", "");
            //测试标记为失败
            setResultDetailStatus(ResultDetailStatus.FAIL);
            if (appiumDriverLocalService.isRunning()) {
                appiumDriverLocalService.stop();
            }
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
                //终止测试包
                if (!testPackage.equals("")) {
                    terminate(testPackage);
                }
                androidDriver.quit();
                log.sendStepLog(LogType.PASS, "退出连接设备", "");
            }
        } catch (Exception e) {
            log.sendStepLog(LogType.WARN, "测试终止异常！请检查设备连接状态", "");
            //测试异常
            setResultDetailStatus(ResultDetailStatus.WARN);
            e.printStackTrace();
        }
        if (appiumDriverLocalService.isRunning()) {
            appiumDriverLocalService.stop();
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //默认通过
        setResultDetailStatus(ResultDetailStatus.PASS);
        //发送运行时长
        if (version.length() > 0) {
            log.sendElapsed((int) (Calendar.getInstance().getTimeInMillis() - startTime), PlatformType.ANDROID, version);
        }
    }

    /**
     * @param packageName
     * @return void
     * @author ZhouYiXun
     * @des 终止app
     * @date 2021/8/16 23:46
     */
    public void terminate(String packageName) {
        try {
            androidDriver.terminateApp(packageName, new AndroidTerminateApplicationOptions().withTimeout(Duration.ofMillis(1000)));
            log.sendStepLog(LogType.PASS, "终止App", "应用包名： " + packageName);
        } catch (Exception e) {
            log.sendStepLog(LogType.WARN, "终止App异常！", "应用包名： " + packageName);
        }
    }

    /**
     * @param status
     * @return void
     * @author ZhouYiXun
     * @des 发送测试状态
     * @date 2021/8/16 23:46
     */
    public void setResultDetailStatus(int status) {
        if (!isSendStatus) {
            log.sendStatusLog(status);
            isSendStatus = true;
        }
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
            log.sendStepLog(LogType.ERROR, "设备电量过低!", "跳过本次测试...");
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
                JSONObject memResult = new JSONObject();
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
                memResult.put("data", perform);
                log.sendPerLog(performName, memResult);
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
                detail.put(attr.getKey().replace("-", ""), attr.getValue());
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
            log.sendRecordLog(false, "");
        }
    }

    /**
     * @param udId
     * @return void
     * @author ZhouYiXun
     * @des 停止录像
     * @date 2021/8/16 23:56
     */
    public void stopRecord(String udId) {
        File recordDir = new File("test-output/record");
        if (!recordDir.exists()) {
            recordDir.mkdirs();
        }
        long timeMillis = Calendar.getInstance().getTimeInMillis();
        String fileName = recordDir + File.separator + timeMillis + "_" + udId.substring(0, 4) + ".mp4";
        File uploadFile = new File(fileName);
        try {
            //加锁防止内存泄漏
            synchronized (AndroidStepHandler.class) {
                FileOutputStream fileOutputStream = new FileOutputStream(uploadFile);
                byte[] bytes = Base64Utils.decodeFromString((androidDriver.stopRecordingScreen()));
                fileOutputStream.write(bytes);
                fileOutputStream.close();
            }
            SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd");
            String time = sf.format(timeMillis);
            log.sendRecordLog(true, "上传方法");
        } catch (Exception e) {
            log.sendRecordLog(false, "");
        }
    }
}
