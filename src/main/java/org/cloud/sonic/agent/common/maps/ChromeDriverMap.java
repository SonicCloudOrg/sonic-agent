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
package org.cloud.sonic.agent.common.maps;

import java.util.HashMap;
import java.util.Map;

/**
 * @see {http://chromedriver.storage.googleapis.com/index.html}
 */
public class ChromeDriverMap {
    private static Map<String, String> chromeDriverMap = new HashMap<>() {
        {
            put("55", "2.28");
            put("56", "2.28");
            put("57", "2.29");
            put("58", "2.31");
            put("59", "2.31");
            put("60", "2.33");
            put("61", "2.33");
            put("62", "2.34");
            put("63", "2.35");
            put("64", "2.36");
            put("65", "2.37");
            put("66", "2.38");
            put("67", "2.39");
            put("68", "2.40");
            put("69", "2.41");
            put("70", "70.0.3538.97");
            put("71", "71.0.3578.137");
            put("72", "72.0.3626.69");
            put("73", "73.0.3683.68");
            put("74", "74.0.3729.6");
            put("75", "75.0.3770.140");
            put("76", "76.0.3809.126");
            put("77", "77.0.3865.40");
            put("78", "78.0.3904.105");
            put("79", "79.0.3945.36");
            put("80", "80.0.3987.106");
            put("81", "81.0.4044.138");
            // without 82.
            put("83", "83.0.4103.39");
            put("84", "84.0.4147.30");
            put("85", "85.0.4183.87");
            put("86", "86.0.4240.22");
            put("87", "87.0.4280.88");
            put("88", "88.0.4324.96");
            put("89", "89.0.4389.23");
            put("90", "90.0.4430.24");
            put("91", "91.0.4472.101");
            put("92", "92.0.4515.107");
            put("93", "93.0.4577.63");
            put("94", "94.0.4606.113");
            put("95", "95.0.4638.69");
            put("96", "96.0.4664.45");
            put("97", "97.0.4692.71");
            put("98", "98.0.4758.102");
            put("99", "99.0.4844.51");
            put("100", "100.0.4896.60");
            put("101", "101.0.4951.41");
            put("102", "102.0.5005.61");
            put("103", "103.0.5060.134");
            put("104", "104.0.5112.79");
            put("105", "105.0.5195.52");
            put("106", "106.0.5249.61");
            put("107", "107.0.5304.62");
            put("108", "108.0.5359.71");
            put("109", "109.0.5414.25");
            put("110", "110.0.5481.30");
            put("111", "111.0.5563.64");
            put("112", "112.0.5615.49");
            put("113", "113.0.5672.24");
            put("114", "114.0.5735.16");
            put("115", "115.0.5790.170");
            put("116", "116.0.5845.96");
            put("117", "117.0.5938.149");
            put("118", "118.0.5993.70");
            put("119", "119.0.6045.105");
            put("120", "120.0.6099.71");
        }
    };

    public static Map<String, String> getMap() {
        return chromeDriverMap;
    }

    /**
     * 解决高版本的上下文切换问题:
     * https://stackoverflow.com/questions/75678572/java-io-ioexception-invalid-status-code-403-text-forbidden
     *
     * @param majorChromeVersion 当前的主版本
     * @return 是否需要启用jdk-http-client的版本
     */
    public static boolean shouldUseJdkHttpClient(String majorChromeVersion) {
        return Integer.parseInt(majorChromeVersion) >= 111;
    }
}
