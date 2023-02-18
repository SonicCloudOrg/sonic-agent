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
package org.cloud.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidSupplyTool;
import org.cloud.sonic.agent.tests.handlers.AndroidStepHandler;

/**
 * android性能数据获取线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:29 上午
 */
@Slf4j
public class AndroidPerfDataThread extends Thread {

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_PERF_DATA_TASK_PRE = "android-perf-data-task-%s-%s-%s";

    private final AndroidTestTaskBootThread androidTestTaskBootThread;

    public AndroidPerfDataThread(AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_PERF_DATA_TASK_PRE));
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    @Override
    public void run() {
        JSONObject perf = androidTestTaskBootThread.getJsonObject().getJSONObject("perf");
        if (perf.getInteger("isOpen") == 1) {
            String udId = androidTestTaskBootThread.getUdId();
            AndroidStepHandler androidStepHandler = androidTestTaskBootThread.getAndroidStepHandler();
            AndroidSupplyTool.startPerfmon(udId, "", null,
                    androidStepHandler.getLog(), perf.getInteger("perfInterval"));
            boolean hasTarget = false;
            while (!androidTestTaskBootThread.getRunStepThread().isStopped()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
                if (androidStepHandler.getTargetPackage().length() != 0 && !hasTarget) {
                    AndroidSupplyTool.startPerfmon(udId, androidStepHandler.getTargetPackage(), null,
                            androidStepHandler.getLog(), perf.getInteger("perfInterval"));
                    hasTarget = true;
                }
            }
            AndroidSupplyTool.stopPerfmon(udId);
            log.info("{} perf done.", udId);
        }
    }
}
