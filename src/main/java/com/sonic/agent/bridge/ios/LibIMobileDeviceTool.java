package com.sonic.agent.bridge.ios;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.maps.IOSDeviceManagerMap;
import com.sonic.agent.netty.NettyThreadPool;
import com.sonic.agent.tools.ProcessCommandTool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

//@ConditionalOnProperty(value = "modules.ios.enable", havingValue = "true")
//@DependsOn({"iOSThreadPoolInit", "nettyMsgInit"})
//@Component
public class LibIMobileDeviceTool {
    private static final Logger logger = LoggerFactory.getLogger(LibIMobileDeviceTool.class);

    public LibIMobileDeviceTool() {
        init();
    }

    public static void init() {
        logger.info("开启iOS相关功能");
        if (!System.getProperty("os.name").contains("Mac")) {
            logger.info("iOS设备监听已关闭");
            return;
        }
        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
            List<String> aDevice = ProcessCommandTool.getProcessLocalCommand("idevice_id -l");
            for (String udId : aDevice) {
                sendOnlineStatus(udId);
            }
            while (true) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<String> newList = ProcessCommandTool.getProcessLocalCommand("idevice_id -l");
                if (!aDevice.equals(newList)) {
                    for (String udId : newList) {
                        if (!aDevice.contains(udId)) {
                            sendOnlineStatus(udId);
                        }
                    }
                    for (String udId : aDevice) {
                        if (!newList.contains(udId)) {
                            sendDisConnectStatus(udId);
                        }
                    }
                    aDevice = newList;
                }
            }
        });
        logger.info("iOS设备监听已开启");
    }

    public static void sendDisConnectStatus(String udId) {
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("msg", "deviceStatus");
        deviceStatus.put("serialNum", udId);
        deviceStatus.put("status", "DISCONNECTED");
        logger.info("iOS设备：" + udId + " 下线！");
        NettyThreadPool.send(deviceStatus);
        IOSDeviceManagerMap.getMap().remove(udId);
//        wdaKill(udid);
//        relayKill(udid);
    }

    public static void sendOnlineStatus(String udId) {
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("msg", "deviceStatus");
        deviceStatus.put("serialNum", udId);
        deviceStatus.put("name", getDeviceNameByUdId(udId));
        deviceStatus.put("deviceName", getProductTypeByUdId(udId));
        deviceStatus.put("status", "ONLINE");
        deviceStatus.put("api", "无");
        deviceStatus.put("platform", "IOS");
        deviceStatus.put("version", getProductVersionByUdId(udId));
        deviceStatus.put("size", "未知");
        deviceStatus.put("cpu", getCpuByUdId(udId));
        deviceStatus.put("manufacturer", "APPLE");
        logger.info("iOS设备：" + udId + " 上线！");
        NettyThreadPool.send(deviceStatus);
        IOSDeviceManagerMap.getMap().remove(udId);
    }

    public static List<String> getDeviceList() {
        return ProcessCommandTool.getProcessLocalCommand("idevice_id -l");
    }

    public static String exportCrash(String udId, String path, Boolean isKeep) {
        File recordByRmvb = new File(path);
        if (!recordByRmvb.exists()) {//判断文件目录是否存在
            recordByRmvb.mkdirs();
        }
        String command;
        if (isKeep) {
            command = "idevicecrashreport -u " + udId + " -k -e " + path;
        } else {
            command = "idevicecrashreport -u " + udId + " -e " + path;
        }
        return ProcessCommandTool.getProcessLocalCommand(command).get(0);
    }

    public static String getCpuByUdId(String udId) {
        String result = ProcessCommandTool.getProcessLocalCommand("ideviceinfo -u " + udId + " -k CPUArchitecture").get(0);
        if (result == null || result.contains("ERROR")) {
            result = "未知";
        }
        return result;
    }

    public static String getProductVersionByUdId(String udId) {
        String result = ProcessCommandTool.getProcessLocalCommand("ideviceinfo -u " + udId + " -k ProductVersion").get(0);
        if (result == null || result.contains("ERROR")) {
            result = "未知";
        }
        return result;
    }

    public static String getProductTypeByUdId(String udId) {
        String result = ProcessCommandTool.getProcessLocalCommand("ideviceinfo -u " + udId + " -k ProductType").get(0);
        if (result == null || result.contains("ERROR")) {
            result = "未知";
        }
        return result;
    }

    public static String getDeviceNameByUdId(String udId) {
        String result = ProcessCommandTool.getProcessLocalCommand("ideviceinfo -u " + udId + " -k DeviceName").get(0);
        if (result == null || result.contains("ERROR")) {
            result = "未知";
        }
        return result;
    }

    public static String reboot(String udId) {
        return ProcessCommandTool.getProcessLocalCommand("idevicediagnostics -u " + udId + " restart").get(0);
    }

    public static String getAppVersion(String udId, String packageName) {
        Process process;
        InputStreamReader inputStreamReader;
        LineNumberReader consoleInput;
        String consoleInputLine;
        String result = "";
        try {
            process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "ideviceinstaller -u " + udId + " -l -o xml"});
            inputStreamReader = new InputStreamReader(process.getInputStream());
            consoleInput = new LineNumberReader(inputStreamReader);
            while ((consoleInputLine = consoleInput.readLine()) != null) {
                result += consoleInputLine;
            }
            consoleInput.close();
            inputStreamReader.close();
            process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Document doc = Jsoup.parse(result);
        String shortVersion = "";
        String longVersion = "";
        for (Element dict : doc.body().children().get(0).children().get(0).children()) {
            Element dictChildren = dict.getElementsMatchingOwnText("CFBundleIdentifier").get(0);
            if (dictChildren.nextElementSibling().text().equals(packageName)) {
                shortVersion = dictChildren.parent().getElementsMatchingOwnText("CFBundleShortVersionString").get(0).nextElementSibling().text();
                longVersion = dictChildren.parent().getElementsMatchingOwnText("CFBundleVersion").get(0).nextElementSibling().text();
                break;
            }
        }
        if (longVersion.length() > 0) {
            String[] str = new String[longVersion.length()];
            int count = 0;
            for (int i = 0; i < str.length; i++) {
                str[i] = longVersion.substring(i, i + 1);
            }
            for (int i = 0; i < str.length; i++) {
                if (str[i].equals(".")) {
                    count++;
                }
            }
            if (count >= 3) {
                return longVersion;
            }
        }
        return shortVersion + "." + longVersion;
    }
}
