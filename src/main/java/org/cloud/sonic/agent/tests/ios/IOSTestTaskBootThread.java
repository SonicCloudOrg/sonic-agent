package org.cloud.sonic.agent.tests.ios;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.bridge.ios.IOSDeviceLocalStatus;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.interfaces.ResultDetailStatus;
import org.cloud.sonic.agent.tests.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;

public class IOSTestTaskBootThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(IOSTestTaskBootThread.class);

    /**
     * ios-test-task-boot-{resultId}-{caseId}-{udid}
     */
    public final static String IOS_TEST_TASK_BOOT_PRE = "ios-test-task-boot-%s-%s-%s";

    /**
     * 判断线程是否结束
     */
    private Semaphore finished = new Semaphore(0);

    private Boolean forceStop = false;


    /**
     * 一些任务信息
     */
    private JSONObject jsonObject;

    private IOSStepHandler iosStepHandler;

    /**
     * 测试步骤线程
     */
    private IOSRunStepThread runStepThread;

    /**
     * 性能数据采集线程
     */
    private IOSPerfDataThread perfDataThread;

    /**
     * 录像线程
     */
    private IOSRecordThread recordThread;

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
    public IOSTestTaskBootThread() {
        this.setName(this.formatThreadName(IOS_TEST_TASK_BOOT_PRE));
        this.setDaemon(true);
    }

    /**
     * 任务线程构造
     *
     * @param jsonObject     任务数据
     * @param iosStepHandler ios步骤执行器
     */
    public IOSTestTaskBootThread(JSONObject jsonObject, IOSStepHandler iosStepHandler) {
        this.iosStepHandler = iosStepHandler;
        this.jsonObject = jsonObject;
        this.resultId = jsonObject.getInteger("rid") == null ? 0 : jsonObject.getInteger("rid");
        this.caseId = jsonObject.getInteger("cid") == null ? 0 : jsonObject.getInteger("cid");
        this.udId = jsonObject.getJSONObject("device") == null ? jsonObject.getString("udId")
                : jsonObject.getJSONObject("device").getString("udId");

        // 比如：test-task-thread-af80d1e4
        this.setName(String.format(IOS_TEST_TASK_BOOT_PRE, resultId, caseId, udId));
        this.setDaemon(true);
    }

    public void waitFinished() throws InterruptedException {
        finished.acquire();
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    public IOSStepHandler getIosStepHandler() {
        return iosStepHandler;
    }

    public IOSRunStepThread getRunStepThread() {
        return runStepThread;
    }

    public IOSPerfDataThread getPerfDataThread() {
        return perfDataThread;
    }

    public IOSRecordThread getRecordThread() {
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

    public IOSTestTaskBootThread setUdId(String udId) {
        this.udId = udId;
        return this;
    }

    public Boolean getForceStop() {
        return forceStop;
    }

    @Override
    public void run() {

        boolean startTestSuccess = false;

        try {
            int wait = 0;
            while (!IOSDeviceLocalStatus.startTest(udId)) {
                wait++;
                iosStepHandler.waitDevice(wait);
                if (wait >= 6 * 10) {
                    iosStepHandler.waitDeviceTimeOut();
                    iosStepHandler.sendStatus();
                    return;
                } else {
                    Thread.sleep(10000);
                }
            }

            startTestSuccess = true;
            //启动测试
            try {
                int wdaPort = SibTool.startWda(udId);
                iosStepHandler.startIOSDriver(udId, wdaPort);
            } catch (Exception e) {
                log.error(e.getMessage());
                iosStepHandler.closeIOSDriver();
                iosStepHandler.sendStatus();
                IOSDeviceLocalStatus.finishError(udId);
                return;
            }

            //电量过低退出测试
            if (iosStepHandler.getBattery()) {
                iosStepHandler.closeIOSDriver();
                iosStepHandler.sendStatus();
                IOSDeviceLocalStatus.finish(udId);
                return;
            }

            //正常运行步骤的线程
            runStepThread = new IOSRunStepThread(this);
            //性能数据获取线程
            perfDataThread = new IOSPerfDataThread(this);
            //录像线程
            recordThread = new IOSRecordThread(this);
            TaskManager.startChildThread(this.getName(), runStepThread, perfDataThread, recordThread);


            //等待两个线程结束了才结束方法
            while ((recordThread.isAlive()) || (runStepThread.isAlive())) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            log.error("任务异常，中断：{}", e.getMessage());
            iosStepHandler.setResultDetailStatus(ResultDetailStatus.FAIL);
            forceStop = true;
        } finally {
            if (startTestSuccess) {
                IOSDeviceLocalStatus.finish(udId);
                iosStepHandler.closeIOSDriver();
            }
            iosStepHandler.sendStatus();
            finished.release();
            TaskManager.clearTerminatedThreadByKey(this.getName());
        }
    }
}
