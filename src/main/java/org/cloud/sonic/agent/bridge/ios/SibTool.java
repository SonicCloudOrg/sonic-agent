package org.cloud.sonic.agent.bridge.ios;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.common.maps.IOSDeviceManagerMap;
import org.cloud.sonic.agent.common.maps.IOSProcessMap;
import org.cloud.sonic.agent.common.maps.IOSSizeMap;
import org.cloud.sonic.agent.netty.NettyClientHandler;
import org.cloud.sonic.agent.netty.NettyThreadPool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ProcessCommandTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@ConditionalOnProperty(value = "modules.ios.enable", havingValue = "true")
@DependsOn({"iOSThreadPoolInit", "nettyMsgInit"})
@Component
public class SibTool implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(SibTool.class);
    @Value("${modules.ios.wda-bundle-id}")
    private String getBundleId;
    private static String bundleId;
    private String sib = new File("plugins/sonic-ios-bridge").getAbsolutePath();

    @Bean
    public void setEnv() {
        bundleId = getBundleId;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        logger.info("开启iOS相关功能");
    }


    public void init() {
        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
            String processName = "sib";
            if (GlobalProcessMap.getMap().get(processName) != null) {
                Process ps = GlobalProcessMap.getMap().get(processName);
                ps.children().forEach(ProcessHandle::destroy);
                ps.destroy();
            }
            Process listenProcess = null;
            String commandLine = "%s devices listen -d";
            String system = System.getProperty("os.name").toLowerCase();
            try {
                if (system.contains("win")) {
                    listenProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sib)});
                } else if (system.contains("linux") || system.contains("mac")) {
                    listenProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sib)});
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(listenProcess.getInputStream()));
            String s;
            while (listenProcess.isAlive()) {
                try {
                    if ((s = stdInput.readLine()) != null) {
                        JSONObject r = JSONObject.parseObject(s);
                        if (r.getString("status").equals("online")) {
                            sendOnlineStatus(r);
                        } else {
                            sendDisConnectStatus(r);
                        }
                        logger.info(s);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            GlobalProcessMap.getMap().put(processName, listenProcess);
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

    public static void sendDisConnectStatus(JSONObject jsonObject) {
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("msg", "deviceDetail");
        deviceStatus.put("udId", jsonObject.getString("serialNumber"));
        deviceStatus.put("status", "DISCONNECTED");
        deviceStatus.put("size", IOSSizeMap.getMap().get(jsonObject.getString("serialNumber")));
        logger.info("iOS设备：" + jsonObject.getString("serialNumber") + " 下线！");
        NettyThreadPool.send(deviceStatus);
        IOSDeviceManagerMap.getMap().remove(jsonObject.getString("serialNumber"));
    }

    public static void sendOnlineStatus(JSONObject jsonObject) {
        JSONObject detail = jsonObject.getJSONObject("deviceDetail");
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("msg", "deviceDetail");
        deviceStatus.put("udId", jsonObject.getString("serialNumber"));
        deviceStatus.put("name", detail.getString("deviceName"));
        deviceStatus.put("model", detail.getString("generationName"));
        deviceStatus.put("status", "ONLINE");
        deviceStatus.put("platform", PlatformType.IOS);
        deviceStatus.put("version", detail.getString("productVersion"));
        deviceStatus.put("size", IOSSizeMap.getMap().get(jsonObject.getString("serialNumber")));
        deviceStatus.put("cpu", detail.getString("cpuArchitecture"));
        deviceStatus.put("manufacturer", "APPLE");
        logger.info("iOS设备：" + jsonObject.getString("serialNumber") + " 上线！");
        NettyThreadPool.send(deviceStatus);
        IOSDeviceManagerMap.getMap().remove(jsonObject.getString("serialNumber"));
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
        synchronized (SibTool.class) {
            List<Process> processList;
            if (IOSProcessMap.getMap().get(udId) != null) {
                processList = IOSProcessMap.getMap().get(udId);
                for (Process p : processList) {
                    if (p != null) {
                        p.children().forEach(ProcessHandle::destroy);
                        p.destroy();
                    }
                }
            }
            int port = PortTool.getPort();
            Process wdaProcess = null;
            String commandLine = "tidevice -u " + udId +
                    " wdaproxy" + " -B " + bundleId +
                    " --port " + port;
            String system = System.getProperty("os.name").toLowerCase();
            if (system.contains("win")) {
                wdaProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", commandLine});
            } else if (system.contains("linux") || system.contains("mac")) {
                wdaProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", commandLine});
            }
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(wdaProcess.getErrorStream()));
            String s;
            int wait = 0;
            while (wdaProcess.isAlive()) {
                if ((s = stdInput.readLine()) != null) {
                    if (s.contains("WebDriverAgent start successfully")) {
                        break;
                    }
                } else {
                    Thread.sleep(500);
                    wait++;
                    if (wait >= 120) {
                        logger.info(udId + " WebDriverAgent启动超时！");
                        return 0;
                    }
                }
                logger.info(s);
            }
            processList = new ArrayList<>();
            processList.add(wdaProcess);
            IOSProcessMap.getMap().put(udId, processList);
            return port;
        }
    }

    public static int relayImg(String udId) throws IOException, InterruptedException {
        int port = PortTool.getPort();
        Process relayProcess = null;
        String commandLine = "tidevice -u " + udId +
                " relay " + port + " " + 9100;
        String system = System.getProperty("os.name").toLowerCase();
        if (system.contains("win")) {
            relayProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", commandLine});
        } else if (system.contains("linux") || system.contains("mac")) {
            relayProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", commandLine});
        }
        List<Process> processList;
        if (IOSProcessMap.getMap().get(udId) != null) {
            processList = IOSProcessMap.getMap().get(udId);
        } else {
            processList = new ArrayList<>();
        }
        processList.add(relayProcess);
        IOSProcessMap.getMap().put(udId, processList);
        Thread.sleep(1000);
        return port;
    }

    public static void reboot(String udId) {
        ProcessCommandTool.getProcessLocalCommand("tidevice -u " + udId + " reboot");
    }
}
