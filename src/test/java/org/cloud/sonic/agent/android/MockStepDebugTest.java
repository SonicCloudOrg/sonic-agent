package org.cloud.sonic.agent.localdebug.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import lombok.Getter;
import lombok.Setter;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.models.HandleDes;
import org.cloud.sonic.driver.poco.PocoDriver;
import org.cloud.sonic.driver.poco.enums.PocoEngine;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MockStepDebugTest {
    static AndroidStepHandler androidStepHandler;
    @Getter
    @Setter
    static String udId = "829ed1f"; // your device udid

    static String uiApkVersion = "5.7.4"; // uiautomator2 version

    @BeforeClass
    public static void beforeClass() throws Exception {
        String systemADBPath = getADBPathFromSystemEnv();

        AndroidDebugBridge.init(false);
        AndroidDeviceBridgeTool.androidDebugBridge = AndroidDebugBridge.createBridge(systemADBPath, true, Long.MAX_VALUE, TimeUnit.MILLISECONDS);;

        Field field = AndroidDeviceBridgeTool.class.getDeclaredField("uiaApkVersion");
        field.setAccessible(true);
        field.set(null, uiApkVersion);

        androidStepHandler = new AndroidStepHandler();

        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);

        androidStepHandler.setTestMode(0, 0, iDevice.getSerialNumber(), "no send server", "ddddddd");
        int port = AndroidDeviceBridgeTool.startUiaServer(iDevice);
        androidStepHandler.startAndroidDriver(iDevice,port);
    }

    private static String getADBPathFromSystemEnv() {
        String path = System.getenv("ANDROID_HOME");
        if (path != null) {
            path += File.separator + "platform-tools" + File.separator + "adb";
        } else {
            return null;
        }
        return path;
    }

    @Test
    public void runStep() throws Throwable {
        run(JSONObject.parseObject(stepMsg));
    }

    public void run(JSONObject jsonObject) throws Throwable {
        List<JSONObject> steps = jsonObject.getJSONArray("steps").toJavaList(JSONObject.class);

        HandleDes handleDes = new HandleDes();
        for (JSONObject step : steps) {
            androidStepHandler.runStep(step,handleDes);
        }
    }
    @Getter
    @Setter
    private String stepMsg = "{\"msg\":\"runStep\",\"pf\":1,\"gp\":{},\"sessionId\":\"2\",\"pwd\":\"\",\"udId\":\"829ed1f\",\"steps\":[{\"step\":{\"stepType\":\"startPocoDriver\",\"caseId\":1,\"elements\":[],\"conditionType\":0,\"id\":1,\"sort\":1,\"text\":\"5001\",\"error\":3,\"projectId\":1,\"content\":\"UNITY_3D\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"pocoSwipe\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(name=\\\"star\\\", type=\\\"Image\\\")\",\"id\":1,\"moduleId\":0,\"eleName\":\"star1\",\"projectId\":1},{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"shell\\\")\",\"id\":5,\"moduleId\":0,\"eleName\":\"贝壳\",\"projectId\":1}],\"conditionType\":0,\"id\":2,\"sort\":2,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"pocoSwipe\",\"caseId\":1,\"elements\":[{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"star\\\")[1]\",\"id\":2,\"moduleId\":0,\"eleName\":\"star2\",\"projectId\":1},{\"eleType\":\"poco\",\"eleValue\":\"poco(\\\"shell\\\")\",\"id\":5,\"moduleId\":0,\"eleName\":\"贝壳\",\"projectId\":1}],\"conditionType\":0,\"id\":3,\"sort\":3,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}},{\"step\":{\"stepType\":\"closePocoDriver\",\"caseId\":1,\"elements\":[],\"conditionType\":0,\"id\":4,\"sort\":4,\"text\":\"\",\"error\":3,\"projectId\":1,\"content\":\"\",\"parentId\":0,\"platform\":1}}],\"cid\":1}";
}
