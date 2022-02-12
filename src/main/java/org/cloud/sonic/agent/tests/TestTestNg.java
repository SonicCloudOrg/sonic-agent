package org.cloud.sonic.agent.tests;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class TestTestNg {
    @Test(dataProvider = "testData")
    public void test(String dpNumber) throws InterruptedException {
        System.out.println("Current Thread Id: " + Thread.currentThread().getId() + ". Dataprovider number: " + dpNumber);
        Thread.sleep(5000);
    }

    @DataProvider(name = "testData", parallel = true)
    public static Object[][] testData() {
        return new Object[][]{
                {"1"},
                {"2"}
        };
    }
}
