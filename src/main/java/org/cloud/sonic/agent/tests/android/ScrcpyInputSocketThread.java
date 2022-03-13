package org.cloud.sonic.agent.tests.android;

import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.common.maps.ScrcpyMap;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Queue;

/**
 * scrcpy socket线程
 * 通过端口转发，将设备视频流转发到此Socket
 */
public class ScrcpyInputSocketThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(ScrcpyInputSocketThread.class);

    public final static String ANDROID_INPUT_SOCKET_PRE = "android-input-socket-task-%s-%s-%s";

    private IDevice iDevice;

    private Queue<byte[]> dataQueue;

    private ScrcpyLocalThread scrcpyLocalThread;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    private Session session;

    public ScrcpyInputSocketThread(IDevice iDevice, Queue<byte[]> dataQueue, ScrcpyLocalThread scrcpyLocalThread, Session session) {
        this.iDevice = iDevice;
        this.dataQueue = dataQueue;
        this.scrcpyLocalThread = scrcpyLocalThread;
        this.session = session;
        this.androidTestTaskBootThread = scrcpyLocalThread.getAndroidTestTaskBootThread();
        this.setDaemon(false);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_INPUT_SOCKET_PRE));
    }

    public IDevice getiDevice() {
        return iDevice;
    }

    public Queue<byte[]> getDataQueue() {
        return dataQueue;
    }

    public ScrcpyLocalThread getScrcpyLocalThread() {
        return scrcpyLocalThread;
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    public Session getSession() {
        return session;
    }

    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final int READ_BUFFER_SIZE = 1024 * 5;

    @Override
    public void run() {
        int scrcpyPort = PortTool.getPort();
        AndroidDeviceBridgeTool.forward(iDevice, scrcpyPort, "scrcpy");
        Socket videoSocket = new Socket();
        Socket controlSocket = new Socket();
        InputStream inputStream = null;
        try {
            videoSocket.connect(new InetSocketAddress("localhost", scrcpyPort));
            controlSocket.connect(new InetSocketAddress("localhost", scrcpyPort));
            inputStream = videoSocket.getInputStream();
            int readLength;
            int naluIndex = 0;
            int bufferLength = 0;

            byte[] buffer = new byte[BUFFER_SIZE];
            while (scrcpyLocalThread.isAlive()) {
                readLength = inputStream.read(buffer, bufferLength, READ_BUFFER_SIZE);
                if (readLength > 0) {

                    bufferLength += readLength;
                    for (int i = 5; i < bufferLength - 4; i++) {
                        if (buffer[i] == 0x00 &&
                                buffer[i + 1] == 0x00 &&
                                buffer[i + 2] == 0x00 &&
                                buffer[i + 3] == 0x01
                        ) {
                            naluIndex = i;

                            byte[] naluBuffer = new byte[naluIndex];
                            System.arraycopy(buffer, 0, naluBuffer, 0, naluIndex);
                            dataQueue.add(naluBuffer);
                            bufferLength -= naluIndex;
                            System.arraycopy(buffer, naluIndex, buffer, 0, bufferLength);
                            i = 5;
                        }
                    }

                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (scrcpyLocalThread.isAlive()) {
                scrcpyLocalThread.interrupt();
                log.info("scprcpy thread已关闭");
            }
            if (videoSocket.isConnected()) {
                try {
                    videoSocket.close();
                    log.info("scprcpy video socket已关闭");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (controlSocket.isConnected()) {
                try {
                    controlSocket.close();
                    log.info("scprcpy control socket已关闭");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                    log.info("scprcpy input流已关闭");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        AndroidDeviceBridgeTool.removeForward(iDevice, scrcpyPort, "scrcpy");
        if (session != null) {
            ScrcpyMap.getMap().remove(session);
        }
    }
}

