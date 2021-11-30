package com.sonic.agent.bridge.ios;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.interfaces.PlatformType;
import com.sonic.agent.maps.IOSDeviceManagerMap;
import com.sonic.agent.maps.IOSProcessMap;
import com.sonic.agent.netty.NettyThreadPool;
import com.sonic.agent.tools.PortTool;
import com.sonic.agent.tools.ProcessCommandTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@ConditionalOnProperty(value = "modules.ios.enable", havingValue = "true")
@DependsOn({"iOSThreadPoolInit", "nettyMsgInit"})
@Component
public class TIDeviceTool {
    private static final Logger logger = LoggerFactory.getLogger(TIDeviceTool.class);
    @Value("${modules.ios.wda-bundle-id}")
    private String getBundleId;
    private static String bundleId;
    @Value("${modules.ios.wda-ipa-name}")
    private String getIpa;
    private static String ipa;

    @Bean
    public void setEnv() {
        bundleId = getBundleId;
        ipa = getIpa;
    }

    public TIDeviceTool() {
        logger.info("开启iOS相关功能");
        init();
    }

    public static void init() {
        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
            List<String> aDevice = ProcessCommandTool.getProcessLocalCommand("tidevice list");
            for (String udId : aDevice) {
                sendOnlineStatus(udId.substring(0, udId.indexOf(" ")));
            }
            while (true) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<String> newList = ProcessCommandTool.getProcessLocalCommand("tidevice list");
                if (!aDevice.equals(newList)) {
                    for (String udId : newList) {
                        if (!aDevice.contains(udId.substring(0, udId.indexOf(" ")))) {
                            sendOnlineStatus(udId.substring(0, udId.indexOf(" ")));
                        }
                    }
                    for (String udId : aDevice) {
                        if (!newList.contains(udId.substring(0, udId.indexOf(" ")))) {
                            sendDisConnectStatus(udId.substring(0, udId.indexOf(" ")));
                        }
                    }
                    aDevice = newList;
                }
            }
        });
        logger.info("iOS设备监听已开启");
    }

    public static List<String> getDeviceList() {
        List<String> result = new ArrayList<>();
        List<String> data = ProcessCommandTool.getProcessLocalCommand("tidevice list");
        for (String a : data) {
            result.add(a.substring(0, a.indexOf(" ")));
        }
        return result;
    }

    public static void sendDisConnectStatus(String udId) {
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("msg", "deviceDetail");
        deviceStatus.put("udId", udId);
        deviceStatus.put("status", "DISCONNECTED");
        logger.info("iOS设备：" + udId + " 下线！");
        NettyThreadPool.send(deviceStatus);
        IOSDeviceManagerMap.getMap().remove(udId);
    }

    public static void sendOnlineStatus(String udId) {
        JSONObject info = getInfo(udId);
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("msg", "deviceDetail");
        deviceStatus.put("udId", udId);
        deviceStatus.put("name", info.getString("name"));
        deviceStatus.put("model", info.getString("model"));
        deviceStatus.put("status", "ONLINE");
        deviceStatus.put("platform", PlatformType.IOS);
        deviceStatus.put("version", info.getString("version"));
        deviceStatus.put("size", "未知");
        deviceStatus.put("cpu", info.getString("cpu"));
        deviceStatus.put("manufacturer", "APPLE");
        logger.info("iOS设备：" + udId + " 上线！");
        NettyThreadPool.send(deviceStatus);
        IOSDeviceManagerMap.getMap().remove(udId);
    }

    public static JSONObject getInfo(String udId) {
        JSONObject result = new JSONObject();
        List<String> s = ProcessCommandTool.getProcessLocalCommand("tidevice -u " + udId + " info");
        for (String json : s) {
            String j = json.replaceAll(" ", "").replaceAll("\n", "").replaceAll("\r", "").trim();
            if (j.contains("MarketName:")) {
                result.put("model", j.replaceAll("MarketName:", ""));
            }
            if (j.contains("DeviceName:")) {
                result.put("name", j.replaceAll("DeviceName:", ""));
            }
            if (j.contains("ProductVersion:")) {
                result.put("version", j.replaceAll("ProductVersion:", ""));
            }
            if (j.contains("CPUArchitecture:")) {
                result.put("cpu", j.replaceAll("CPUArchitecture:", ""));
            }
        }
        return result;
    }

    public static String getName(String udId) {
        List<String> s = ProcessCommandTool.getProcessLocalCommand("tidevice -u " + udId + " info");
        for (String json : s) {
            String j = json.replaceAll(" ", "").replaceAll("\n", "").replaceAll("\r", "").trim();
            if (j.contains("DeviceName:")) {
                return j.replaceAll("DeviceName:", "");
            }
        }
        return "";
    }

    public static int startWda(String udId) throws IOException, InterruptedException {
        synchronized (TIDeviceTool.class) {
            Runtime.getRuntime().exec("tidevice -u " + udId + " uninstall " + bundleId);
            Thread.sleep(500);
            Runtime.getRuntime().exec("tidevice -u " + udId + " install plugins/" + ipa);
            Thread.sleep(1000);
            int port = PortTool.getPort();
            Process wdaProcess = Runtime.getRuntime().exec("tidevice -u " + udId +
                    " wdaproxy" + " -B " + bundleId +
                    " --port " + port);
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(wdaProcess.getInputStream()));
            String s;
            while ((s = stdInput.readLine()) != null) {
                if (s.contains("WebDriverAgent start successfully")) {
                    logger.info(udId + " wda启动完毕！");
                    break;
                } else {
                    Thread.sleep(500);
                }
            }
            List<Process> processList;
            if (IOSProcessMap.getMap().get(udId) != null) {
                processList = IOSProcessMap.getMap().get(udId);
                for (Process p : processList) {
                    if (p != null) {
                        p.destroy();
                    }
                }
            }
            processList = new ArrayList<>();
            processList.add(wdaProcess);
            IOSProcessMap.getMap().put(udId, processList);
            return port;
        }
    }

    public static int relayImg(String udId) throws IOException {
        int port = PortTool.getPort();
        Process relayProcess = Runtime.getRuntime().exec("tidevice -u " + udId +
                " relay " + port + " " + 9100);
        List<Process> processList;
        if (IOSProcessMap.getMap().get(udId) != null) {
            processList = IOSProcessMap.getMap().get(udId);
        } else {
            processList = new ArrayList<>();
        }
        processList.add(relayProcess);
        IOSProcessMap.getMap().put(udId, processList);
        return port;
    }

    public static void reboot(String udId) {
        ProcessCommandTool.getProcessLocalCommand("tidevice -u " + udId + " reboot");
    }
}
