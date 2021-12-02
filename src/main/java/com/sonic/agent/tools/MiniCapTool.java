package com.sonic.agent.tools;

import com.android.ddmlib.IDevice;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.tests.TaskManager;
import com.sonic.agent.tests.android.AndroidTestTaskBootThread;
import com.sonic.agent.tests.android.InputSocketThread;
import com.sonic.agent.tests.android.OutputSocketThread;
import com.sonic.agent.tests.android.StartServerThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static com.sonic.agent.tests.android.AndroidTestTaskBootThread.ANDROID_TEST_TASK_BOOT_PRE;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/8/26 9:20
 */
public class MiniCapTool {
    private final Logger logger = LoggerFactory.getLogger(MiniCapTool.class);

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
        int c = 0;
        switch (s) {
            case 0:
                c = 0;
                break;
            case 1:
                c = 90;
                break;
            case 2:
                c = 180;
                break;
            case 3:
                c = 270;
                break;
        }
        int finalC = c;
        // 启动mincap服务
        StartServerThread miniCapPro = new StartServerThread(iDevice, pic, finalC, session, androidTestTaskBootThread);
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
        InputSocketThread sendImg = new InputSocketThread(
                iDevice, new LinkedBlockingQueue<>(), miniCapPro, session
        );
        // 启动输出流
        OutputSocketThread outputSocketThread = new OutputSocketThread(
                sendImg, banner, imgList, session, pic
        );

        TaskManager.startChildThread(key, sendImg, outputSocketThread);

        return miniCapPro; // server线程
    }

}
