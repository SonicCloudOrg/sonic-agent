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
package org.cloud.sonic.agent.bridge.ios;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import jakarta.websocket.Session;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.*;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tests.ios.IOSBatteryThread;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ProcessCommandTool;
import org.cloud.sonic.agent.tools.ScheduleTool;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static org.cloud.sonic.agent.tools.BytesTool.sendText;

@DependsOn({"iOSThreadPoolInit"})
@Component
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class SibTool implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(SibTool.class);
    @Value("${modules.ios.wda-bundle-id}")
    private String getBundleId;

    @Value("${modules.ios.wda-xcode-project-path:default}")
    private String getXcodeProjectPath;

    private static String bundleId;
    private static String xcodeProjectPath;
    private static File sibBinary = new File("plugins" + File.separator + "sonic-ios-bridge");
    private static String sib = sibBinary.getAbsolutePath();
    private static RestTemplate restTemplate;

    @Autowired
    private RestTemplate restTemplateBean;
    private static Map<String, Integer> webViewMap = new HashMap<>();

    @Bean
    public void setEnv() {
        bundleId = getBundleId;
        xcodeProjectPath = getXcodeProjectPath;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        init();
        logger.info("Enable iOS Module");
    }

    public void init() {
        restTemplate = restTemplateBean;
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
                    listenProcess = Runtime.getRuntime()
                            .exec(new String[]{"cmd", "/c", String.format(commandLine, sib)});
                } else if (system.contains("linux") || system.contains("mac")) {
                    listenProcess = Runtime.getRuntime()
                            .exec(new String[]{"sh", "-c", String.format(commandLine, sib)});
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            InputStreamReader inputStreamReader = new InputStreamReader(listenProcess.getInputStream());
            BufferedReader stdInput = new BufferedReader(inputStreamReader);
            String s;
            while (true) {
                try {
                    if ((s = stdInput.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                JSONObject r = JSONObject.parseObject(s);
                if (r.getString("status").equals("online")) {
                    sendOnlineStatus(r);
                } else if (r.getString("status").equals("offline")) {
                    sendDisConnectStatus(r);
                }
                logger.info(s);
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStreamReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("listen done.");
            GlobalProcessMap.getMap().put(processName, listenProcess);
        });

        ScheduleTool.scheduleAtFixedRate(
                new IOSBatteryThread(),
                IOSBatteryThread.DELAY,
                IOSBatteryThread.DELAY,
                IOSBatteryThread.TIME_UNIT);

        logger.info("iOS devices listening...");
    }

    public static List<String> getDeviceList() {
        List<String> result = new ArrayList<>();
        String commandLine = "%s devices";
        List<String> data = ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib));
        for (String a : data) {
            if (a.length() == 0 || a.contains("no device")) {
                break;
            }
            if (a.contains(" ")) {
                result.add(a.substring(0, a.indexOf(" ")));
            }
        }
        return result;
    }

    public static void sendDisConnectStatus(JSONObject jsonObject) {
        if (StringUtils.hasText(jsonObject.getString("serialNumber"))) {
            JSONObject deviceStatus = new JSONObject();
            deviceStatus.put("msg", "deviceDetail");
            deviceStatus.put("udId", jsonObject.getString("serialNumber"));
            deviceStatus.put("status", DeviceStatus.DISCONNECTED);
            deviceStatus.put("platform", PlatformType.IOS);
            logger.info("iOS devices: " + jsonObject.getString("serialNumber") + " OFFLINE!");
            TransportWorker.send(deviceStatus);
            IOSDeviceManagerMap.getMap().remove(jsonObject.getString("serialNumber"));
            DevicesBatteryMap.getTempMap().remove(jsonObject.getString("serialNumber"));
        }
    }

    public static void sendOnlineStatus(JSONObject jsonObject) {
        if (StringUtils.hasText(jsonObject.getString("serialNumber"))) {
            mount(jsonObject.getString("serialNumber"));
            JSONObject detail = jsonObject.getJSONObject("deviceDetail");
            JSONObject deviceStatus = new JSONObject();
            deviceStatus.put("msg", "deviceDetail");
            deviceStatus.put("udId", jsonObject.getString("serialNumber"));
            deviceStatus.put("name", detail.getString("deviceName"));
            deviceStatus.put("model", detail.getString("generationName"));
            deviceStatus.put("status", DeviceStatus.ONLINE);
            deviceStatus.put("platform", PlatformType.IOS);
            deviceStatus.put("version", detail.getString("productVersion"));
            deviceStatus.put("size", getSize(jsonObject.getString("serialNumber")));
            deviceStatus.put("cpu", detail.getString("cpuArchitecture"));
            deviceStatus.put("manufacturer", "APPLE");
            logger.info("iOS Devices: " + jsonObject.getString("serialNumber") + " ONLINE!");
            TransportWorker.send(deviceStatus);
            IOSInfoMap.getDetailMap().put(jsonObject.getString("serialNumber"), detail);
            IOSDeviceManagerMap.getMap().remove(jsonObject.getString("serialNumber"));
            DevicesBatteryMap.getTempMap().remove(jsonObject.getString("serialNumber"));
        }
    }

    public static String getName(String udId) {
        String r = IOSInfoMap.getDetailMap().get(udId).getString("deviceName");
        return r != null ? r : "";
    }

    public static int[] startWda(String udId) throws IOException, InterruptedException {
        Socket wda = PortTool.getBindSocket();
        Socket mjpeg = PortTool.getBindSocket();
        int wdaPort = PortTool.releaseAndGetPort(wda);
        int mjpegPort = PortTool.releaseAndGetPort(mjpeg);
        return startWda(udId, wdaPort, mjpegPort);
    }

    public static int[] startWda(String udId, int wdaPort, int mjpegPort) throws IOException, InterruptedException {
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
        wdaPort = (wdaPort == 0) ? PortTool.getPort() : wdaPort;
        mjpegPort = (mjpegPort == 0) ? PortTool.getPort() : mjpegPort;
        Process wdaProcess = null;
        final Process[] iProxyProcess = {null};
        String commandLine;

        // ios17 support, but mac only
        if (isUpperThanIos17(udId)) {
            commandLine = String.format(
                    "xcodebuild -project %s -scheme WebDriverAgentRunner -destination 'id=%s' test",
                    xcodeProjectPath, udId);
        } else {
            commandLine = String.format(
                    "%s run wda -u %s -b %s --mjpeg-remote-port 9100 --server-remote-port 8100 --mjpeg-local-port %d --server-local-port %d",
                    sib, udId, bundleId, mjpegPort, wdaPort);
        }
        String system = System.getProperty("os.name").toLowerCase();
        if (system.contains("win")) {
            wdaProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", commandLine});
        } else if (system.contains("linux") || system.contains("mac")) {
            wdaProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", commandLine});
        }
        InputStreamReader inputStreamReader = new InputStreamReader(wdaProcess.getInputStream());
        BufferedReader stdInput = new BufferedReader(inputStreamReader);
        Semaphore isFinish = new Semaphore(0);

        int finalWdaPort = wdaPort;
        int finalMjpegPort = mjpegPort;

        Thread wdaThread = new Thread(() -> {
            String s;
            while (true) {
                try {
                    if ((s = stdInput.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                logger.info(s);
                if (s.contains("ServerURLHere->")) {
                    if (SibTool.isUpperThanIos17(udId)) {
                        try {
                            iProxyProcess[0] = Runtime.getRuntime().exec(
                                    new String[]{"sh", "-c", String.format("iproxy -u %s %d:8100 %d:9100 -s 0.0.0.0",
                                            udId, finalWdaPort, finalMjpegPort)});
                        } catch (IOException e) {
                            logger.info(e.getMessage());
                        }
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    isFinish.release();
                }
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStreamReader.close();
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
        if (iProxyProcess[0] != null) {
            processList.add(iProxyProcess[0]);
        }
        IOSProcessMap.getMap().put(udId, processList);
        return new int[]{wdaPort, mjpegPort};
    }

    public static void reboot(String udId) {
        String commandLine = "%s reboot -u %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId));
    }

    public static void shutdown(String udId) {
        String commandLine = "%s reboot -u %s -s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId));
    }

    public static void install(String udId, String path) {
        String commandLine;
        if (isUpperThanIos17(udId)) {
            commandLine = String.format("ideviceinstaller -u %s -i %s", udId, path);
        } else {
            commandLine = String.format("%s app install -u %s -p %s", sib, udId, path);
        }
        ProcessCommandTool.getProcessLocalCommand(commandLine);
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
            } catch (Exception e) {
                e.printStackTrace();
            }
            String processName = String.format("process-%s-syslog", udId);
            GlobalProcessMap.getMap().put(processName, ps);
            InputStreamReader inputStreamReader = new InputStreamReader(ps.getInputStream());
            BufferedReader stdInput = new BufferedReader(inputStreamReader);
            String s;
            while (true) {
                try {
                    if ((s = stdInput.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                logger.info(s);
                JSONObject appList = new JSONObject();
                appList.put("msg", "logDetail");
                appList.put("detail", s);
                sendText(session, appList.toJSONString());
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStreamReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("sys done.");
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
            } catch (Exception e) {
                e.printStackTrace();
            }
            String processName = String.format("process-%s-orientation", udId);
            GlobalProcessMap.getMap().put(processName, ps);
            InputStreamReader inputStreamReader = new InputStreamReader(ps.getInputStream());
            BufferedReader stdInput = new BufferedReader(inputStreamReader);
            String s;
            while (true) {
                try {
                    if ((s = stdInput.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                logger.info(s);
                if (s.contains("orientation") && (!s.contains("0")) && (!s.contains("failed"))) {
                    int result = switch (BytesTool.getInt(s)) {
                        case 2 -> 180;
                        case 3 -> 270;
                        case 4 -> 90;
                        default -> 0;
                    };
                    JSONObject rotation = new JSONObject();
                    rotation.put("msg", "rotation");
                    rotation.put("value", result);
                    sendText(session, rotation.toJSONString());
                }
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStreamReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("orientation watcher done.");
        }).start();
    }

    public static List<String> getAppList(String udId) {
        return getAppList(udId, null).stream().map(e -> e.getString("bundleId")).collect(Collectors.toList());
    }

    public static List<JSONObject> getAppList(String udId, Session session) {
        List<JSONObject> result = new ArrayList<>();
        Process appListProcess = null;
        String commandLine = "%s app list -u %s -j -i";
        String system = System.getProperty("os.name").toLowerCase();
        try {
            if (system.contains("win")) {
                appListProcess = Runtime.getRuntime()
                        .exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId)});
            } else if (system.contains("linux") || system.contains("mac")) {
                appListProcess = Runtime.getRuntime()
                        .exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId)});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(appListProcess.getInputStream());
        BufferedReader stdInput = new BufferedReader(inputStreamReader);
        String s;
        while (true) {
            try {
                if (StringUtils.isEmpty(s = stdInput.readLine()))
                    break;
            } catch (IOException e) {
                logger.info(e.getMessage());
                break;
            }
            try {
                JSONObject appInfo = JSON.parseObject(s);
                if (session != null) {
                    JSONObject appList = new JSONObject();
                    appList.put("msg", "appListDetail");
                    appList.put("detail", appInfo);
                    sendText(session, appList.toJSONString());
                } else {
                    result.add(appInfo);
                }
            } catch (JSONException e) {
                logger.info(e.fillInStackTrace().toString());
            }
        }
        try {
            stdInput.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            inputStreamReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("app list done.");
        return result;
    }

    public static void getProcessList(String udId, Session session) {
        Process appProcess = null;
        String commandLine = "%s ps -u %s -j";
        String system = System.getProperty("os.name").toLowerCase();
        try {
            if (system.contains("win")) {
                appProcess = Runtime.getRuntime()
                        .exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId)});
            } else if (system.contains("linux") || system.contains("mac")) {
                appProcess = Runtime.getRuntime()
                        .exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId)});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(appProcess.getInputStream());
        BufferedReader stdInput = new BufferedReader(inputStreamReader);
        String s;
        while (true) {
            try {
                if ((s = stdInput.readLine()) == null)
                    break;
            } catch (IOException e) {
                logger.info(e.getMessage());
                break;
            }
            List<JSONObject> pList = JSON.parseArray(s, JSONObject.class);
            for (JSONObject p : pList) {
                JSONObject processListDetail = new JSONObject();
                processListDetail.put("msg", "processListDetail");
                processListDetail.put("detail", p);
                sendText(session, processListDetail.toJSONString());
            }
        }
        try {
            stdInput.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            inputStreamReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("process done.");
    }

    public static void locationUnset(String udId) {
        String commandLine = "%s location unset -u %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId));
    }

    public static void locationSet(String udId, String longitude, String latitude) {
        String commandLine = "%s location set -u %s --long %s --lat %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, longitude, latitude));
    }

    public static JSONObject getBattery(String udId) {
        String commandLine = "%s battery -u %s -j";
        String res = ProcessCommandTool.getProcessLocalCommandStr(commandLine.formatted(sib, udId));
        return JSONObject.parseObject(res, JSONObject.class);
    }

    public static void launch(String udId, String pkg) {
        String commandLine = "%s app launch -u %s -b %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, pkg));
    }

    public static void kill(String udId, String pkg) {
        String commandLine = "%s app kill -u %s -b %s";
        ProcessCommandTool.getProcessLocalCommand(String.format(commandLine, sib, udId, pkg));
    }

    public static void uninstall(String udId, String pkg) {
        String commandLine;
        if (isUpperThanIos17(udId)) {
            commandLine = String.format("ideviceinstaller -u %s -U %s", udId, pkg);
        } else {
            commandLine = String.format("%s app uninstall -u %s -b %s", sib, udId, pkg);
        }
        ProcessCommandTool.getProcessLocalCommand(commandLine);
    }

    public static int battery(String udId) {
        String commandLine = "%s battery -u %s -j";
        String re = ProcessCommandTool.getProcessLocalCommandStr(String.format(commandLine, sib, udId));
        return JSON.parseObject(re).getInteger("CurrentCapacity");
    }

    public static void stopWebInspector(String udId) {
        String processName = String.format("process-%s-web-inspector", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }

    public static int startWebInspector(String udId) {
        Process ps = null;
        String commandLine = "%s webinspector -u %s -p %d --cdp";
        int port = PortTool.getPort();
        try {
            String system = System.getProperty("os.name").toLowerCase();
            if (system.contains("win")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId, port)});
            } else if (system.contains("linux") || system.contains("mac")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId, port)});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(ps.getInputStream());
        BufferedReader stdInput = new BufferedReader(inputStreamReader);
        InputStreamReader err = new InputStreamReader(ps.getErrorStream());
        BufferedReader stdInputErr = new BufferedReader(err);
        Semaphore isFinish = new Semaphore(0);
        Thread webErr = new Thread(() -> {
            String s;
            while (true) {
                try {
                    if ((s = stdInputErr.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                if (!s.equals("close send protocol")) {
                    logger.info(s);
                }
            }
            try {
                stdInputErr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                err.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("WebInspector print thread shutdown.");
        });
        webErr.start();
        Thread web = new Thread(() -> {
            String s;
            while (true) {
                try {
                    if ((s = stdInput.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                logger.info(s);
                if (s.contains("service started successfully")) {
                    isFinish.release();
                }
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStreamReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            webViewMap.remove(udId);
            logger.info("WebInspector print thread shutdown.");
        });
        web.start();
        int wait = 0;
        while (!isFinish.tryAcquire()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            wait++;
            if (wait >= 120) {
                logger.info(udId + " WebInspector start timeout!");
                return 0;
            }
        }
        String processName = String.format("process-%s-web-inspector", udId);
        GlobalProcessMap.getMap().put(processName, ps);
        return port;
    }

    public static void stopProxy(String udId, int target) {
        String processName = String.format("process-%s-proxy-%d", udId, target);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }

    public static void proxy(String udId, int local, int target) {
        stopProxy(udId, target);
        Process ps = null;
        String commandLine = "%s proxy -u %s -l %d -r %d";
        try {
            String system = System.getProperty("os.name").toLowerCase();
            if (system.contains("win")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId, local, target)});
            } else if (system.contains("linux") || system.contains("mac")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId, local, target)});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(ps.getInputStream());
        BufferedReader stdInput = new BufferedReader(inputStreamReader);
        InputStreamReader err = new InputStreamReader(ps.getErrorStream());
        BufferedReader stdInputErr = new BufferedReader(err);
        Thread proErr = new Thread(() -> {
            String s;
            while (true) {
                try {
                    if ((s = stdInputErr.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                logger.info(s);
            }
            try {
                stdInputErr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                err.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("proxy print thread shutdown.");
        });
        proErr.start();
        Thread pro = new Thread(() -> {
            String s;
            while (true) {
                try {
                    if ((s = stdInput.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                logger.info(s);
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStreamReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("proxy print thread shutdown.");
        });
        pro.start();
        String processName = String.format("process-%s-proxy-%d", udId, target);
        GlobalProcessMap.getMap().put(processName, ps);
    }

    public static int getOrientation(String udId) {
        String commandLine = "%s orientation -u %s";
        return BytesTool.getInt(ProcessCommandTool.getProcessLocalCommandStr(String.format(commandLine, sib, udId)));
    }

    public static String getSize(String udId) {
        String commandLine = "%s info -d com.apple.mobile.iTunes -u %s";
        String re = ProcessCommandTool.getProcessLocalCommandStr(String.format(commandLine, sib, udId));
        String size = "";
        try {
            JSONObject r = JSON.parseObject(re);
            size = r.getInteger("ScreenWidth") + "x" + r.getInteger("ScreenHeight");
        } catch (Throwable ignored) {
        }
        return size;
    }

    public static int getScreenScale(String udId) {
        String commandLine = "%s info -d com.apple.mobile.iTunes -u %s";
        String re = ProcessCommandTool.getProcessLocalCommandStr(String.format(commandLine, sib, udId));
        int size = 2;
        try {
            JSONObject r = JSON.parseObject(re);
            size = r.getInteger("ScreenScaleFactor");
        } catch (Throwable ignored) {
        }
        return size;
    }

    public static void mount(String udId) {
        String commandLine = "%s mount -u %s";
        String re = ProcessCommandTool.getProcessLocalCommandStr(String.format(commandLine, sib, udId));
        logger.info(re);
    }

    public static List<JSONObject> getWebView(String udId) {
        int port;
        if (webViewMap.get(udId) != null) {
            port = webViewMap.get(udId);
        } else {
            port = startWebInspector(udId);
            if (port != 0) {
                webViewMap.put(udId, port);
            } else {
                return new ArrayList<>();
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        ResponseEntity<JSONArray> responseEntity = restTemplate.exchange("http://localhost:" + port + "/json/list",
                HttpMethod.GET, new HttpEntity(headers), JSONArray.class);
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody().toJavaList(JSONObject.class);
        } else {
            return new ArrayList<>();
        }
    }

    public static void stopPerfmon(String udId) {
        String processName = String.format("process-%s-perfmon", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }

    public static void startPerfmon(String udId, String bundleId, Session session, LogUtil logUtil, int interval) {
        stopPerfmon(udId);
        Process ps = null;
        String commandLine = "%s perfmon -r %d --sys-cpu --sys-mem --sys-disk --sys-network --fps --gpu -u %s%s ";
        String system = System.getProperty("os.name").toLowerCase();
        String tail = bundleId.length() == 0 ? "" : (" --proc-cpu --proc-mem -b " + bundleId);
        try {
            if (system.contains("win")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"cmd", "/c", String.format(commandLine, sib, interval, udId, tail)});
            } else if (system.contains("linux") || system.contains("mac")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"sh", "-c", String.format(commandLine, sib, interval, udId, tail)});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        InputStreamReader inputStreamReader = new InputStreamReader(ps.getInputStream());
        BufferedReader stdInput = new BufferedReader(inputStreamReader);
        InputStreamReader err = new InputStreamReader(ps.getErrorStream());
        BufferedReader stdInputErr = new BufferedReader(err);
        Thread psErr = new Thread(() -> {
            String s;
            while (true) {
                try {
                    if ((s = stdInputErr.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                logger.info(s);
            }
            try {
                stdInputErr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                err.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("perfmon print thread shutdown.");
        });
        psErr.start();
        Thread pro = new Thread(() -> {
            String s;
            while (true) {
                try {
                    if ((s = stdInput.readLine()) == null)
                        break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                try {
                    JSONObject perf = JSON.parseObject(s);
                    if (session != null) {
                        JSONObject perfDetail = new JSONObject();
                        perfDetail.put("msg", "perfDetail");
                        perfDetail.put("detail", perf);
                        sendText(session, perfDetail.toJSONString());
                    }
                    if (logUtil != null) {
                        logUtil.sendPerLog(perf.toJSONString());
                    }
                } catch (Exception e) {
                }
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStreamReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("perfmon print thread shutdown.");
        });
        pro.start();
        String processName = String.format("process-%s-perfmon", udId);
        GlobalProcessMap.getMap().put(processName, ps);
    }

    public static void stopShare(String udId) {
        String processName = String.format("process-%s-sib-share", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }

    public static void startShare(String udId, Session session) {
        String processName = String.format("process-%s-sib-share", udId);
        String commandLine = "%s remote share -u %s -p %d";
        stopShare(udId);
        JSONObject shareJSON = new JSONObject();
        shareJSON.put("msg", "share");
        try {
            String system = System.getProperty("os.name").toLowerCase();
            Process ps = null;
            int port = PortTool.getPort();
            if (system.contains("win")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId, port)});
            } else if (system.contains("linux") || system.contains("mac")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId, port)});
            }
            GlobalProcessMap.getMap().put(processName, ps);
            shareJSON.put("port", port);
        } catch (Exception e) {
            shareJSON.put("port", 0);
            e.printStackTrace();
        } finally {
            BytesTool.sendText(session, shareJSON.toJSONString());
        }
    }

    public static void startShare(String udId, int port) {
        String processName = String.format("process-%s-sib-share", udId);
        String commandLine = "%s remote share -u %s -p %d";
        stopShare(udId);
        try {
            String system = System.getProperty("os.name").toLowerCase();
            Process ps = null;
            if (system.contains("win")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"cmd", "/c", String.format(commandLine, sib, udId, port)});
            } else if (system.contains("linux") || system.contains("mac")) {
                ps = Runtime.getRuntime()
                        .exec(new String[]{"sh", "-c", String.format(commandLine, sib, udId, port)});
            }
            GlobalProcessMap.getMap().put(processName, ps);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isUpperThanIos17(String udId) {
        JSONObject deviceInfoObj = IOSInfoMap.getDetailMap().get(udId);
        if (deviceInfoObj != null && deviceInfoObj.containsKey("productVersion")) {
            String productVersion = deviceInfoObj.getString("productVersion");
            return CompareVersionUtil.compareVersion(productVersion, "17.0") >= 0;
        }
        return false;
    }

}
