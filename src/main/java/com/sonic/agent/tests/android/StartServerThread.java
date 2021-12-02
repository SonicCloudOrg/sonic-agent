package com.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.sonic.agent.tools.AgentTool.sendText;

/**
 * 启动mincap等服务的线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:40 上午
 */
public class StartServerThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(StartServerThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_START_MINCAP_SERVER_PRE = "android_start_mincap_server_task_%s_%s_%s";

    private IDevice iDevice;

    private String pic;

    private int finalC;

    private Session session;

    private String udId;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    private Semaphore isFinish = new Semaphore(0);


    public StartServerThread(IDevice iDevice, String pic, int finalC, Session session,
                             AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.iDevice = iDevice;
        this.pic = pic;
        this.finalC = finalC;
        this.session = session;
        this.udId = iDevice.getSerialNumber();
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_START_MINCAP_SERVER_PRE));
    }

    public IDevice getiDevice() {
        return iDevice;
    }

    public String getPic() {
        return pic;
    }

    public int getFinalC() {
        return finalC;
    }

    public Session getSession() {
        return session;
    }

    public String getUdId() {
        return udId;
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    public Semaphore getIsFinish() {
        return isFinish;
    }

    @Override
    public void run() {
        //先删除原有路径下的文件，防止上次出错后停止，再次打开会报错的情况
        AndroidDeviceBridgeTool.executeCommand(iDevice, "rm -rf /data/local/tmp/minicap*");
        //获取cpu信息
        String cpuAbi = AndroidDeviceBridgeTool.getProperties(iDevice, "ro.product.cpu.abi");
        //获取安卓sdk版本
        String androidSdkVersion = AndroidDeviceBridgeTool.getProperties(iDevice, "ro.build.version.sdk");
        //查找对应文件并推送
        String miniCapFileName = AndroidDeviceBridgeTool.matchMiniCapFile(androidSdkVersion);
        File miniCapFile = new File("mini" + File.separator + cpuAbi + File.separator + miniCapFileName);
        File miniCapSoFile = new File("mini/minicap-shared/aosp/libs/android-" + androidSdkVersion
                + File.separator + cpuAbi + File.separator + "minicap.so");
        try {
            iDevice.pushFile(miniCapFile.getAbsolutePath(), "/data/local/tmp/" + miniCapFileName);
            iDevice.pushFile(miniCapSoFile.getAbsolutePath(), "/data/local/tmp/minicap.so");
        } catch (Exception e) {
            e.printStackTrace();
        }
        //给文件权限
        AndroidDeviceBridgeTool.executeCommand(iDevice, "chmod 777 /data/local/tmp/" + miniCapFileName);
        String size = AndroidDeviceBridgeTool.getScreenSize(iDevice);
        String vSize;
        int q = 80;
        if (pic.equals("fixed")) {
            vSize = size;
            q = 40;
        } else {
            vSize = "800x800";
        }
        try {
            //开始启动
            iDevice.executeShellCommand(String.format("LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/%s -Q %d -S -P %s@%s/%d",
                    miniCapFileName, q, size, vSize, finalC), new IShellOutputReceiver() {
                @Override
                public void addOutput(byte[] bytes, int i, int i1) {
                    String res = new String(bytes, i, i1);
                    log.info(res);
                    if (res.contains("Server start")) {
                        isFinish.release();
                    }
                    if (res.contains("Vector<> have different types")) {
                        log.info(iDevice.getSerialNumber() + "设备不兼容投屏！");
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
            log.info("{} 设备miniCap启动异常！"
                    , iDevice.getSerialNumber());
            log.error(e.getMessage());
        }
    }

}