package com.sonic.agent.tests.android;

import com.android.ddmlib.IDevice;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.cv.RecordHandler;
import com.sonic.agent.tools.MiniCapTool;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FrameRecorder;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * android 录像线程
 *
 * @author Eason(main) & JayWenStar(slave)
 * @date 2021/12/2 12:29 上午
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class AndroidRecordThread extends Thread {

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    @Setter(value = AccessLevel.NONE)
    public final static String ANDROID_RECORD_TASK_PRE = "android-record-task-%s-%s-%s";

    private final AndroidTestTaskBootThread androidTestTaskBootThread;

    public AndroidRecordThread(AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_RECORD_TASK_PRE));
    }


    @Override
    public void run() {
        AndroidStepHandler androidStepHandler = androidTestTaskBootThread.getAndroidStepHandler();
        AndroidRunStepThread runStepThread = androidTestTaskBootThread.getRunStepThread();
        String udId = androidTestTaskBootThread.getUdId();

        Boolean isSupportRecord = true;
        String manufacturer = AndroidDeviceBridgeTool.getIDeviceByUdId(udId).getProperty(IDevice.PROP_DEVICE_MANUFACTURER);
        if (manufacturer.equals("HUAWEI") || manufacturer.equals("OPPO") || manufacturer.equals("vivo")) {
            isSupportRecord = false;
        }

        while (runStepThread.isAlive()) {
            if (androidStepHandler.getAndroidDriver() == null) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                }
                continue;
            }
            Thread miniCapPro = null;
            AtomicReference<List<byte[]>> imgList = new AtomicReference<>(new ArrayList<>());
            AtomicReference<String[]> banner = new AtomicReference<>(new String[24]);
            if (isSupportRecord) {
                try {
                    androidStepHandler.startRecord();
                } catch (Exception e) {
                    log.error(e.getMessage());
                    isSupportRecord = false;
                }
            } else {
                MiniCapTool miniCapTool = new MiniCapTool();
                miniCapPro = miniCapTool.start(udId, banner, imgList, "high", -1, null, androidTestTaskBootThread);
            }
            int w = 0;
            while (w < 10 && (runStepThread.isAlive())) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                }
                w++;
            }
            //处理录像
            if (isSupportRecord) {
                if (androidStepHandler.getStatus() == 3) {
                    androidStepHandler.stopRecord(udId);
                    return;
                } else {
                    androidStepHandler.getAndroidDriver().stopRecordingScreen();
                }
            } else {
                miniCapPro.interrupt();
                if (androidStepHandler.getStatus() == 3) {
                    File recordByRmvb = new File("test-output/record");
                    if (!recordByRmvb.exists()) {
                        recordByRmvb.mkdirs();
                    }
                    long timeMillis = Calendar.getInstance().getTimeInMillis();
                    String fileName = timeMillis + "_" + udId.substring(0, 4) + ".mp4";
                    File uploadFile = new File(recordByRmvb + File.separator + fileName);
                    try {
                        androidStepHandler.log.sendRecordLog(true, fileName,
                                RecordHandler.record(uploadFile, imgList.get()
                                        , Integer.parseInt(banner.get()[9]), Integer.parseInt(banner.get()[13])));
                    } catch (FrameRecorder.Exception e) {
                        e.printStackTrace();
                    }
                    return;
                }
            }
        }
    }
}
