package com.sonic.agent.testng;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.bridge.AndroidDeviceBridgeTool;
import com.sonic.agent.maps.AndroidDeviceManagerMap;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ZhouYiXun
 * @des 安卓测试执行类
 * @date 2021/8/25 20:50
 */
public class AndroidTests {
    @DataProvider(name = "testData", parallel = true)
    public Object[][] getTestData(ITestContext context) {
        int rid = Integer.parseInt(context.getCurrentXmlTest().getParameter("rid"));
        String dataInfo = context.getCurrentXmlTest().getParameter("dataInfo");
        JSONObject globalParams = JSON.parseObject(context.getCurrentXmlTest().getParameter("gp"));
        List<String> udIdList = JSON.parseArray(
                context.getCurrentXmlTest().getParameter("udIdList")).toJavaList(String.class);
        List<JSONObject> dataProvider = new ArrayList<>();
        for (String udId : udIdList) {
            if (AndroidDeviceManagerMap.getMap().get(udId) != null
                    || !AndroidDeviceBridgeTool.getIDeviceByUdId(udId).getState()
                    .equals(IDevice.DeviceState.ONLINE)) {
                continue;
            }
            JSONObject deviceTestData = new JSONObject();
            deviceTestData.put("udId", udId);
            deviceTestData.put("rid", rid);
            deviceTestData.put("dataInfo", dataInfo);
            deviceTestData.put("gp", globalParams);
            dataProvider.add(deviceTestData);
        }
        Object[][] testDataProvider = new Object[dataProvider.size()][];
        for (int i = 0; i < dataProvider.size(); i++) {
            testDataProvider[i] = new Object[]{dataProvider.get(i)};
        }
        return testDataProvider;
    }

    @Test(dataProvider = "testData", description = "Android端测试")
    public void run(JSONObject dataProvider) throws InterruptedException {
        AndroidStepHandler androidStepHandler = new AndroidStepHandler();
//        androidStepHandler
    }
}
