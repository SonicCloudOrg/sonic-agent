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
package org.cloud.sonic.agent.bridge.ios;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.common.maps.IOSDeviceManagerMap;
import org.cloud.sonic.agent.common.maps.IOSInfoMap;
import org.cloud.sonic.agent.common.maps.IOSProcessMap;
import org.cloud.sonic.agent.event.AgentRegisteredEvent;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ProcessCommandTool;
import org.cloud.sonic.common.tools.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
@DependsOn({"iOSThreadPoolInit"})
@Component
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class SibTool implements ApplicationListener<AgentRegisteredEvent> {
    private static final Logger logger = LoggerFactory.getLogger(SibTool.class);
    @Value("${modules.ios.wda-bundle-id}")
    private String getBundleId;
    private static String bundleId;
    private static String sib = new File("plugins" + File.separator + "sonic-ios-bridge").getAbsolutePath();
    @Value("${sonic.sib}")
    private String sibVersion;

    @Bean
    public void setEnv() {
        bundleId = getBundleId;
    }

    @Override
    public void onApplicationEvent(@NonNull AgentRegisteredEvent event) {
        init();
        logger.info("Enable iOS Module");
    }

    public void init() {
        List<String> ver = ProcessCommandTool.getProcessLocalCommand(String.format("%s version", sib));
        if (ver.size() == 0 || !ver.get(0).equals(sibVersion)) {
            logger.info(String.format("Start sonic-ios-bridge failed! Please use [chmod -R 777 %s], if still failed, you can try with [sudo]", new File("plugins").getAbsolutePath()));
            System.exit(0);
        }
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
                        } else if (r.getString("status").equals("offline")) {
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
        logger.info("iOS devices listening...");
    }

    public static List<String> getDeviceList() {
        List<String> result = new ArrayList<>();
        String commandLine = "%s devices";
        List<String> data = ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib));
        for (String a : data) {
            if (a.length() == 0) {
                break;
            }
            result.add(a.substring(0, a.indexOf(" ")));
        }
        return result;
    }

    public static void sendDisConnectStatus(JSONObject jsonObject) {
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("udId", jsonObject.getString("serialNumber"));
        deviceStatus.put("status", DeviceStatus.DISCONNECTED);
        deviceStatus.put("size", IOSInfoMap.getSizeMap().get(jsonObject.getString("serialNumber")));
        deviceStatus.put("agentId", AgentZookeeperRegistry.currentAgent.getId());
        logger.info("iOS devices: " + jsonObject.getString("serialNumber") + " OFFLINE!");
        SpringTool.getBean(AgentManagerTool.class).devicesStatus(deviceStatus);
        IOSDeviceManagerMap.getMap().remove(jsonObject.getString("serialNumber"));
    }

    public static void sendOnlineStatus(JSONObject jsonObject) {
        JSONObject detail = jsonObject.getJSONObject("deviceDetail");
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("udId", jsonObject.getString("serialNumber"));
        deviceStatus.put("name", detail.getString("deviceName"));
        deviceStatus.put("model", detail.getString("generationName"));
        deviceStatus.put("status", DeviceStatus.ONLINE);
        deviceStatus.put("platform", PlatformType.IOS);
        deviceStatus.put("version", detail.getString("productVersion"));
        deviceStatus.put("size", IOSInfoMap.getSizeMap().get(jsonObject.getString("serialNumber")));
        deviceStatus.put("cpu", detail.getString("cpuArchitecture"));
        deviceStatus.put("manufacturer", "APPLE");
        deviceStatus.put("agentId", AgentZookeeperRegistry.currentAgent.getId());
        logger.info("iOS Devices: " + jsonObject.getString("serialNumber") + " ONLINE!");
        SpringTool.getBean(AgentManagerTool.class).devicesStatus(deviceStatus);
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
                    logger.info(udId + " WebDriverAgent start timeout!");
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

    public static void launch(String udId, String pkg) {
        String commandLine = "%s app launch -u %s -b %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, pkg));
    }

    public static void uninstall(String udId, String pkg) {
        String commandLine = "%s app uninstall -u %s -b %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, pkg));
    }
}
