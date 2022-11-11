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
package org.cloud.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import org.cloud.sonic.agent.common.interfaces.ResultDetailStatus;
import org.cloud.sonic.agent.tests.TaskManager;
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
     * 判断线程是否结束
     */
    private Semaphore finished = new Semaphore(0);

    private Boolean forceStop = false;

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
     * @param jsonObject         任务数据
     * @param androidStepHandler android步骤执行器
     */
    public AndroidTestTaskBootThread(JSONObject jsonObject, AndroidStepHandler androidStepHandler) {
        this.androidStepHandler = androidStepHandler;
        this.jsonObject = jsonObject;
        this.resultId = jsonObject.getInteger("rid") == null ? 0 : jsonObject.getInteger("rid");
        this.caseId = jsonObject.getInteger("cid") == null ? 0 : jsonObject.getInteger("cid");
        this.udId = jsonObject.getJSONObject("device") == null ? jsonObject.getString("udId") :
                jsonObject.getJSONObject("device").getString("udId");

        // 比如：test-task-thread-af80d1e4
        this.setName(String.format(ANDROID_TEST_TASK_BOOT_PRE, resultId, caseId, udId));
        this.setDaemon(true);
    }

    public void waitFinished() throws InterruptedException {
        finished.acquire();
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

    public AndroidTestTaskBootThread setResultId(int resultId) {
        this.resultId = resultId;
        return this;
    }

    public AndroidTestTaskBootThread setCaseId(int caseId) {
        this.caseId = caseId;
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
            try {
                IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
                int port = AndroidDeviceBridgeTool.startUiaServer(iDevice);
                androidStepHandler.startAndroidDriver(iDevice, port);
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
            forceStop = true;
        } finally {
            if (startTestSuccess) {
                AndroidDeviceLocalStatus.finish(udId);
                androidStepHandler.closeAndroidDriver();
            }
            androidStepHandler.sendStatus();
            finished.release();
            TaskManager.clearTerminatedThreadByKey(this.getName());
        }
    }
}
