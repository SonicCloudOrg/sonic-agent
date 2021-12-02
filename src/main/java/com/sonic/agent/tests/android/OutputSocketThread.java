package com.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static com.sonic.agent.tools.AgentTool.*;

/**
 * 视频流输出线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:12 上午
 */
public class OutputSocketThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(OutputSocketThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_OUTPUT_SOCKET_PRE = "android_output_socket_task_%s_%s_%s";

    private InputSocketThread sendImg;

    private AtomicReference<String[]> banner;

    private AtomicReference<List<byte[]>> imgList;

    private Session session;

    private String pic;

    private String udId;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    public OutputSocketThread(
            InputSocketThread sendImg,
            AtomicReference<String[]> banner,
            AtomicReference<List<byte[]>> imgList,
            Session session,
            String pic
    ) {
        this.sendImg = sendImg;
        this.banner = banner;
        this.imgList = imgList;
        this.session = session;
        this.pic = pic;
        this.androidTestTaskBootThread = sendImg.getAndroidTestTaskBootThread();

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_OUTPUT_SOCKET_PRE));
    }

    @Override
    public void run() {

        int readBannerBytes = 0;
        int bannerLength = 2;
        int readFrameBytes = 0;
        int frameBodyLength = 0;
        byte[] frameBody = new byte[0];
        byte[] oldBytes = new byte[0];
        int count = 0;
        while (sendImg.isAlive()) {
            Queue<byte[]> dataQueue = sendImg.getDataQueue();
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
                        log.info("banner读取已就绪");
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
    }
}