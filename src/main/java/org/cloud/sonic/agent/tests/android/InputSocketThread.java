package org.cloud.sonic.agent.tests.android;

import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.maps.MiniCapMap;
import org.cloud.sonic.agent.tools.PortTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

import static org.cloud.sonic.agent.tools.AgentTool.subByteArray;

/**
 * minicap socket线程
 * 通过端口转发，将设备视频流转发到此Socket
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/02 00:52 下午
 */
public class InputSocketThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(InputSocketThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_INPUT_SOCKET_PRE = "android-input-socket-task-%s-%s-%s";

    private IDevice iDevice;

    private BlockingQueue<byte[]> dataQueue;

    private SonicLocalThread miniCapPro;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    private Session session;

    public InputSocketThread(IDevice iDevice, BlockingQueue<byte[]> dataQueue, SonicLocalThread miniCapPro, Session session) {
        this.iDevice = iDevice;
        this.dataQueue = dataQueue;
        this.miniCapPro = miniCapPro;
        this.session = session;
        this.androidTestTaskBootThread = miniCapPro.getAndroidTestTaskBootThread();

        // 让资源合理关闭
        this.setDaemon(false);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_INPUT_SOCKET_PRE));
    }

    public IDevice getiDevice() {
        return iDevice;
    }

    public BlockingQueue<byte[]> getDataQueue() {
        return dataQueue;
    }

    public SonicLocalThread getMiniCapPro() {
        return miniCapPro;
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    public Session getSession() {
        return session;
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

