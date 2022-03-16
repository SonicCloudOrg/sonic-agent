package org.cloud.sonic.agent.bridge.ios;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.common.maps.IOSDeviceManagerMap;
import org.cloud.sonic.agent.common.maps.IOSProcessMap;
import org.cloud.sonic.agent.common.maps.IOSInfoMap;
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
import java.net.Socket;
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
    private static String sib = new File("plugins/sonic-ios-bridge").getAbsolutePath();

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
        String commandLine = "%s devices";
        List<String> data = ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib));
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
        deviceStatus.put("size", IOSInfoMap.getSizeMap().get(jsonObject.getString("serialNumber")));
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
        deviceStatus.put("size", IOSInfoMap.getSizeMap().get(jsonObject.getString("serialNumber")));
        deviceStatus.put("cpu", detail.getString("cpuArchitecture"));
        deviceStatus.put("manufacturer", "APPLE");
        logger.info("iOS设备：" + jsonObject.getString("serialNumber") + " 上线！");
        NettyThreadPool.send(deviceStatus);
        IOSInfoMap.getDetailMap().put(jsonObject.getString("serialNumber"), detail);
        IOSDeviceManagerMap.getMap().remove(jsonObject.getString("serialNumber"));
    }

    public static String getName(String udId) {
        String r = IOSInfoMap.getDetailMap().get(udId).getString("deviceName");
        return r != null ? r : "";
    }

    public static int[] startWda(String udId) throws IOException, InterruptedException {
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
        Socket wda = PortTool.getBindSocket();
        Socket mjpeg = PortTool.getBindSocket();
        int wdaPort = PortTool.releaseAndGetPort(wda);
        int mjpegPort = PortTool.releaseAndGetPort(mjpeg);
        Process wdaProcess = null;
        String commandLine = "%s run wda -u %s -b %s --mjpeg-remote-port 9100" +
                " --server-remote-port 8200 --mjpeg-local-port %d --server-local-port %d";
        String system = System.getProperty("os.name").toLowerCase();
        if (system.contains("win")) {
            wdaProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId, bundleId, mjpegPort, wdaPort)});
        } else if (system.contains("linux") || system.contains("mac")) {
            wdaProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId, bundleId, mjpegPort, wdaPort)});
        }
        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(wdaProcess.getInputStream()));
        String s;
        int wait = 0;
        while (wdaProcess.isAlive()) {
            if ((s = stdInput.readLine()) != null) {
                logger.info(s);
                if (s.contains("WebDriverAgent server start successful")) {
                    break;
                }
            } else {
                Thread.sleep(500);
                wait++;
                if (wait >= 120) {
                    logger.info(udId + " WebDriverAgent启动超时！");
                    return new int[]{0, 0};
                }
            }
        }
        processList = new ArrayList<>();
        processList.add(wdaProcess);
        IOSProcessMap.getMap().put(udId, processList);
        return new int[]{wdaPort, mjpegPort};
    }

    public static void reboot(String udId) {
        String commandLine = "%s reboot -u %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId));
    }

    public static void install(String udId, String path) {
        String commandLine = "%s app install -u %s -p %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, path));
    }

    public static JSONObject getAppList(String udId) {
        String commandLine = "%s app list -u %s -j";
        List<String> a = ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId));
        if (a.size() > 0) {
            return JSONObject.parseObject(a.get(0));
        } else {
            return new JSONObject();
        }
    }

    public static void launch(String udId,String pkg){
        String commandLine = "%s app launch -u %s -b %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, pkg));
    }

    public static void uninstall(String udId,String pkg){
        String commandLine = "%s app uninstall -u %s -b %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, pkg));
    }
}
