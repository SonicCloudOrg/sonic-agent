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
package org.cloud.sonic.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/8/26 22:23
 */
public class BytesTool {
    private static final Logger log = LoggerFactory.getLogger(BytesTool.class);

    public static int agentId = 0;
    public static String agentHost = "";
    public static int highTemp = 0;
    public static int highTempTime = 0;

    public static int toInt(byte[] b) {
        int res = 0;
        for (int i = 0; i < b.length; i++) {
            res += (b[i] & 0xff) << (i * 8);
        }
        return res;
    }

    public static byte[] intToByteArray(int i) {
        byte[] result = new byte[4];
        result[0] = (byte) (i & 0xff);
        result[1] = (byte) (i >> 8 & 0xff);
        result[2] = (byte) (i >> 16 & 0xff);
        result[3] = (byte) (i >> 24 & 0xff);
        return result;
    }

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
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(message));
            } catch (IllegalStateException | IOException e) {
                log.error("WebSocket send msg error...connection has been closed.");
            }
        }
    }

    public static void sendByte(Session session, ByteBuffer message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.getBasicRemote().sendBinary(message);
            } catch (IllegalStateException | IOException e) {
                log.error("WebSocket send msg error...connection has been closed.");
            }
        }
    }

    public static void sendText(Session session, String message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IllegalStateException | IOException e) {
                log.error("WebSocket send msg error...connection has been closed.");
            }
        }
    }

    public static boolean isInt(String s) {
        return s.matches("[0-9]+");
    }

    public static int getInt(String a) {
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(a);
        return Integer.parseInt(m.replaceAll("").trim());
    }

    public static boolean versionCheck(String target, String local) {
        int[] targetParse = parseVersion(target);
        int[] localParse = parseVersion(local);
        if (targetParse[0] < localParse[0]) {
            return true;
        }
        if (targetParse[0] == localParse[0]) {
            if (targetParse[1] < localParse[1]) {
                return true;
            }
            if (targetParse[1] == localParse[1]) {
                if (targetParse[2] <= localParse[2]) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int[] parseVersion(String s) {
        return Arrays.asList(s.split("\\.")).stream().mapToInt(Integer::parseInt).toArray();
    }
}
