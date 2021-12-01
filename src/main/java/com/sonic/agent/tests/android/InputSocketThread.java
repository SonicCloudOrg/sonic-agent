package com.sonic.agent.tests.android;

import com.android.ddmlib.IDevice;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.maps.MiniCapMap;
import com.sonic.agent.tests.android.AndroidTestTaskBootThread;
import com.sonic.agent.tools.PortTool;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.Session;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Queue;

import static com.sonic.agent.tools.AgentTool.subByteArray;

/**
 * mincap socket线程
 * 通过端口转发，将设备视频流转发到此Socket
 *
 * @author Eason(main) & JayWenStar(slave)
 * @date 2021/12/02 00:52 下午
 */
@Data
@Slf4j
public class InputSocketThread extends Thread {

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    @Setter(value = AccessLevel.NONE)
    public final static String ANDROID_INPUT_SOCKET_PRE = "android-input-socket-task-%s-%s-%s";

    private IDevice iDevice;

    private Queue<byte[]> dataQueue;

    private StartServerThread miniCapPro;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    private Session session;

    public InputSocketThread(IDevice iDevice, Queue<byte[]> dataQueue, StartServerThread miniCapPro, Session session) {
        this.iDevice = iDevice;
        this.dataQueue = dataQueue;
        this.miniCapPro = miniCapPro;
        this.session = session;
        this.androidTestTaskBootThread = miniCapPro.getAndroidTestTaskBootThread();


        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_INPUT_SOCKET_PRE));
    }


    @Override
    public void run() {

        int finalMiniCapPort = PortTool.getPort();
        AndroidDeviceBridgeTool.forward(iDevice, finalMiniCapPort, "minicap");
        Socket capSocket = null;
        InputStream inputStream = null;
        try {
            capSocket = new Socket("localhost", finalMiniCapPort);
            inputStream = capSocket.getInputStream();
            int len = 1024;
            while (miniCapPro.isAlive()) {
                byte[] buffer = new byte[len];
                int realLen;
                realLen = inputStream.read(buffer);
                if (buffer.length != realLen && realLen >= 0) {
                    buffer = subByteArray(buffer, 0, realLen);
                }
                if (realLen >= 0) {
                    dataQueue.offer(buffer);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (miniCapPro.isAlive()) {
                miniCapPro.interrupt();
                log.info("miniCap thread已关闭");
            }
            if (capSocket != null && capSocket.isConnected()) {
                try {
                    capSocket.close();
                    log.info("miniCap socket已关闭");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                    log.info("miniCap input流已关闭");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        AndroidDeviceBridgeTool.removeForward(iDevice, finalMiniCapPort, "minicap");
        if (session != null) {
            MiniCapMap.getMap().remove(session);
        }
    }
}

