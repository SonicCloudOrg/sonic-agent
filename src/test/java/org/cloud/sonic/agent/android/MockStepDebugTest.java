package org.cloud.sonic.agent.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import lombok.Getter;
import lombok.Setter;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.tests.android.AndroidRunStepThread;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.cloud.sonic.agent.tests.handlers.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MockStepDebugTest {
    AndroidStepHandler androidStepHandler;
    @Getter
    @Setter
    String udId = "829ed1f"; // your device udid

    @Autowired
    StepHandlers handlers;
    @Before
    public void beforeClass() throws Exception {

        androidStepHandler = new AndroidStepHandler();

        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);

        androidStepHandler.setTestMode(0, 0, iDevice.getSerialNumber(), "no send server", "ddddddd");
        int port = AndroidDeviceBridgeTool.startUiaServer(iDevice);
        androidStepHandler.startAndroidDriver(iDevice,port);
    }
    @Test
    public void runStep() throws Throwable {
        stepRunner(JSONObject.parseObject(stepMsg));
    }

    public void stepRunner(JSONObject jsonObject) throws Throwable {

        AndroidTestTaskBootThread dataBean = new AndroidTestTaskBootThread(jsonObject, androidStepHandler);
        AndroidRunStepThread task = new AndroidRunStepThread(dataBean) {
            @Override
            public void run() {
                super.run();
                androidStepHandler.sendStatus();
            }
        };
        task.run();
    }
    @Getter
    @Setter
//    private String stepMsg = "{\"msg\":\"runStep\",\"pf\":1,\"gp\":{},\"sessionId\":\"0\",\"pwd\":\"\",\"udId\":\"829ed1f\",\"steps\":[{\"step\":{\"stepType\":\"startPocoDriver\",\"caseId\":1,\"elements\":[],\"conditionType\":0,\"id\":1,\"sort\":1,\"text\":\"5001\",\"error\":3,\"projectId\":1,\"content\":\"UNITY_3D\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"pocoSwipe\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(name=\\\"star\\\", type=\\\"Image\\\")\",\"id\":1,\"moduleId\":0,\"eleName\":\"star1\",\"projectId\":1},{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"shell\\\")\",\"id\":5,\"moduleId\":0,\"eleName\":\"贝壳\",\"projectId\":1}],\"conditionType\":0,\"id\":2,\"sort\":2,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"pocoSwipe\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"star\\\")[1]\",\"id\":2,\"moduleId\":0,\"eleName\":\"star2\",\"projectId\":1},{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"shell\\\")\",\"id\":5,\"moduleId\":0,\"eleName\":\"贝壳\",\"projectId\":1}],\"conditionType\":0,\"id\":3,\"sort\":3,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"isExistPocoEle\",\"sort\":4,\"error\":1,\"content\":\"true\",\"parentId\":0,\"platform\":1,\"caseId\":1,\"childSteps\":[{\"stepType\":\"pocoClick\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(name=\\\"star\\\", type=\\\"Image\\\")\",\"id\":1,\"moduleId\":0,\"eleName\":\"star1\",\"projectId\":1}],\"conditionType\":0,\"id\":6,\"sort\":5,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":5,\"platform\":1},{\"stepType\":\"pocoSwipe\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(name=\\\"star\\\", type=\\\"Image\\\")\",\"id\":1,\"moduleId\":0,\"eleName\":\"star1\",\"projectId\":1},{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"shell\\\")\",\"id\":5,\"moduleId\":0,\"eleName\":\"贝壳\",\"projectId\":1}],\"conditionType\":0,\"id\":7,\"sort\":6,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":5,\"platform\":1}],\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(name=\\\"star\\\", type=\\\"Image\\\")\",\"id\":1,\"moduleId\":0,\"eleName\":\"star1\",\"projectId\":1}],\"conditionType\":1,\"id\":5,\"text\":\"\",\"projectId\":1}},{\"step\":{\"stepType\":\"isExistPocoEle\",\"sort\":7,\"error\":1,\"content\":\"true\",\"parentId\":0,\"platform\":1,\"caseId\":1,\"childSteps\":[{\"stepType\":\"pocoLongPress\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"star\\\")[1]\",\"id\":2,\"moduleId\":0,\"eleName\":\"star2\",\"projectId\":1}],\"conditionType\":0,\"id\":9,\"sort\":8,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"100\",\"parentId\":8,\"platform\":1}],\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"star\\\")[1]\",\"id\":2,\"moduleId\":0,\"eleName\":\"star2\",\"projectId\":1}],\"conditionType\":4,\"id\":8,\"text\":\"\",\"projectId\":1}},{\"step\":{\"stepType\":\"closePocoDriver\",\"caseId\":1,\"elements\":[],\"conditionType\":0,\"id\":10,\"sort\":9,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}}],\"cid\":1}\n";
    private String stepMsg = "{\"msg\":\"runStep\",\"pf\":1,\"gp\":{},\"sessionId\":\"2\",\"pwd\":\"\",\"udId\":\"829ed1f\",\"steps\":[{\"step\":{\"stepType\":\"startPocoDriver\",\"caseId\":1,\"elements\":[],\"conditionType\":0,\"id\":1,\"sort\":1,\"text\":\"5001\",\"error\":3,\"projectId\":1,\"content\":\"UNITY_3D\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"pocoSwipe\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(name=\\\"star\\\", type=\\\"Image\\\")\",\"id\":1,\"moduleId\":0,\"eleName\":\"star1\",\"projectId\":1},{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"shell\\\")\",\"id\":5,\"moduleId\":0,\"eleName\":\"贝壳\",\"projectId\":1}],\"conditionType\":0,\"id\":2,\"sort\":2,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"pocoSwipe\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"star\\\")[1]\",\"id\":2,\"moduleId\":0,\"eleName\":\"star2\",\"projectId\":1},{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"shell\\\")\",\"id\":5,\"moduleId\":0,\"eleName\":\"贝壳\",\"projectId\":1}],\"conditionType\":0,\"id\":3,\"sort\":3,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"closePocoDriver\",\"caseId\":1,\"elements\":[],\"conditionType\":0,\"id\":4,\"sort\":4,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}}],\"cid\":1}";
}