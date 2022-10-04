package org.cloud.sonic.agent.tests.script;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.driver.android.AndroidDriver;

public interface GroovyScript {
    void runAndroid(AndroidDriver androidDriver, IDevice iDevice, JSONObject globalParams, LogUtil logUtil, String script);
}
