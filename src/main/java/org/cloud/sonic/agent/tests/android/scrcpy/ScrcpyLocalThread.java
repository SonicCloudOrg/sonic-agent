package org.cloud.sonic.agent.tests.android.scrcpy;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.cloud.sonic.agent.tools.BytesTool.sendText;

/**
 * 启动scrcpy等服务的线程
 */
public class ScrcpyLocalThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(ScrcpyLocalThread.class);

    public final static String ANDROID_START_MINICAP_SERVER_PRE = "android-start-scrcpy-server-task-%s-%s-%s";

    private IDevice iDevice;

    private int finalC;

    private Session session;

    private String udId;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    private Semaphore isFinish = new Semaphore(0);

    public ScrcpyLocalThread(IDevice iDevice, int finalC, Session session, AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.iDevice = iDevice;
        this.finalC = finalC;
        this.session = session;
        this.udId = iDevice.getSerialNumber();
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_START_MINICAP_SERVER_PRE));
    }

    public IDevice getiDevice() {
        return iDevice;
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
        File scrcpyServerFile = new File("plugins/sonic-android-scrcpy.jar");
        try {
            iDevice.pushFile(scrcpyServerFile.getAbsolutePath(), "/data/local/tmp/sonic-android-scrcpy.jar");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            iDevice.executeShellCommand("CLASSPATH=/data/local/tmp/sonic-android-scrcpy.jar app_process / com.genymobile.scrcpy.Server 1.23 log_level=info max_size=0 max_fps=60 tunnel_forward=true send_frame_meta=false control=false show_touches=false stay_awake=true power_off_on_close=false clipboard_autosync=false",
                    new IShellOutputReceiver() {
                        @Override
                        public void addOutput(byte[] bytes, int i, int i1) {
                            String res = new String(bytes, i, i1);
                            log.info(res);
                            if (res.contains("Device")) {
                                isFinish.release();
                            }else {
                                log.info("scrcpy服务启动失败！");
                                JSONObject support = new JSONObject();
                                support.put("msg", "support");
                                support.put("text", "scrcpy服务启动失败！");
                                sendText(session, support.toJSONString());
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
            log.info("{} scrcpy服务启动异常！", iDevice.getSerialNumber());
            log.error(e.getMessage());
        }
    }

}