/**
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
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.cloud.sonic.agent.tests.SuiteListener.runningTestsMap;

/**
 * @author ZhouYiXun
 * @des 安卓测试执行类
 * @date 2021/8/25 20:50
 */
public class AndroidTests {
    private final Logger logger = LoggerFactory.getLogger(AndroidTests.class);

    @DataProvider(name = "testData", parallel = true)
    public Object[][] getTestData(ITestContext context) {
        JSONObject dataInfo = JSON.parseObject(context.getCurrentXmlTest().getParameter("dataInfo"));
        List<JSONObject> dataProvider = new ArrayList<>();
        for (JSONObject iDevice : dataInfo.getJSONArray("device").toJavaList(JSONObject.class)) {
            String udId = iDevice.getString("udId");
            if (AndroidDeviceBridgeTool.getIDeviceByUdId(udId) == null || !AndroidDeviceBridgeTool.getIDeviceByUdId(udId)
                    .getState().toString().equals("ONLINE")) {
                continue;
            }
            JSONObject deviceTestData = new JSONObject();
            deviceTestData.put("steps", dataInfo.getJSONArray("steps"));
            deviceTestData.put("rid", dataInfo.getInteger("rid"));
            deviceTestData.put("cid", dataInfo.getInteger("cid"));
            deviceTestData.put("gp", dataInfo.getJSONObject("gp"));
            deviceTestData.put("device", iDevice);
            dataProvider.add(deviceTestData);
        }
        Object[][] testDataProvider = new Object[dataProvider.size()][];
        for (int i = 0; i < dataProvider.size(); i++) {
            testDataProvider[i] = new Object[]{dataProvider.get(i)};
        }
        return testDataProvider;
    }

    @Test(dataProvider = "testData")
    public void run(JSONObject jsonObject) throws IOException {
        int rid = jsonObject.getInteger("rid");
        int cid = jsonObject.getInteger("cid");
        AndroidStepHandler androidStepHandler = new AndroidStepHandler();
        String udId = jsonObject.getJSONObject("device").getString("udId");
        JSONObject gp = jsonObject.getJSONObject("gp");
        androidStepHandler.setGlobalParams(gp);
        androidStepHandler.setTestMode(cid, rid, udId, DeviceStatus.TESTING, "");

        // 启动任务
        AndroidTestTaskBootThread bootThread = new AndroidTestTaskBootThread(jsonObject, androidStepHandler);
        if (!runningTestsMap.containsKey(rid + "")) {
            logger.info("任务【{}】中断，跳过", bootThread.getName());
            return;
        }
        TaskManager.startBootThread(bootThread);
        // 用例串行
        try {
            bootThread.waitFinished();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (bootThread.getForceStop()) {
            logger.info("任务【{}】中断，跳过", bootThread.getName());
            return;
        }
        logger.info("任务【{}】完成", bootThread.getName());
    }

}
