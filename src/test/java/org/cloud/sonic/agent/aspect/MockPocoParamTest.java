package org.cloud.sonic.agent.aspect;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.cloud.sonic.driver.poco.models.PocoElement;
import org.cloud.sonic.driver.poco.models.RootElement;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Ignore
public class MockPocoParamTest {
    JSONObject mockStepData;

    HandleContext handleContext;

    @Before
    public void initData() {
        JSONObject step = new JSONObject();
        JSONArray elements = new JSONArray();

        JSONObject pocoIteratorEle = new JSONObject();
        pocoIteratorEle.put("eleType", "poco");
        pocoIteratorEle.put("eleValue", "currentIteratorPoco");

        JSONObject pocoEle = new JSONObject();
        pocoEle.put("eleType", "poco");
        pocoEle.put("eleValue", "xxxdddcc");

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                elements.add(new JSONObject(pocoIteratorEle));
            } else {
                elements.add(new JSONObject(pocoEle));
            }
        }
        step.put("elements", elements);
        mockStepData = new JSONObject();
        mockStepData.put("step", step);
        handleContext = new HandleContext();
    }

    @Autowired
    private TestAop testAop;

    @Test
    public void testMockCurrentElementNotNull() {
        handleContext.currentIteratorElement = new PocoElement(new RootElement());
        testAop.runStep(mockStepData, handleContext);
    }

    @Test
    public void testMockCurrentElementISNull() {
        testAop.runStep(mockStepData, handleContext);
    }
}
