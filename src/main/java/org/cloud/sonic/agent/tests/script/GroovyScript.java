package org.cloud.sonic.agent.tests.script;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.driver.android.AndroidDriver;
import org.cloud.sonic.driver.ios.IOSDriver;

public interface GroovyScript {
    void runAndroid(AndroidDriver androidDriver, IDevice iDevice, JSONObject globalParams, LogUtil logUtil, String script);

    void runIOS(IOSDriver iosDriver, String udId, JSONObject globalParams, LogUtil logUtil, String script);
}
