package com.sonic.agent.tests;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import com.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import com.sonic.agent.cv.RecordHandler;
import com.sonic.agent.interfaces.DeviceStatus;
import com.sonic.agent.interfaces.ResultDetailStatus;
import com.sonic.agent.maps.AndroidDeviceManagerMap;
import com.sonic.agent.tests.android.AndroidTestTaskBootThread;
import com.sonic.agent.tools.MiniCapTool;
import org.bytedeco.javacv.FrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

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
        AndroidStepHandler androidStepHandler = new AndroidStepHandler();
        int rid = jsonObject.getInteger("rid");
        int cid = jsonObject.getInteger("cid");
        String udId = jsonObject.getJSONObject("device").getString("udId");
        JSONObject gp = jsonObject.getJSONObject("gp");
        androidStepHandler.setGlobalParams(gp);
        androidStepHandler.setTestMode(cid, rid, udId, DeviceStatus.TESTING, "");

        // 启动任务
        AndroidTestTaskBootThread bootThread = new AndroidTestTaskBootThread(jsonObject, androidStepHandler);
        TaskManager.startBootThread(bootThread);
    }
}
