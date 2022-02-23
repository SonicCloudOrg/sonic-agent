package org.cloud.sonic.agent.tests;

import com.alibaba.fastjson.JSON;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试套件监听器
 *
 * @author JayWenStar
 * @date 2022/2/8 11:55 上午
 */
public class SuiteListener implements ISuiteListener {

    public static ConcurrentHashMap<String, Boolean> runningTestsMap = new ConcurrentHashMap<>();

    @Override
    public void onStart(ISuite suite) {
        String rid = JSON.parseObject(suite.getParameter("dataInfo")).getString("rid");
        runningTestsMap.put(rid, true);
    }

    @Override
    public void onFinish(ISuite suite) {
        String rid = JSON.parseObject(suite.getParameter("dataInfo")).getString("rid");
        runningTestsMap.remove(rid);
    }
}
