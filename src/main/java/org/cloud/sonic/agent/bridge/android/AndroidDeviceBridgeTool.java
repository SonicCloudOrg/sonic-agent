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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.*;
import org.cloud.sonic.agent.event.AgentRegisteredEvent;
import org.cloud.sonic.agent.tests.android.AndroidBatteryThread;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ZhouYiXun
 * @des ADB工具类
 * @date 2021/08/16 19:26
 */
@ConditionalOnProperty(value = "modules.android.enable", havingValue = "true")
@DependsOn({"androidThreadPoolInit"})
@Component
public class AndroidDeviceBridgeTool implements ApplicationListener<AgentRegisteredEvent> {
    private static final Logger logger = LoggerFactory.getLogger(AndroidDeviceBridgeTool.class);
    public static AndroidDebugBridge androidDebugBridge = null;
    private AndroidBatteryThread androidBatteryThread = null;
    private static String apkVersion;
    @Value("${sonic.saa}")
    private String ver;

    @Autowired
    private AndroidDeviceStatusListener androidDeviceStatusListener;


    @Override
    public void onApplicationEvent(@NonNull AgentRegisteredEvent event) {
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
        //获取系统SDK路径
        String systemADBPath = getADBPathFromSystemEnv();
        //添加设备上下线监听
        AndroidDebugBridge.addDeviceChangeListener(androidDeviceStatusListener);
        try {
            AndroidDebugBridge.init(false);
        } catch (IllegalStateException e) {
            logger.warn("AndroidDebugBridge has been init!");
        }
        //开始创建ADB
        androidDebugBridge = AndroidDebugBridge.createBridge(systemADBPath, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        if (androidDebugBridge != null) {
            logger.info("Android devices listening...");
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
        if (androidBatteryThread == null || !androidBatteryThread.isAlive()) {
            androidBatteryThread = new AndroidBatteryThread();
            androidBatteryThread.start();
        }
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
            logger.info("Device has not connected!");
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
        executeCommand(iDevice, "am start -n org.cloud.sonic.android/.SearchActivity");
    }

    public static void controlBattery(IDevice iDevice, int type) {
        if (type == 0) {
            executeCommand(iDevice, "dumpsys battery unplug && dumpsys battery set status 1");
        }
        if (type == 1) {
            executeCommand(iDevice, "dumpsys battery reset");
        }
    }

    public static JSONObject getPocoTree(IDevice iDevice, String type) {
        int port = PortTool.getPort();
        int target = 0;
        switch (type) {
            case "unity":
                target = 5001;
                break;
            case "other":
                target = 15004;
        }
        forward(iDevice, port, target);
        AtomicReference<JSONObject> result = new AtomicReference<>();
        int finalTarget = target;
        Thread pocoThread = new Thread(() -> {
            Socket poco = null;
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                poco = new Socket("localhost", finalTarget);
                inputStream = poco.getInputStream();
                outputStream = poco.getOutputStream();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("jsonrpc", "2.0");
                jsonObject.put("method", "Dump");
                jsonObject.put("params", Arrays.asList(true));
                jsonObject.put("id", 0);
                int len = jsonObject.toJSONString().length();
                ByteBuffer header = ByteBuffer.allocate(4);
                header.put(BytesTool.intToByteArray(len), 0, 4);
                header.flip();
                ByteBuffer body = ByteBuffer.allocate(len);
                body.put(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8), 0, len);
                body.flip();
                ByteBuffer total = ByteBuffer.allocate(len + 4);
                total.put(header.array());
                total.put(body.array());
                total.flip();
                outputStream.write(total.array());
                while (poco.isConnected() && !Thread.interrupted()) {
                    byte[] head = new byte[4];
                    inputStream.read(head);
                    byte[] buffer = new byte[BytesTool.toInt(head)];
                    int realLen;
                    realLen = inputStream.read(buffer);
                    if (realLen >= 0) {
                        result.set(JSON.parseObject(new String(buffer)).getJSONObject("result"));
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (poco != null && poco.isConnected()) {
                    try {
                        poco.close();
                        logger.info("poco socket closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                        logger.info("poco input stream closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (outputStream != null) {
                    try {
                        outputStream.close();
                        logger.info("poco output stream closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                removeForward(iDevice, port, finalTarget);
            }
        });
        pocoThread.start();
        int wait = 0;
        while (result.get() == null) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            wait++;
            if (wait >= 20) {
                pocoThread.interrupt();
            }
        }
        return result.get();
    }

    /**
     * @param udId
     * @param packageName
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 获取app版本信息
     * @date 2021/8/16 15:29
     */
//    public static String getAppOnlyVersion(String udId, String packageName) {
//        IDevice iDevice = getIDeviceByUdId(udId);
//        //实质是获取安卓开发在gradle定义的versionName来定义版本号
//        String version = executeCommand(iDevice, String.format("pm dump %s | grep 'versionName'", packageName));
//        version = version.substring(version.indexOf("=") + 1, version.length() - 1);
//        if (version.length() > 50) {
//            version = version.substring(0, version.indexOf(" ") + 1);
//        }
//        //因为不同设备获取的信息不一样，所以需要去掉\r、\n
//        return version.replace("\r", "").replace("\n", "");
//    }
}
