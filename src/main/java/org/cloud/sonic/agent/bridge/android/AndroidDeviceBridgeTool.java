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
package org.cloud.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.*;
import org.cloud.sonic.agent.common.maps.AndroidThreadMap;
import org.cloud.sonic.agent.common.maps.AndroidWebViewMap;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.tests.android.AndroidBatteryThread;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ScheduleTool;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.FileTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author ZhouYiXun
 * @des ADB工具类
 * @date 2021/08/16 19:26
 */
@ConditionalOnProperty(value = "modules.android.enable", havingValue = "true")
@DependsOn({"androidThreadPoolInit"})
@Component
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class AndroidDeviceBridgeTool implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(AndroidDeviceBridgeTool.class);
    public static AndroidDebugBridge androidDebugBridge = null;
    private static String uiaApkVersion;
    private static String apkVersion;
    private static RestTemplate restTemplate;
    @Value("${sonic.saa}")
    private String ver;
    @Value("${sonic.saus}")
    private String uiaVer;
    @Autowired
    private RestTemplate restTemplateBean;

    @Autowired
    private AndroidDeviceStatusListener androidDeviceStatusListener;


    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        init();
        logger.info("Enable Android Module");
    }

    /**
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 获取系统安卓SDK路径
     * @date 2021/8/16 19:35
     */
    private static String getADBPathFromSystemEnv() {
        String path = System.getenv("ANDROID_HOME");
        if (path != null) {
            path += File.separator + "platform-tools" + File.separator + "adb";
        } else {
            logger.error("Get ANDROID_HOME env failed!");
            return null;
        }
        return path;
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des 定义方法
     * @date 2021/8/16 19:36
     */
    public void init() {
        apkVersion = ver;
        uiaApkVersion = uiaVer;
        restTemplate = restTemplateBean;
        //获取系统SDK路径
        String systemADBPath = getADBPathFromSystemEnv();
        //添加设备上下线监听
        AndroidDebugBridge.addDeviceChangeListener(androidDeviceStatusListener);
        try {
            AndroidDebugBridge.init(false);
            //开始创建ADB
            androidDebugBridge = AndroidDebugBridge.createBridge(systemADBPath, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            if (androidDebugBridge != null) {
                logger.info("Android devices listening...");
            }
        } catch (IllegalStateException e) {
            logger.warn("AndroidDebugBridge has been init!");
        }
        int count = 0;
        //获取设备列表，超时后退出
        while (androidDebugBridge.hasInitialDeviceList() == false) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            count++;
            if (count > 200) {
                break;
            }
        }
        ScheduleTool.scheduleAtFixedRate(
                new AndroidBatteryThread(),
                AndroidBatteryThread.DELAY,
                AndroidBatteryThread.DELAY,
                AndroidBatteryThread.TIME_UNIT
        );
    }

    /**
     * @return com.android.ddmlib.IDevice[]
     * @author ZhouYiXun
     * @des 获取真实在线设备列表
     * @date 2021/8/16 19:38
     */
    public static IDevice[] getRealOnLineDevices() {
        if (androidDebugBridge != null) {
            return androidDebugBridge.getDevices();
        } else {
            return null;
        }
    }

    /**
     * @param iDevice
     * @return void
     * @author ZhouYiXun
     * @des 重启设备
     * @date 2021/8/16 19:41
     */
    public static void reboot(IDevice iDevice) {
        if (iDevice != null) {
            executeCommand(iDevice, "reboot");
        }
    }

    public static void shutdown(IDevice iDevice) {
        if (iDevice != null) {
            executeCommand(iDevice, "reboot -p");
        }
    }

    /**
     * @param udId 设备序列号
     * @return com.android.ddmlib.IDevice
     * @author ZhouYiXun
     * @des 根据udId获取iDevice对象
     * @date 2021/8/16 19:42
     */
    public static IDevice getIDeviceByUdId(String udId) {
        IDevice iDevice = null;
        IDevice[] iDevices = AndroidDeviceBridgeTool.getRealOnLineDevices();
        if (iDevices.length == 0) {
            return null;
        }
        for (IDevice device : iDevices) {
            //如果设备是在线状态并且序列号相等，则就是这个设备
            if (device.getState().equals(IDevice.DeviceState.ONLINE)
                    && device.getSerialNumber().equals(udId)) {
                iDevice = device;
                break;
            }
        }
        if (iDevice == null) {
            logger.info("Device 「{}」 has not connected!", udId);
        }
        return iDevice;
    }

    /**
     * @param iDevice
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 获取屏幕大小
     * @date 2021/8/16 19:44
     */
    public static String getScreenSize(IDevice iDevice) {
        String size = "";
        try {
            size = executeCommand(iDevice, "wm size");
            if (size.contains("Override size")) {
                size = size.substring(size.indexOf("Override size"));
            } else {
                size = size.split(":")[1];
            }
            //注意顺序问题
            size = size.trim()
                    .replace(":", "")
                    .replace("Override size", "")
                    .replace("\r", "")
                    .replace("\n", "")
                    .replace(" ", "");
            if (size.length() > 20) {
                size = "unknown";
            }
        } catch (Exception e) {
            logger.info("Get screen size failed, ignore when plug in moment...");
        }
        return size;
    }

    /**
     * @param iDevice
     * @param command
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 发送shell指令给对应设备
     * @date 2021/8/16 19:47
     */
    public static String executeCommand(IDevice iDevice, String command) {
        CollectingOutputReceiver output = new CollectingOutputReceiver();
        try {
            iDevice.executeShellCommand(command, output, 0, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.info("Send shell command {} to device {} failed."
                    , command, iDevice.getSerialNumber());
            logger.error(e.getMessage());
        }
        return output.getOutput();
    }

    public static boolean checkSonicApkVersion(IDevice iDevice) {
        String all = executeCommand(iDevice, "dumpsys package org.cloud.sonic.android");
        if (!all.contains("versionName=" + apkVersion)) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean checkUiaApkVersion(IDevice iDevice) {
        String all = executeCommand(iDevice, "dumpsys package io.appium.uiautomator2.server");
        if (!all.contains("versionName=" + uiaApkVersion)) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * @param iDevice
     * @param port
     * @param service
     * @return void
     * @author ZhouYiXun
     * @des 同adb forward指令，将设备内进程的端口暴露给pc本地，但是只能转发给localhost，不能转发给ipv4
     * @date 2021/8/16 19:52
     */
    public static void forward(IDevice iDevice, int port, String service) {
        try {
            logger.info("{} device {} port forward to {}", iDevice.getSerialNumber(), service, port);
            iDevice.createForward(port, service, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public static void forward(IDevice iDevice, int port, int target) {
        try {
            logger.info("{} device {} forward to {}", iDevice.getSerialNumber(), target, port);
            iDevice.createForward(port, target);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * @param iDevice
     * @param port
     * @param serviceName
     * @return void
     * @author ZhouYiXun
     * @des 去掉转发
     * @date 2021/8/16 19:53
     */
    public static void removeForward(IDevice iDevice, int port, String serviceName) {
        try {
            logger.info("cancel {} device {} port forward to {}", iDevice.getSerialNumber(), serviceName, port);
            iDevice.removeForward(port, serviceName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public static void removeForward(IDevice iDevice, int port, int target) {
        try {
            logger.info("cancel {} device {} forward to {}", iDevice.getSerialNumber(), target, port);
            iDevice.removeForward(port, target);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * @param iDevice
     * @param localPath
     * @param remotePath
     * @return void
     * @author ZhouYiXun
     * @des 推送文件
     * @date 2021/8/16 19:59
     */
//    public static void pushLocalFile(IDevice iDevice, String localPath, String remotePath) {
//        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
//            //使用iDevice的pushFile方法好像有bug，暂时用命令行去推送
//            ProcessBuilder pb = new ProcessBuilder(new String[]{getADBPathFromSystemEnv(), "-s", iDevice.getSerialNumber(), "push", localPath, remotePath});
//            pb.redirectErrorStream(true);
//            try {
//                pb.start();
//            } catch (IOException e) {
//                logger.error(e.getMessage());
//                return;
//            }
//        });
//    }

    /**
     * @param iDevice
     * @param keyNum
     * @return void
     * @author ZhouYiXun
     * @des 输入对应按键
     * @date 2021/8/16 19:59
     */
    public static void pressKey(IDevice iDevice, int keyNum) {
        executeCommand(iDevice, String.format("input keyevent %s", keyNum));
    }

    public static void install(IDevice iDevice, String path) throws InstallException {
        iDevice.installPackage(path,
                true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES
                , "-r", "-t", "-g");
    }

    public static void uninstall(IDevice iDevice, String bundleId) throws InstallException {
        iDevice.uninstallPackage(bundleId);
    }

    public static void forceStop(IDevice iDevice, String bundleId) {
        executeCommand(iDevice, String.format("am force-stop %s", bundleId));
    }

    public static void activateApp(IDevice iDevice, String bundleId) {
        executeCommand(iDevice, String.format("monkey -p %s -c android.intent.category.LAUNCHER 1", bundleId));
    }

    /**
     * @param iDevice
     * @param key
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 获取设备配置信息
     * @date 2021/8/16 20:01
     */
    public static String getProperties(IDevice iDevice, String key) {
        return iDevice.getProperty(key);
    }

    /**
     * @param sdk
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 根据sdk匹配对应的文件
     * @date 2021/8/16 20:01
     */
    public static String matchMiniCapFile(String sdk) {
        String filePath;
        if (Integer.valueOf(sdk) < 16) {
            filePath = "minicap-nopie";
        } else {
            filePath = "minicap";
        }
        return filePath;
    }

    public static void startProxy(IDevice iDevice, String host, int port) {
        executeCommand(iDevice, String.format("settings put global http_proxy %s:%d", host, port));
    }

    public static void clearProxy(IDevice iDevice) {
        executeCommand(iDevice, "settings put global http_proxy :0");
    }

    public static void screen(IDevice iDevice, String type) {
        int p = getScreen(iDevice);
        try {
            switch (type) {
                case "abort":
                    executeCommand(iDevice, "content insert --uri content://settings/system --bind name:s:accelerometer_rotation --bind value:i:0");
                    break;
                case "add":
                    if (p == 3) {
                        p = 0;
                    } else {
                        p++;
                    }
                    executeCommand(iDevice, "content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:" + p);
                    break;
                case "sub":
                    if (p == 0) {
                        p = 3;
                    } else {
                        p--;
                    }
                    executeCommand(iDevice, "content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:" + p);
                    break;
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public static int getScreen(IDevice iDevice) {
        try {
            return Integer.parseInt(executeCommand(iDevice, "settings get system user_rotation")
                    .trim().replaceAll("\n", "")
                    .replace("\t", ""));
        } catch (Exception e) {
            logger.error(e.getMessage());
            return 0;
        }
    }

    public static int getOrientation(IDevice iDevice) {
        String inputs = executeCommand(iDevice, "dumpsys input");
        if (inputs.indexOf("SurfaceOrientation") != -1) {
            String orientationS = inputs.substring(inputs.indexOf("SurfaceOrientation")).trim();
            int o = BytesTool.getInt(orientationS.substring(20, orientationS.indexOf("\n")));
            return o;
        } else {
            inputs = executeCommand(iDevice, "dumpsys window displays");
            String orientationS = inputs.substring(inputs.indexOf("cur=")).trim();
            String sizeT = orientationS.substring(4, orientationS.indexOf(" "));
            String[] size = sizeT.split("x");
            if (BytesTool.getInt(size[0]) > BytesTool.getInt(size[1])) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    public static int[] getDisplayOfAllScreen(IDevice iDevice, int width, int height, int ori) {
        String out = executeCommand(iDevice, "dumpsys window windows");
        String[] windows = out.split("Window #");
        String packageName = getCurrentPackage(iDevice);
        int offsetx = 0, offsety = 0;
        if (packageName != null) {
            for (String window : windows) {
                if (window.contains("package=" + packageName)) {
                    String patten = "Frames: containing=\\[(\\d+\\.?\\d*),(\\d+\\.?\\d*)]\\[(\\d+\\.?\\d*),(\\d+\\.?\\d*)]";
                    Pattern pattern = Pattern.compile(patten);
                    Matcher m = pattern.matcher(window);
                    while (m.find()) {
                        if (m.groupCount() != 4) break;
                        offsetx = Integer.parseInt(m.group(1));
                        offsety = Integer.parseInt(m.group(2));
                        width = Integer.parseInt(m.group(3));
                        height = Integer.parseInt(m.group(4));

                        if (ori == 1 || ori == 3) {
                            int tempOffsetX = offsetx;
                            int tempWidth = width;

                            offsetx = offsety;
                            offsety = tempOffsetX;
                            width = height;
                            height = tempWidth;
                        }

                        width -= offsetx;
                        height -= offsety;
                    }
                }
            }
        }
        return new int[]{offsetx, offsety, width, height};
    }

    public static String getCurrentPackage(IDevice iDevice) {
        Integer api = Integer.parseInt(iDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL));
        String cmd = AndroidDeviceBridgeTool.executeCommand(iDevice,
                String.format("dumpsys window %s", ((api != null && api >= 29) ? "displays" : "windows")));
        String result = "";
        try {
            String start = cmd.substring(cmd.indexOf("mCurrentFocus="));
            String end = start.substring(0, start.indexOf("/"));
            result = end.substring(end.lastIndexOf(" ") + 1);
        } catch (Exception e) {
        }
        if (result.length() == 0) {
            try {
                String start = cmd.substring(cmd.indexOf("mFocusedApp="));
                String startCut = start.substring(0, start.indexOf("/"));
                String packageName = startCut.substring(startCut.lastIndexOf(" ") + 1);
                result = packageName;
            } catch (Exception e) {
            }
        }
        return result;
    }

    public static String getCurrentActivity(IDevice iDevice) {
        Integer api = Integer.parseInt(iDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL));
        String cmd = AndroidDeviceBridgeTool.executeCommand(iDevice,
                String.format("dumpsys window %s", ((api != null && api >= 29) ? "displays" : "windows")));
        String result = "";
        try {
            String start = cmd.substring(cmd.indexOf("mCurrentFocus="));
            String end = start.substring(start.indexOf("/") + 1);
            result = end.substring(0, end.indexOf("}"));
        } catch (Exception e) {
        }
        if (result.length() == 0) {
            try {
                String start = cmd.substring(cmd.indexOf("mFocusedApp="));
                String end = start.substring(start.indexOf("/") + 1);
                String endCut = end.substring(0, end.indexOf(" "));
                result = endCut;
            } catch (Exception e) {
            }
        }
        return result;
    }

    public static void pushYadb(IDevice iDevice) {
        executeCommand(iDevice, "rm -rf /data/local/tmp/yadb");
        File yadbLocalFile = new File("plugins" + File.separator + "yadb");
        try {
            iDevice.pushFile(yadbLocalFile.getAbsolutePath(), "/data/local/tmp/yadb");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (SyncException e) {
            e.printStackTrace();
        }
        executeCommand(iDevice, "chmod 777 /data/local/tmp/yadb");
    }

    public static void pushToCamera(IDevice iDevice, String url) {
        try {
            File image = DownloadTool.download(url);
            iDevice.pushFile(image.getAbsolutePath(), "/sdcard/DCIM/Camera/" + image.getName());
            executeCommand(iDevice, "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/Camera/" + image.getName());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (SyncException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    public static void searchDevice(IDevice iDevice) {
        executeCommand(iDevice, "am start -n org.cloud.sonic.android/.plugin.activityPlugin.SearchActivity");
    }

    public static void controlBattery(IDevice iDevice, int type) {
        if (type == 0) {
            executeCommand(iDevice, "dumpsys battery unplug && dumpsys battery set status 1");
        }
        if (type == 1) {
            executeCommand(iDevice, "dumpsys battery reset");
        }
    }

    public static String pullFile(IDevice iDevice, String path) {
        String result = null;
        File base = new File("test-output" + File.separator + "pull");
        String filename = base.getAbsolutePath() + File.separator + UUID.randomUUID();
        File file = new File(filename);
        file.mkdirs();
        String system = System.getProperty("os.name").toLowerCase();
        String processName = String.format("process-%s-pull-file", iDevice.getSerialNumber());
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
        try {
            Process process = null;
            String command = String.format("%s pull %s %s", getADBPathFromSystemEnv(), path, file.getAbsolutePath());
            if (system.contains("win")) {
                process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
            } else if (system.contains("linux") || system.contains("mac")) {
                process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            }
            GlobalProcessMap.getMap().put(processName, process);
            boolean isRunning;
            int wait = 0;
            do {
                Thread.sleep(500);
                wait++;
                isRunning = false;
                List<ProcessHandle> processHandleList = process.children().collect(Collectors.toList());
                if (processHandleList.size() == 0) {
                    if (process.isAlive()) {
                        isRunning = true;
                    }
                } else {
                    for (ProcessHandle p : processHandleList) {
                        if (p.isAlive()) {
                            isRunning = true;
                            break;
                        }
                    }
                }
                if (wait >= 20) {
                    process.children().forEach(ProcessHandle::destroy);
                    process.destroy();
                    break;
                }
            } while (isRunning);
            File re = new File(filename + File.separator + (path.lastIndexOf("/") == -1 ? path : path.substring(path.lastIndexOf("/"))));
            result = UploadTools.upload(re, "packageFiles");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileTool.deleteDir(file);
        }
        return result;
    }

    public static int startUiaServer(IDevice iDevice) throws InstallException {
        Thread s = AndroidThreadMap.getMap().get(String.format("%s-uia-thread", iDevice.getSerialNumber()));
        if (s != null) {
            s.interrupt();
        }
        if (!checkUiaApkVersion(iDevice)) {
            iDevice.uninstallPackage("io.appium.uiautomator2.server");
            iDevice.uninstallPackage("io.appium.uiautomator2.server.test");
            iDevice.installPackage("plugins/sonic-appium-uiautomator2-server.apk",
                    true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES
                    , "-r", "-t");
            iDevice.installPackage("plugins/sonic-appium-uiautomator2-server-test.apk",
                    true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES
                    , "-r", "-t");
            executeCommand(iDevice, "appops set io.appium.uiautomator2.server RUN_IN_BACKGROUND allow");
            executeCommand(iDevice, "appops set io.appium.uiautomator2.server.test RUN_IN_BACKGROUND allow");
            executeCommand(iDevice, "dumpsys deviceidle whitelist +io.appium.uiautomator2.server");
            executeCommand(iDevice, "dumpsys deviceidle whitelist +io.appium.uiautomator2.server.test");
        }
        int port = PortTool.getPort();
        UiaThread uiaThread = new UiaThread(iDevice, port);
        uiaThread.start();
        int wait = 0;
        while (!uiaThread.getIsOpen()) {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            wait++;
            if (wait >= 20) {
                break;
            }
        }
        AndroidThreadMap.getMap().put(String.format("%s-uia-thread", iDevice.getSerialNumber()), uiaThread);
        return port;
    }

    static class UiaThread extends Thread {

        private IDevice iDevice;
        private int port;
        private boolean isOpen = false;

        public UiaThread(IDevice iDevice, int port) {
            this.iDevice = iDevice;
            this.port = port;
        }

        public boolean getIsOpen() {
            return isOpen;
        }

        @Override
        public void run() {
            forward(iDevice, port, 6790);
            try {
                iDevice.executeShellCommand("am instrument -w io.appium.uiautomator2.server.test/androidx.test.runner.AndroidJUnitRunner",
                        new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                logger.info(res);
                                if (res.contains("io.appium.uiautomator2.server.test.AppiumUiAutomator2Server:")) {
                                    try {
                                        Thread.sleep(2000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    isOpen = true;
                                }
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }, 0, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
            } finally {
                AndroidDeviceBridgeTool.removeForward(iDevice, port, 6790);
            }
        }

        @Override
        public void interrupt() {
            super.interrupt();
        }
    }

    public static void clearWebView(IDevice iDevice) {
        List<JSONObject> has = AndroidWebViewMap.getMap().get(iDevice);
        if (has != null && has.size() > 0) {
            for (JSONObject j : has) {
                AndroidDeviceBridgeTool.removeForward(iDevice, j.getInteger("port"), j.getString("name"));
            }
        }
        AndroidWebViewMap.getMap().remove(iDevice);
    }

    public static File getChromeDriver(IDevice iDevice, String packageName) throws IOException {
        String chromeVersion = "";
        List<JSONObject> result = getWebView(iDevice);
        if (result.size() > 0) {
            for (JSONObject j : result) {
                if (packageName.equals(j.getString("package"))) {
                    chromeVersion = j.getString("version");
                    break;
                }
            }
        }
        clearWebView(iDevice);
        if (chromeVersion.length() == 0) {
            return null;
        } else {
            chromeVersion = chromeVersion.replace("Chrome/", "");
        }
        String system = System.getProperty("os.name").toLowerCase();
        File search = new File(String.format("webview/%s_chromedriver%s", chromeVersion,
                (system.contains("win") ? ".exe" : "")));
        if (search.exists()) {
            return search;
        }
        int end = (chromeVersion.indexOf(".") != -1 ? chromeVersion.indexOf(".") : chromeVersion.length() - 1);
        String major = chromeVersion.substring(0, end);
        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<String> infoEntity =
                restTemplate.exchange(String.format("https://chromedriver.storage.googleapis.com/LATEST_RELEASE_%s", major), HttpMethod.GET, new HttpEntity(headers), String.class);
        if (system.contains("win")) {
            system = "win32";
        } else if (system.contains("linux")) {
            system = "linux64";
        } else {
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.contains("aarch64")) {
                // fix m1 arm version obtained is lower than 87 for special processing
                String driverList = restTemplate.exchange(String.format("https://registry.npmmirror.com/-/binary/chromedriver/%s/", infoEntity.getBody()), HttpMethod.GET, new HttpEntity(headers), String.class).getBody();
                for (Object obj : JSONArray.parseArray(driverList)) {
                    JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                    String fullName = jsonObject.getString("name");
                    if (fullName.contains("m1") || fullName.contains("arm")) {
                        system = fullName.substring(fullName.indexOf("mac"), fullName.indexOf("."));
                        break;
                    }
                }
            } else {
                system = "mac64";
            }
        }
        File file = DownloadTool.download(String.format("https://cdn.npmmirror.com/binaries/chromedriver/%s/chromedriver_%s.zip", infoEntity.getBody(), system));
        File driver = FileTool.unZipChromeDriver(file, chromeVersion);
        return driver;
    }

    public static List<JSONObject> getWebView(IDevice iDevice) {
        clearWebView(iDevice);
        List<JSONObject> has = new ArrayList<>();
        Set<String> webSet = new HashSet<>();
        List<String> out = Arrays.asList(AndroidDeviceBridgeTool
                .executeCommand(iDevice, "cat /proc/net/unix").split("\n"));
        for (String w : out) {
            if (w.contains("webview") || w.contains("WebView") || w.contains("_devtools_remote")) {
                if (w.contains("@") && w.indexOf("@") + 1 < w.length()) {
                    webSet.add(w.substring(w.indexOf("@") + 1));
                }
            }
        }
        List<JSONObject> result = new ArrayList<>();
        if (webSet.size() > 0) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            for (String ws : webSet) {
                int port = PortTool.getPort();
                AndroidDeviceBridgeTool.forward(iDevice, port, ws);
                JSONObject j = new JSONObject();
                j.put("port", port);
                j.put("name", ws);
                has.add(j);
                JSONObject r = new JSONObject();
                r.put("port", port);
                try {
                    ResponseEntity<LinkedHashMap> infoEntity =
                            restTemplate.exchange("http://localhost:" + port + "/json/version", HttpMethod.GET, new HttpEntity(headers), LinkedHashMap.class);
                    if (infoEntity.getStatusCode() == HttpStatus.OK) {
                        r.put("version", infoEntity.getBody().get("Browser"));
                        r.put("package", infoEntity.getBody().get("Android-Package"));
                    }
                } catch (Exception e) {
                    continue;
                }
                ResponseEntity<JSONArray> responseEntity =
                        restTemplate.exchange("http://localhost:" + port + "/json/list", HttpMethod.GET, new HttpEntity(headers), JSONArray.class);
                if (responseEntity.getStatusCode() == HttpStatus.OK) {
                    List<JSONObject> child = new ArrayList<>();
                    for (Object e : responseEntity.getBody()) {
                        LinkedHashMap objE = (LinkedHashMap) e;
                        JSONObject c = new JSONObject();
                        c.put("favicon", objE.get("faviconUrl"));
                        c.put("title", objE.get("title"));
                        c.put("url", objE.get("url"));
                        c.put("id", objE.get("id"));
                        child.add(c);
                    }
                    r.put("children", child);
                    result.add(r);
                }
            }
            AndroidWebViewMap.getMap().put(iDevice, has);
        }
        return result;
    }
}
