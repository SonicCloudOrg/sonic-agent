/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.tests;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.CollectionUtils;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试套件监听器
 *
 * @author JayWenStar
 * @date 2022/2/8 11:55 上午
 */
public class SuiteListener implements ISuiteListener {

    public static ConcurrentHashMap<String, Boolean> runningTestsMap = new ConcurrentHashMap<>();

    @Override
    public void onStart(ISuite suite) {
        String runningTestsMapKey = getRunningTestsMapKey(suite);
        if (runningTestsMapKey == null || runningTestsMapKey.length() == 0){
            return;
        }
        runningTestsMap.put(runningTestsMapKey, true);
    }

    @Override
    public void onFinish(ISuite suite) {
        String runningTestsMapKey = getRunningTestsMapKey(suite);
        if (runningTestsMapKey == null || runningTestsMapKey.length() == 0){
            return;
        }
        // 加上udId，避免先完成的设备移除后，后完成的设备无法执行后续操作
        runningTestsMap.remove(runningTestsMapKey);
    }

    private String getRunningTestsMapKey(ISuite suite){
        JSONObject dataInfoJson = JSON.parseObject(suite.getParameter("dataInfo"));
        String rid = dataInfoJson.getString("rid");
        JSONArray deviceArray = dataInfoJson.getJSONArray("device");
        if (CollectionUtils.isEmpty(deviceArray)){
            return null;
        }
        JSONObject jsonObject = deviceArray.getJSONObject(0);
        if (jsonObject == null){
            return null;
        }
        String udId = jsonObject.getString("udId");
        if (udId == null || udId.length() == 0){
            return null;
        }
        return rid + "-" + udId;
    }
}
