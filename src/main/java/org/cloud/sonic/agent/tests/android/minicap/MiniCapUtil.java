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
package org.cloud.sonic.agent.tests.android.minicap;

import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.Session;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread.ANDROID_TEST_TASK_BOOT_PRE;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/8/26 9:20
 */
public class MiniCapUtil {
    private final Logger logger = LoggerFactory.getLogger(MiniCapUtil.class);

    public Thread start(
            String udId,
            AtomicReference<String[]> banner,
            AtomicReference<List<byte[]>> imgList,
            String pic,
            int tor,
            Session session
    ) {
        // 这里的AndroidTestTaskBootThread仅作为data bean使用，不会启动
        return start(udId, banner, imgList, pic, tor, session, new AndroidTestTaskBootThread().setUdId(udId));
    }


    public Thread start(
            String udId,
            AtomicReference<String[]> banner,
            AtomicReference<List<byte[]>> imgList,
            String pic,
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
        // 启动minicap服务
        MiniCapLocalThread miniCapPro = new MiniCapLocalThread(iDevice, pic, s * 90, session, androidTestTaskBootThread);
        TaskManager.startChildThread(key, miniCapPro);

        // 等待启动
        int wait = 0;
        while (!miniCapPro.getIsFinish().tryAcquire()) {
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
        MiniCapInputSocketThread sendImg = new MiniCapInputSocketThread(
                iDevice, new LinkedBlockingQueue<>(), miniCapPro, session
        );
        // 启动输出流
        MiniCapOutputSocketThread miniCapOutputSocketThread = new MiniCapOutputSocketThread(
                sendImg, banner, imgList, session, pic
        );

        TaskManager.startChildThread(key, sendImg, miniCapOutputSocketThread);

        return miniCapPro; // server线程
    }

}
