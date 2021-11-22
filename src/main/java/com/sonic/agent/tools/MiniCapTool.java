package com.sonic.agent.tools;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.*;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/8/26 9:20
 */
public class MiniCapTool {
    private final Logger logger = LoggerFactory.getLogger(MiniCapTool.class);

    public Future<?> start(String udId, AtomicReference<String[]> banner, AtomicReference<List<byte[]>> imgList, String pic, int tor, Session session) {
        Queue<byte[]> dataQueue = new LinkedBlockingQueue<>();
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
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
        AtomicBoolean isFinish = new AtomicBoolean(false);
        Future<?> miniCapPro = AndroidDeviceThreadPool.cachedThreadPool.submit(() ->{
            //先删除原有路径下的文件，防止上次出错后停止，再次打开会报错的情况
            AndroidDeviceBridgeTool.executeCommand(iDevice, "rm -rf /data/local/tmp/minicap*");
            //获取cpu信息
            String cpuAbi = AndroidDeviceBridgeTool.getProperties(iDevice, "ro.product.cpu.abi");
            //获取安卓sdk版本
            String androidSdkVersion = AndroidDeviceBridgeTool.getProperties(iDevice, "ro.build.version.sdk");
            //查找对应文件并推送
            String miniCapFileName = AndroidDeviceBridgeTool.matchMiniCapFile(androidSdkVersion);
            File miniCapFile = new File("mini" + File.separator + cpuAbi + File.separator + miniCapFileName);
            File miniCapSoFile = new File("mini/minicap-shared/aosp/libs/android-" + androidSdkVersion
                    + File.separator + cpuAbi + File.separator + "minicap.so");
            try {
                iDevice.pushFile(miniCapFile.getAbsolutePath(), "/data/local/tmp/" + miniCapFileName);
                iDevice.pushFile(miniCapSoFile.getAbsolutePath(), "/data/local/tmp/minicap.so");
            }catch (Exception e){
                e.printStackTrace();
            }
            //给文件权限
            AndroidDeviceBridgeTool.executeCommand(iDevice, "chmod 777 /data/local/tmp/" + miniCapFileName);
            String size = AndroidDeviceBridgeTool.getScreenSize(iDevice);
            String vSize;
            int q = 80;
            if (pic.equals("fixed")) {
                vSize = size;
                q = 40;
            } else {
                vSize = "800x800";
            }
            try {
                //开始启动
                iDevice.executeShellCommand(String.format("LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/%s -Q %d -S -P %s@%s/%d",
                        miniCapFileName, q, size, vSize, finalC), new IShellOutputReceiver() {
                    @Override
                    public void addOutput(byte[] bytes, int i, int i1) {
                        String res = new String(bytes, i, i1);
                        logger.info(res);
                        if(res.contains("Server start")){
                            isFinish.set(true);
                        }
                        if (res.contains("Vector<> have different types")) {
                            logger.info(iDevice.getSerialNumber() + "设备不兼容投屏！");
                            if (session != null) {
                                JSONObject support = new JSONObject();
                                support.put("msg", "support");
                                support.put("text", "该设备不兼容MiniCap投屏！");
                                sendText(session, support.toJSONString());
                            }
                        }
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                }, 0, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                logger.info("{} 设备miniCap启动异常！"
                        , iDevice.getSerialNumber());
                logger.error(e.getMessage());
            }
        });
        int wait = 0;
        while (!isFinish.get()){
            wait++;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(wait>8){
                break;
            }
        }
        int finalMiniCapPort = PortTool.getPort();
        Future<?> finalMiniCapPro = miniCapPro;
        Future<?> sendImage = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            AndroidDeviceBridgeTool.forward(iDevice, finalMiniCapPort, "minicap");
            Socket capSocket = null;
            InputStream inputStream = null;
            try {
                capSocket = new Socket("localhost", finalMiniCapPort);
                inputStream = capSocket.getInputStream();
                int len = 1024;
                while (!finalMiniCapPro.isDone()) {
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
                if (!finalMiniCapPro.isDone()) {
                    finalMiniCapPro.cancel(true);
                    logger.info("miniCap thread已关闭");
                }
                if (capSocket != null && capSocket.isConnected()) {
                    try {
                        capSocket.close();
                        logger.info("miniCap socket已关闭");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                        logger.info("miniCap input流已关闭");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            AndroidDeviceBridgeTool.removeForward(iDevice, finalMiniCapPort, "minicap");
        });

        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
            int readBannerBytes = 0;
            int bannerLength = 2;
            int readFrameBytes = 0;
            int frameBodyLength = 0;
            byte[] frameBody = new byte[0];
            byte[] oldBytes = new byte[0];
            int count = 0;
            while (!sendImage.isDone()) {
                if (dataQueue.isEmpty()) {
                    continue;
                }
                byte[] buffer = dataQueue.poll();
                int len = buffer.length;
                for (int cursor = 0; cursor < len; ) {
                    int byte10 = buffer[cursor] & 0xff;
                    if (readBannerBytes < bannerLength) {//第一次进来读取头部信息
                        switch (readBannerBytes) {
                            case 0:
                                // version
                                banner.get()[0] = buffer[cursor] + "";
                                break;
                            case 1:
                                // length
                                bannerLength = buffer[cursor];
                                banner.get()[1] = String.valueOf(bannerLength);
                                break;
                            case 2:
                            case 3:
                            case 4:
                            case 5:
                                banner.get()[5] = bytesToLong(buffer, 2) + "";
                                break;
                            case 6:
                            case 7:
                            case 8:
                            case 9:
                                banner.get()[9] = bytesToLong(buffer, 6) + "";
                                break;
                            case 10:
                            case 11:
                            case 12:
                            case 13:
                                banner.get()[13] = bytesToLong(buffer, 10) + "";
                                break;
                            case 14:
                            case 15:
                            case 16:
                            case 17:
                                banner.get()[17] = bytesToLong(buffer, 14) + "";
                                break;
                            case 18:
                            case 19:
                            case 20:
                            case 21:
                                banner.get()[21] = bytesToLong(buffer, 18) + "";
                                break;
                            case 22:
                                banner.get()[22] += buffer[cursor] * 90;
                                break;
                            case 23:
                                // quirks
                                banner.get()[23] = buffer[cursor] + "";
                                break;
                        }
                        cursor += 1;
                        readBannerBytes += 1;
                        if (readBannerBytes == bannerLength) {
                            logger.info("banner读取已就绪");
                            if (session != null) {
                                JSONObject size = new JSONObject();
                                size.put("msg", "size");
                                size.put("width", banner.get()[9]);
                                size.put("height", banner.get()[13]);
                                sendText(session, size.toJSONString());
                            }
                        }
                    } else if (readFrameBytes < 4) {//读取并设置图片的大小
                        frameBodyLength += (byte10 << (readFrameBytes * 8));
                        cursor += 1;
                        readFrameBytes += 1;
                    } else {
                        if (len - cursor >= frameBodyLength) {
                            byte[] subByte = subByteArray(buffer, cursor,
                                    cursor + frameBodyLength);
                            frameBody = addBytes(frameBody, subByte);
                            if ((frameBody[0] != -1) || frameBody[1] != -40) {
                                return;
                            }
                            final byte[] finalBytes = subByteArray(frameBody,
                                    0, frameBody.length);
                            if (session != null) {
                                if (!Arrays.equals(oldBytes, finalBytes)) {
                                    switch (pic) {
                                        case "low":
                                            count++;
                                            break;
                                        case "middle":
                                        case "fixed":
                                            count += 2;
                                            break;
                                        case "high":
                                            break;
                                    }
                                    if (count % 4 == 0) {
                                        count = 0;
                                        oldBytes = finalBytes;
                                        sendByte(session, finalBytes);
                                    }
                                }
                            }
                            if (imgList != null) {
                                imgList.get().add(finalBytes);
                            }
                            cursor += frameBodyLength;
                            frameBodyLength = 0;
                            readFrameBytes = 0;
                            frameBody = new byte[0];
                        } else {
                            byte[] subByte = subByteArray(buffer, cursor, len);
                            frameBody = addBytes(frameBody, subByte);
                            frameBodyLength -= (len - cursor);
                            readFrameBytes += (len - cursor);
                            cursor = len;
                        }
                    }
                }
            }
        });
        return miniCapPro;
    }

    private long bytesToLong(byte[] src, int offset) {
        long value;
        value = ((src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8) | ((src[offset + 2] & 0xFF) << 16)
                | ((src[offset + 3] & 0xFF) << 24));
        return value;
    }

    // java合并两个byte数组
    private byte[] addBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;
    }

    private byte[] subByteArray(byte[] byte1, int start, int end) {
        byte[] byte2;
        byte2 = new byte[end - start];
        System.arraycopy(byte1, start, byte2, 0, end - start);
        return byte2;
    }

    private void sendByte(Session session, byte[] message) {
        synchronized (session) {
            try {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(message));
            } catch (IllegalStateException | IOException e) {
                logger.error("webSocket发送失败!连接已关闭！");
            }
        }
    }

    private void sendText(Session session, String message) {
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IllegalStateException | IOException e) {
                logger.error("webSocket发送失败!连接已关闭！");
            }
        }
    }
}
