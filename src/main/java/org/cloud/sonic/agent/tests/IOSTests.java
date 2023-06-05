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
package org.cloud.sonic.agent.tests;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.tests.handlers.IOSStepHandler;
import org.cloud.sonic.agent.tests.ios.IOSTestTaskBootThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.cloud.sonic.agent.tests.SuiteListener.runningTestsMap;

/**
 * @author ZhouYiXun
 * @des iOS测试执行类
 * @date 2021/8/25 20:51
 */
public class IOSTests {
    private final Logger logger = LoggerFactory.getLogger(IOSTests.class);

    public static ConcurrentHashMap<String, Boolean> iosTestsMap = new ConcurrentHashMap<>();

    @DataProvider(name = "testData", parallel = true)
    public Object[][] getTestData(ITestContext context) {
        JSONObject dataInfo = JSON.parseObject(context.getCurrentXmlTest().getParameter("dataInfo"));
        List<JSONObject> dataProvider = new ArrayList<>();
        for (JSONObject device : dataInfo.getJSONArray("device").toJavaList(JSONObject.class)) {
            String udId = device.getString("udId");
            if (!SibTool.getDeviceList().contains(udId)) {
                continue;
            }
            JSONObject deviceTestData = new JSONObject();
            deviceTestData.put("steps", dataInfo.getJSONArray("steps"));
            deviceTestData.put("rid", dataInfo.getInteger("rid"));
            deviceTestData.put("cid", dataInfo.getInteger("cid"));
            deviceTestData.put("gp", dataInfo.getJSONObject("gp"));
            deviceTestData.put("perf", dataInfo.getJSONObject("perf"));
            deviceTestData.put("device", device);
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
        String udId = jsonObject.getJSONObject("device").getString("udId");
        if (TaskManager.ridRunning(rid, udId)) {
            logger.info("Task repeat! Maybe cause by network, ignore...");
            return;
        }
        int cid = jsonObject.getInteger("cid");
        IOSStepHandler iosStepHandler = new IOSStepHandler();
        JSONObject gp = jsonObject.getJSONObject("gp");
        iosStepHandler.setGlobalParams(gp);
        iosStepHandler.setTestMode(cid, rid, udId, DeviceStatus.TESTING, "");

        // 启动任务
        IOSTestTaskBootThread bootThread = new IOSTestTaskBootThread(jsonObject, iosStepHandler);
        // runningTestsMap的key在rid的基础上再加上udid，避免先执行完的会remove rid，导致用例执行不完全的问题
        if (!runningTestsMap.containsKey(rid + "-" + udId)) {
            logger.info("Task【{}】interrupted, skip.", bootThread.getName());
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
            logger.info("Task【{}】interrupted, skip.", bootThread.getName());
            return;
        }
        logger.info("Task【{}】finish.", bootThread.getName());
    }
}
