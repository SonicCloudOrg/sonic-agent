package com.sonic.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author ZhouYiXun
 * @des 获取id
 * @date 2021/8/26 22:23
 */
public class AgentTool {
    private static final Logger log = LoggerFactory.getLogger(AgentTool.class);

    public static int agentId = 0;

    public static byte[] subByteArray(byte[] byte1, int start, int end) {
        byte[] byte2;
        byte2 = new byte[end - start];
        System.arraycopy(byte1, start, byte2, 0, end - start);
        return byte2;
    }

    public static long bytesToLong(byte[] src, int offset) {
        long value;
        value = ((src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8) | ((src[offset + 2] & 0xFF) << 16)
                | ((src[offset + 3] & 0xFF) << 24));
        return value;
    }

    // java合并两个byte数组
    public static byte[] addBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;
    }

    public static void sendByte(Session session, byte[] message) {
        synchronized (session) {
            try {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(message));
            } catch (IllegalStateException | IOException e) {
                log.error("WebSocket发送失败!连接已关闭！");
            }
        }
    }

    public static void sendText(Session session, String message) {
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IllegalStateException | IOException e) {
                log.error("WebSocket发送失败!连接已关闭！");
            }
        }
    }

}
