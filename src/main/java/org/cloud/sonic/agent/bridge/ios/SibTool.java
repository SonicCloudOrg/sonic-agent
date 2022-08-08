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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.*;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.cloud.sonic.agent.tests.ios.IOSBatteryThread;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ProcessCommandTool;
import org.cloud.sonic.agent.tools.ScheduleTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static org.cloud.sonic.agent.tools.BytesTool.sendText;

@ConditionalOnProperty(value = "modules.ios.enable", havingValue = "true")
@DependsOn({"iOSThreadPoolInit"})
@Component
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class SibTool implements ApplicationListener<ContextRefreshedEvent> {
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
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
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

        ScheduleTool.scheduleAtFixedRate(
                new IOSBatteryThread(),
                IOSBatteryThread.DELAY,
                IOSBatteryThread.DELAY,
                IOSBatteryThread.TIME_UNIT
        );

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
        deviceStatus.put("msg", "deviceDetail");
        deviceStatus.put("udId", jsonObject.getString("serialNumber"));
        deviceStatus.put("status", DeviceStatus.DISCONNECTED);
        deviceStatus.put("size", IOSInfoMap.getSizeMap().get(jsonObject.getString("serialNumber")));
        deviceStatus.put("platform", PlatformType.IOS);
        logger.info("iOS devices: " + jsonObject.getString("serialNumber") + " OFFLINE!");
        TransportWorker.send(deviceStatus);
        IOSDeviceManagerMap.getMap().remove(jsonObject.getString("serialNumber"));
        DevicesBatteryMap.getTempMap().remove(jsonObject.getString("serialNumber"));
    }

    public static void sendOnlineStatus(JSONObject jsonObject) {
        JSONObject detail = jsonObject.getJSONObject("deviceDetail");
        JSONObject deviceStatus = new JSONObject();
        deviceStatus.put("msg", "deviceDetail");
        deviceStatus.put("udId", jsonObject.getString("serialNumber"));
        deviceStatus.put("name", detail.getString("deviceName"));
        deviceStatus.put("model", detail.getString("generationName"));
        deviceStatus.put("status", DeviceStatus.ONLINE);
        deviceStatus.put("platform", PlatformType.IOS);
        deviceStatus.put("version", detail.getString("productVersion"));
        deviceStatus.put("size", IOSInfoMap.getSizeMap().get(jsonObject.getString("serialNumber")));
        deviceStatus.put("cpu", detail.getString("cpuArchitecture"));
        deviceStatus.put("manufacturer", "APPLE");
        logger.info("iOS Devices: " + jsonObject.getString("serialNumber") + " ONLINE!");
        TransportWorker.send(deviceStatus);
        IOSInfoMap.getDetailMap().put(jsonObject.getString("serialNumber"), detail);
        IOSDeviceManagerMap.getMap().remove(jsonObject.getString("serialNumber"));
        DevicesBatteryMap.getTempMap().remove(jsonObject.getString("serialNumber"));
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
                " --server-remote-port 8100 --mjpeg-local-port %d --server-local-port %d";
        String system = System.getProperty("os.name").toLowerCase();
        if (system.contains("win")) {
            wdaProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId, bundleId, mjpegPort, wdaPort)});
        } else if (system.contains("linux") || system.contains("mac")) {
            wdaProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId, bundleId, mjpegPort, wdaPort)});
        }
        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(wdaProcess.getInputStream()));
        Process finalWdaProcess = wdaProcess;
        Semaphore isFinish = new Semaphore(0);
        Thread wdaThread = new Thread(() -> {
            String s;
            while (finalWdaProcess.isAlive()) {
                try {
                    if ((s = stdInput.readLine()) != null) {
                        logger.info(s);
                        if (s.contains("WebDriverAgent server start successful")) {
                            isFinish.release();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("WebDriverAgent print thread shutdown.");
        });
        wdaThread.start();
        int wait = 0;
        while (!isFinish.tryAcquire()) {
            Thread.sleep(500);
            wait++;
            if (wait >= 120) {
                logger.info(udId + " WebDriverAgent start timeout!");
                return new int[]{0, 0};
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

    public static void stopSysLog(String udId) {
        String processName = String.format("process-%s-syslog", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }

    public static void getSysLog(String udId, String filter, Session session) {
        new Thread(() -> {
            stopSysLog(udId);
            String system = System.getProperty("os.name").toLowerCase();
            Process ps = null;
            String commandLine = "%s syslog -u %s";
            if (filter != null && filter.length() > 0) {
                commandLine += String.format(" -f %s", filter);
            }
            try {
                if (system.contains("win")) {
                    ps = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId)});
                } else if (system.contains("linux") || system.contains("mac")) {
                    ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId)});
                }
                String processName = String.format("process-%s-syslog", udId);
                GlobalProcessMap.getMap().put(processName, ps);
                BufferedReader stdInput = new BufferedReader(new
                        InputStreamReader(ps.getInputStream()));
                String s;
                while (ps.isAlive()) {
                    if ((s = stdInput.readLine()) != null) {
                        logger.info(s);
                        try {
                            JSONObject appList = new JSONObject();
                            appList.put("msg", "logDetail");
                            appList.put("detail", s);
                            sendText(session, appList.toJSONString());
                        } catch (Exception e) {
                            logger.info(s);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void stopOrientationWatcher(String udId) {
        String processName = String.format("process-%s-orientation", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }

    public static void orientationWatcher(String udId, Session session) {
        new Thread(() -> {
            String system = System.getProperty("os.name").toLowerCase();
            Process ps = null;
            String commandLine = "%s orientation -w -u %s";
            try {
                if (system.contains("win")) {
                    ps = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId)});
                } else if (system.contains("linux") || system.contains("mac")) {
                    ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId)});
                }
                String processName = String.format("process-%s-orientation", udId);
                GlobalProcessMap.getMap().put(processName, ps);
                BufferedReader stdInput = new BufferedReader(new
                        InputStreamReader(ps.getInputStream()));
                String s;
                while (ps.isAlive()) {
                    if ((s = stdInput.readLine()) != null) {
                        logger.info(s);
                        if (s.contains("orientation") && (!s.contains("0")) && (!s.contains("failed"))) {
                            try {
                                int result = 0;
                                switch (BytesTool.getInt(s)) {
                                    case 1:
                                        result = 0;
                                        break;
                                    case 2:
                                        result = 180;
                                        break;
                                    case 3:
                                        result = 270;
                                        break;
                                    case 4:
                                        result = 90;
                                        break;
                                }
                                JSONObject rotation = new JSONObject();
                                rotation.put("msg", "rotation");
                                rotation.put("value", result);
                                sendText(session, rotation.toJSONString());
                            } catch (Exception e) {
                                logger.info(s);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void getAppList(String udId, Session session) {
        Process appListProcess = null;
        String commandLine = "%s app list -u %s -j -i";
        String system = System.getProperty("os.name").toLowerCase();
        try {
            if (system.contains("win")) {
                appListProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId)});
            } else if (system.contains("linux") || system.contains("mac")) {
                appListProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId)});
            }
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(appListProcess.getInputStream()));
            String s;
            while (appListProcess.isAlive()) {
                if ((s = stdInput.readLine()) != null) {
                    try {
                        JSONObject appList = new JSONObject();
                        appList.put("msg", "appListDetail");
                        appList.put("detail", JSON.parseObject(s));
                        sendText(session, appList.toJSONString());
                    } catch (Exception e) {
                        logger.info(s);
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    public static void getProcessList(String udId, Session session) {
        Process appProcess = null;
        String commandLine = "%s ps -u %s -j";
        String system = System.getProperty("os.name").toLowerCase();
        try {
            if (system.contains("win")) {
                appProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId)});
            } else if (system.contains("linux") || system.contains("mac")) {
                appProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId)});
            }
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(appProcess.getInputStream()));
            String s;
            while (appProcess.isAlive()) {
                if ((s = stdInput.readLine()) != null) {
                    try {
                        List<JSONObject> pList = JSON.parseArray(s, JSONObject.class);
                        for (JSONObject p : pList) {
                            JSONObject processListDetail = new JSONObject();
                            processListDetail.put("msg", "processListDetail");
                            processListDetail.put("detail", p);
                            sendText(session, processListDetail.toJSONString());
                        }
                    } catch (Exception e) {
                        logger.info(s);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void locationUnset(String udId) {
        String commandLine = "%s location unset -u %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId));
    }

    public static void locationSet(String udId, String longitude, String latitude) {
        String commandLine = "%s location set -u %s --long %s --lat %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, longitude, latitude));
    }

    public static JSONObject getAllDevicesBattery() {
        String commandLine = "%s battery -j";
        String res = ProcessCommandTool.getProcessLocalCommandStr(commandLine.formatted(sib));
        return JSONObject.parseObject(res, JSONObject.class);
    }

    public static void launch(String udId, String pkg) {
        String commandLine = "%s app launch -u %s -b %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, pkg));
    }

    public static void uninstall(String udId, String pkg) {
        String commandLine = "%s app uninstall -u %s -b %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, pkg));
    }

    public static int battery(String udId) {
        String commandLine = "%s battery -u %s -j";
        String re = ProcessCommandTool.getProcessLocalCommandStr(String.format(commandLine, sib, udId));
        return JSON.parseObject(re).getInteger("level");
    }
}
