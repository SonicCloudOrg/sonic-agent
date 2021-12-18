package com.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import com.sonic.agent.interfaces.ResultDetailStatus;
import com.sonic.agent.tests.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;

/**
 * android启动各个子任务的线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:33 上午
 */
public class AndroidTestTaskBootThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(AndroidTestTaskBootThread.class);

    /**
     * android-test-task-boot-{resultId}-{caseId}-{udid}
     */
    public final static String ANDROID_TEST_TASK_BOOT_PRE = "android-test-task-boot-%s-%s-%s";

    /**
     * 控制不同线程执行的信号量
     */
    private Semaphore runStepSemaphore = new Semaphore(1);


    /**
     * 一些任务信息
     */
    private JSONObject jsonObject;

    /**
     * Android步骤处理器，包含一些状态信息
     */
    private AndroidStepHandler androidStepHandler;

    /**
     * 测试步骤线程
     */
    private AndroidRunStepThread runStepThread;

    /**
     * 性能数据采集线程
     */
    private AndroidPerfDataThread perfDataThread;

    /**
     * 录像线程
     */
    private AndroidRecordThread recordThread;

    /**
     * 测试结果id 0表示debug线程
     */
    private int resultId = 0;

    /**
     * 测试用例id 0表示debug线程
     */
    private int caseId = 0;

    /**
     * 设备序列号
     */
    private String udId;

    public String formatThreadName(String baseFormat) {
        return String.format(baseFormat, this.resultId, this.caseId, this.udId);
    }

    /**
     * debug线程构造
     */
    public AndroidTestTaskBootThread() {
        this.setName(this.formatThreadName(ANDROID_TEST_TASK_BOOT_PRE));
        this.setDaemon(true);
    }

    /**
     * 任务线程构造
     *
     * @param jsonObject          任务数据
     * @param androidStepHandler  android步骤执行器
     */
    public AndroidTestTaskBootThread(JSONObject jsonObject, AndroidStepHandler androidStepHandler) {
        this.androidStepHandler = androidStepHandler;
        this.jsonObject = jsonObject;
        this.resultId = jsonObject.getInteger("rid");
        this.caseId = jsonObject.getInteger("cid");
        this.udId = jsonObject.getJSONObject("device").getString("udId");

        // 比如：test-task-thread-af80d1e4
        this.setName(String.format(ANDROID_TEST_TASK_BOOT_PRE, resultId, caseId, udId));
        this.setDaemon(true);
    }

    public Semaphore getRunStepSemaphore() {
        return runStepSemaphore;
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    public AndroidStepHandler getAndroidStepHandler() {
        return androidStepHandler;
    }

    public AndroidRunStepThread getRunStepThread() {
        return runStepThread;
    }

    public AndroidPerfDataThread getPerfDataThread() {
        return perfDataThread;
    }

    public AndroidRecordThread getRecordThread() {
        return recordThread;
    }

    public int getResultId() {
        return resultId;
    }

    public int getCaseId() {
        return caseId;
    }

    public String getUdId() {
        return udId;
    }

    public AndroidTestTaskBootThread setUdId(String udId) {
        this.udId = udId;
        return this;
    }

    @Override
    public void run() {

        boolean startTestSuccess = false;

        try {
            int wait = 0;
            while (!AndroidDeviceLocalStatus.startTest(udId)) {
                wait++;
                androidStepHandler.waitDevice(wait);
                if (wait >= 6 * 10) {
                    androidStepHandler.waitDeviceTimeOut();
                    androidStepHandler.sendStatus();
                    return;
                } else {
                    Thread.sleep(10000);
                }
            }

            startTestSuccess = true;
            //启动测试
            try {
                androidStepHandler.startAndroidDriver(udId);
            } catch (Exception e) {
                log.error(e.getMessage());
                androidStepHandler.closeAndroidDriver();
                androidStepHandler.sendStatus();
                AndroidDeviceLocalStatus.finishError(udId);
                return;
            }

            //电量过低退出测试
            if (androidStepHandler.getBattery()) {
                androidStepHandler.closeAndroidDriver();
                androidStepHandler.sendStatus();
                AndroidDeviceLocalStatus.finish(udId);
                return;
            }

            //正常运行步骤的线程
            runStepThread = new AndroidRunStepThread(this);
            //性能数据获取线程
            perfDataThread = new AndroidPerfDataThread(this);
            //录像线程
            recordThread = new AndroidRecordThread(this);
            TaskManager.startChildThread(this.getName(), runStepThread, perfDataThread, recordThread);


            //等待两个线程结束了才结束方法
            while ((recordThread.isAlive()) || (runStepThread.isAlive())) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            log.error("任务异常，中断：{}", e.getMessage());
            androidStepHandler.setResultDetailStatus(ResultDetailStatus.FAIL);
        } finally {
            if (startTestSuccess) {
                AndroidDeviceLocalStatus.finish(udId);
                androidStepHandler.closeAndroidDriver();
            }
            androidStepHandler.sendStatus();
        }
    }
}
