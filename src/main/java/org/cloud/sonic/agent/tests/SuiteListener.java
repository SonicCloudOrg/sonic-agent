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
        String rid = JSON.parseObject(suite.getParameter("dataInfo")).getString("rid");
        runningTestsMap.put(rid, true);
    }

    @Override
    public void onFinish(ISuite suite) {
        String rid = JSON.parseObject(suite.getParameter("dataInfo")).getString("rid");
        runningTestsMap.remove(rid);
    }
}
