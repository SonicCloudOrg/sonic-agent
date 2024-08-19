package org.cloud.sonic.agent.bridge.ios;

/**
 * Created by fengbincao on 2024/08/19
 */
public class CompareVersionUtil {

    /**
     * 比较版本号的大小,前者大则返回一个正数,后者大返回一个负数,相等则返回0
     *
     * @param version1 第一个版本号
     * @param version2 第二个版本号
     * @return 前者大则返回一个正数, 后者大返回一个负数, 相等则返回0
     */
    public static int compareVersion(String version1, String version2) {
        String[] v1 = version1.split("\\."), v2 = version2.split("\\.");
        int index = 0;
        for (; index < v1.length && index < v2.length; index++) {
            int v1n = Integer.parseInt(v1[index]), v2n = Integer.parseInt(v2[index]);
            if (v1n > v2n) return 1;
            if (v1n < v2n) return -1;
        }
        if (index < v1.length) {
            for (; index < v1.length; index++) {
                int v1n = Integer.parseInt(v1[index]);
                if (v1n > 0) return 1;
            }
        }
        if (index < v2.length) {
            for (; index < v2.length; index++) {
                int v2n = Integer.parseInt(v2[index]);
                if (v2n > 0) return -1;
            }
        }
        return 0;
    }
}
