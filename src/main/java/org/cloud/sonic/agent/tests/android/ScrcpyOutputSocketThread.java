package org.cloud.sonic.agent.tests.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.Queue;

import static org.cloud.sonic.agent.tools.AgentTool.sendByte;

/**
 * 视频流输出线程
 */
public class ScrcpyOutputSocketThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(ScrcpyOutputSocketThread.class);

    public final static String ANDROID_OUTPUT_SOCKET_PRE = "android-output-socket-task-%s-%s-%s";

    private ScrcpyInputSocketThread scrcpyInputSocketThread;

    private Session session;

    private String udId;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    public ScrcpyOutputSocketThread(
            ScrcpyInputSocketThread scrcpyInputSocketThread,
            Session session
    ) {
        this.scrcpyInputSocketThread = scrcpyInputSocketThread;
        this.session = session;
        this.androidTestTaskBootThread = scrcpyInputSocketThread.getAndroidTestTaskBootThread();
        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_OUTPUT_SOCKET_PRE));
    }

    @Override
    public void run() {
        while (scrcpyInputSocketThread.isAlive()) {
            Queue<byte[]> dataQueue = scrcpyInputSocketThread.getDataQueue();
            while (!dataQueue.isEmpty()) {
                byte[] buffer = dataQueue.poll();
                sendByte(session, buffer);
            }
        }
    }
}