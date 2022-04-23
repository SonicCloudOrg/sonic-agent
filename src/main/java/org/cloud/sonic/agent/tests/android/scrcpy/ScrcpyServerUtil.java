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
package org.cloud.sonic.agent.tests.android.scrcpy;

import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.concurrent.LinkedBlockingQueue;

import static org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread.ANDROID_TEST_TASK_BOOT_PRE;

public class ScrcpyServerUtil {
    private final Logger logger = LoggerFactory.getLogger(ScrcpyServerUtil.class);

    public Thread start(
            String udId,
            int tor,
            Session session
    ) {
        return start(udId, tor, session, new AndroidTestTaskBootThread().setUdId(udId));
    }

    public Thread start(
            String udId,
            int tor,
            Session session,
            AndroidTestTaskBootThread androidTestTaskBootThread
    ) {
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        String key = androidTestTaskBootThread.formatThreadName(ANDROID_TEST_TASK_BOOT_PRE);
        int s;
        if (tor == -1) {
            s = AndroidDeviceBridgeTool.getScreen(AndroidDeviceBridgeTool.getIDeviceByUdId(udId));
        } else {
            s = tor;
        }
        // 启动scrcpy服务
        ScrcpyLocalThread scrcpyThread = new ScrcpyLocalThread(iDevice, s, session, androidTestTaskBootThread);
        TaskManager.startChildThread(key, scrcpyThread);

        // 等待启动
        int wait = 0;
        while (!scrcpyThread.getIsFinish().tryAcquire()) {
            wait++;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 启动失败了，强行跳过，保证其它服务可用
            if (wait > 8) {
                break;
            }
        }
        // 启动输入流
        ScrcpyInputSocketThread scrcpyInputSocketThread = new ScrcpyInputSocketThread(iDevice, new LinkedBlockingQueue<>(), scrcpyThread, session);
        // 启动输出流
        ScrcpyOutputSocketThread scrcpyOutputSocketThread = new ScrcpyOutputSocketThread(scrcpyInputSocketThread, session);
        TaskManager.startChildThread(key, scrcpyInputSocketThread, scrcpyOutputSocketThread);
        return scrcpyThread; // server线程
    }

}
