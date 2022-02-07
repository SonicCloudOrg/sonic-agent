package org.cloud.sonic.agent.tests;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.bridge.ios.TIDeviceTool;
import org.cloud.sonic.agent.interfaces.DeviceStatus;
import org.cloud.sonic.agent.tests.ios.IOSTestTaskBootThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ZhouYiXun
 * @des iOS测试执行类
 * @date 2021/8/25 20:51
 */
public class IOSTests {
    private final Logger logger = LoggerFactory.getLogger(IOSTests.class);

    @DataProvider(name = "testData", parallel = true)
    public Object[][] getTestData(ITestContext context) {
        JSONObject dataInfo = JSON.parseObject(context.getCurrentXmlTest().getParameter("dataInfo"));
        List<JSONObject> dataProvider = new ArrayList<>();
        for (JSONObject device : dataInfo.getJSONArray("device").toJavaList(JSONObject.class)) {
            String udId = device.getString("udId");
            if (!TIDeviceTool.getDeviceList().contains(udId)) {
                continue;
            }
            JSONObject deviceTestData = new JSONObject();
            deviceTestData.put("steps", dataInfo.getJSONArray("steps"));
            deviceTestData.put("rid", dataInfo.getInteger("rid"));
            deviceTestData.put("cid", dataInfo.getInteger("cid"));
            deviceTestData.put("gp", dataInfo.getJSONObject("gp"));
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
        IOSStepHandler iosStepHandler = new IOSStepHandler();
        int rid = jsonObject.getInteger("rid");
        int cid = jsonObject.getInteger("cid");
        String udId = jsonObject.getJSONObject("device").getString("udId");
        JSONObject gp = jsonObject.getJSONObject("gp");
        iosStepHandler.setGlobalParams(gp);
        iosStepHandler.setTestMode(cid, rid, udId, DeviceStatus.TESTING, "");

        // 启动任务
        IOSTestTaskBootThread bootThread = new IOSTestTaskBootThread(jsonObject, iosStepHandler);
        TaskManager.startBootThread(bootThread);
        // 用例串行
        try {
            bootThread.waitFinished();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("任务【{}】完成", bootThread.getName());
    }
}
