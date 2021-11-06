package com.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.*;
import com.sonic.agent.tools.DownImageTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author ZhouYiXun
 * @des ADB工具类
 * @date 2021/08/16 19:26
 */
@DependsOn({"androidThreadPoolInit", "rabbitMsgInit"})
@Component
public class AndroidDeviceBridgeTool {
    private static final Logger logger = LoggerFactory.getLogger(AndroidDeviceBridgeTool.class);
    private static AndroidDebugBridge androidDebugBridge = null;
    private static AndroidDeviceStatusListener androidDeviceStatusListener = new AndroidDeviceStatusListener();

    public AndroidDeviceBridgeTool() {
        init();
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
            logger.error("获取ANDROID_HOME环境变量失败！");
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
    public static void init() {
        //获取系统SDK路径
        String systemADBPath = getADBPathFromSystemEnv();
        //添加设备上下线监听
        androidDebugBridge.addDeviceChangeListener(androidDeviceStatusListener);
        AndroidDebugBridge.init(false);
        //开始创建ADB
        androidDebugBridge = AndroidDebugBridge.createBridge(systemADBPath, true);
        if (androidDebugBridge != null) {
            logger.info("安卓设备监听已开启");
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
     * @param udId
     * @return com.android.ddmlib.IDevice
     * @author ZhouYiXun
     * @des 根据udId获取iDevice对象
     * @date 2021/8/16 19:42
     */
    public static IDevice getIDeviceByUdId(String udId) {
        IDevice iDevice = null;
        for (IDevice device : AndroidDeviceBridgeTool.getRealOnLineDevices()) {
            //如果设备是在线状态并且序列号相等，则就是这个设备
            if (device.getState().equals(IDevice.DeviceState.ONLINE)
                    && device.getSerialNumber().equals(udId)) {
                iDevice = device;
                break;
            }
        }
        if (iDevice == null) {
            logger.info("设备未连接！");
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
            //不同机型获取的结果有偏差，需要去掉空格、/r、/n和异常情况
            size = executeCommand(iDevice, "wm size").split(":")[1].trim()
                    .replace("\r", "").replace("\n", "")
                    .replace("Override size", "");
            if (size.length() > 20) {
                size = "未知";
            }
        } catch (Exception e) {
            logger.info("获取屏幕尺寸失败！");
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
            logger.info("发送shell指令 {} 给设备 {} 异常！"
                    , command, iDevice.getSerialNumber());
            logger.error(e.getMessage());
        }
        return output.getOutput();
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
            logger.info("{} 设备 {} 服务端口转发到：{}", iDevice.getSerialNumber(), service, port);
            iDevice.createForward(port, service, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
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
            logger.info("{} 设备 {} 服务端口取消转发到：{}", iDevice.getSerialNumber(), serviceName, port);
            iDevice.removeForward(port, serviceName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
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

    /**
     * @param sdk
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 根据sdk匹配对应的文件
     * @date 2021/8/16 20:01
     */
    public static String matchMiniTouchFile(String sdk) {
        String filePath;
        if (Integer.valueOf(sdk) < 16) {
            filePath = "minitouch-nopie";
        } else {
            filePath = "minitouch";
        }
        return filePath;
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
            File image = DownImageTool.download(url);
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

    /**
     * @param iDevice
     * @param quality
     * @return void
     * @author ZhouYiXun
     * @des 开启miniCap服务
     * @date 2021/8/16 20:04
     */
    public static void startMiniCapServer(IDevice iDevice, int quality, int screen, Session session) throws AdbCommandRejectedException, IOException, SyncException, TimeoutException {
        //先删除原有路径下的文件，防止上次出错后停止，再次打开会报错的情况
        executeCommand(iDevice, "rm -rf /data/local/tmp/minicap*");
        //获取cpu信息
        String cpuAbi = getProperties(iDevice, "ro.product.cpu.abi");
        //获取安卓sdk版本
        String androidSdkVersion = getProperties(iDevice, "ro.build.version.sdk");
        //查找对应文件并推送
        String miniCapFileName = matchMiniCapFile(androidSdkVersion);
        File miniCapFile = new File("mini" + File.separator + cpuAbi + File.separator + miniCapFileName);
        File miniCapSoFile = new File("mini/minicap-shared/aosp/libs/android-" + androidSdkVersion
                + File.separator + cpuAbi + File.separator + "minicap.so");
        iDevice.pushFile(miniCapFile.getAbsolutePath(), "/data/local/tmp/" + miniCapFileName);
        iDevice.pushFile(miniCapSoFile.getAbsolutePath(), "/data/local/tmp/minicap.so");
        //给文件权限
        executeCommand(iDevice, "chmod 777 /data/local/tmp/" + miniCapFileName);
        String size = getScreenSize(iDevice);
        String vSize = Integer.parseInt(size.substring(0, size.indexOf("x"))) / 2 + "x" + Integer.parseInt(size.substring(size.indexOf("x") + 1)) / 2;
        try {
            //开始启动
            iDevice.executeShellCommand(String.format("LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/%s -Q " + quality + " -P %s@%s/%d",
                    miniCapFileName, size, vSize, screen), new IShellOutputReceiver() {
                @Override
                public void addOutput(byte[] bytes, int i, int i1) {
                    String res = new String(bytes, i, i1);
                    logger.info(res);
                    if (res.contains("Vector<> have different types")) {
                        logger.info(iDevice.getSerialNumber() + "设备不兼容投屏！");
                        if (session != null) {
                            JSONObject support = new JSONObject();
                            support.put("msg", "support");
                            support.put("text", "该设备不兼容MiniCap投屏！");
                            sendText(session, support.toJSONString());
                        }
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
            logger.info("{} 设备miniCap启动异常！"
                    , iDevice.getSerialNumber());
            logger.error(e.getMessage());
        }
    }

    private static void sendText(Session session, String message) {
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IllegalStateException | IOException e) {
                logger.error("socket发送失败!连接已关闭！");
            }
        }
    }

    /**
     * @param iDevice
     * @return void
     * @author ZhouYiXun
     * @des 开启miniTouch服务
     * @date 2021/8/16 20:26
     */
    public static void miniTouchStart(IDevice iDevice) throws AdbCommandRejectedException, IOException, SyncException, TimeoutException {
        //先删除原有路径下的文件，防止上次出错后停止，再次打开会报错的情况
        executeCommand(iDevice, "rm -rf /data/local/tmp/minitouch*");
        //获取cpu信息
        String cpuAbi = getProperties(iDevice, "ro.product.cpu.abi");
        //获取安卓sdk版本
        String androidSdkVersion = getProperties(iDevice, "ro.build.version.sdk");
        String miniTouchFileName = matchMiniTouchFile(androidSdkVersion);
        File miniTouchFile = new File("mini" + File.separator + cpuAbi + File.separator + miniTouchFileName);
        iDevice.pushFile(miniTouchFile.getAbsolutePath(), "/data/local/tmp/" + miniTouchFileName);
        //给文件权限
        executeCommand(iDevice, "chmod 777 /data/local/tmp/" + miniTouchFileName);
        try {
            //开始启动
            iDevice.executeShellCommand(String.format("/data/local/tmp/%s", miniTouchFileName), new IShellOutputReceiver() {
                @Override
                public void addOutput(byte[] bytes, int i, int i1) {
                    String res = new String(bytes, i, i1);
                    logger.info(res);
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
            logger.info("{} 设备miniTouch启动异常！"
                    , iDevice.getSerialNumber());
            logger.error(e.getMessage());
        }
    }

    /**
     * @param udId
     * @param packageName
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 获取app版本信息
     * @date 2021/8/16 15:29
     */
    public static String getAppOnlyVersion(String udId, String packageName) {
        IDevice iDevice = getIDeviceByUdId(udId);
        //实质是获取安卓开发在gradle定义的versionName来定义版本号
        String version = executeCommand(iDevice, String.format("pm dump %s | grep 'versionName'", packageName));
        version = version.substring(version.indexOf("=") + 1, version.length() - 1);
        if (version.length() > 50) {
            version = version.substring(0, version.indexOf(" ") + 1);
        }
        //因为不同设备获取的信息不一样，所以需要去掉\r、\n
        return version.replace("\r", "").replace("\n", "");
    }
}
