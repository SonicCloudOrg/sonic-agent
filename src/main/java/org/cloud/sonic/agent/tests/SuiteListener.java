/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
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
