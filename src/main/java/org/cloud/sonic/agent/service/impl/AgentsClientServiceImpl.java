package org.cloud.sonic.agent.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import org.apache.dubbo.config.annotation.DubboService;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.AndroidPasswordMap;
import org.cloud.sonic.agent.common.maps.HandlerMap;
import org.cloud.sonic.agent.tests.AndroidTests;
import org.cloud.sonic.agent.tests.IOSTests;
import org.cloud.sonic.agent.tests.SuiteListener;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.android.AndroidRunStepThread;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.cloud.sonic.agent.tests.ios.IOSRunStepThread;
import org.cloud.sonic.agent.tests.ios.IOSTestTaskBootThread;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.websockets.AndroidScreenWSServer;
import org.cloud.sonic.agent.websockets.AndroidWSServer;
import org.cloud.sonic.agent.websockets.IOSWSServer;
import org.cloud.sonic.common.services.AgentsClientService;
import org.cloud.sonic.common.services.AgentsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度agent的服务，跟{@link AgentsService}不同，{@link AgentsService}是操作数据库的接口，而这里是直接操作Agent的
 * Consumer调度的时候注意指定ip:port，否则根据默认的负载均衡策略调度导致失败
 *
 * @author JayWenStar
 * @date 2022/4/8 11:17 下午
 */
@Service
@DubboService
public class AgentsClientServiceImpl implements AgentsClientService {

    @Autowired private AndroidScreenWSServer androidScreenWSServer;
    @Autowired private AndroidWSServer androidWSServer;
    @Autowired private IOSWSServer ioswsServer;

    @Override
    public void runSuite(JSONObject jsonObject) {

        List<JSONObject> cases = jsonObject.getJSONArray("cases").toJavaList(JSONObject.class);
        TestNG tng = new TestNG();
        List<XmlSuite> suiteList = new ArrayList<>();
        XmlSuite xmlSuite = new XmlSuite();
        //bug?
        for (JSONObject dataInfo : cases) {
            XmlTest xmlTest = new XmlTest(xmlSuite);
            Map<String, String> parameters = new HashMap<>();
            parameters.put("dataInfo", dataInfo.toJSONString());
            if (xmlSuite.getParameter("dataInfo") == null) {
                xmlSuite.setParameters(parameters);
            }
            xmlTest.setParameters(parameters);
            List<XmlClass> classes = new ArrayList<>();
            if (jsonObject.getInteger("pf") == PlatformType.ANDROID) {
                classes.add(new XmlClass(AndroidTests.class));
            }
            if (jsonObject.getInteger("pf") == PlatformType.IOS) {
                classes.add(new XmlClass(IOSTests.class));
            }
            xmlTest.setXmlClasses(classes);
        }
        suiteList.add(xmlSuite);
        tng.setXmlSuites(suiteList);
        tng.addListener(new SuiteListener());
        new Thread(tng::run).start();
    }

    @Override
    public void stop() {
        AgentManagerTool.stop();
    }

    @Override
    public void forceStopSuite(JSONObject jsonObject) {
        List<JSONObject> caseList = jsonObject.getJSONArray("cases").toJavaList(JSONObject.class);
        for (JSONObject aCase : caseList) {
            int resultId = (int) aCase.get("rid");
            int caseId = (int) aCase.get("cid");
            JSONArray devices = (JSONArray) aCase.get("device");
            List<JSONObject> deviceList = devices.toJavaList(JSONObject.class);
            for (JSONObject device : deviceList) {
                String udId = (String) device.get("udId");
                TaskManager.forceStopSuite(jsonObject.getInteger("pf"), resultId, caseId, udId);
            }
        }
    }

    @Override
    public Boolean reboot(String udId, Integer platform) {
        switch (platform) {
            case PlatformType.ANDROID -> {
                IDevice rebootDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
                if (rebootDevice != null) {
                    AndroidDeviceBridgeTool.reboot(rebootDevice);
                    return true;
                }
                return false;
            }
            case PlatformType.IOS -> {
                if (SibTool.getDeviceList().contains(udId)) {
                    SibTool.reboot(udId);
                    return true;
                }
                return false;
            }
        }
        return false;
    }


    @Override
    public Boolean checkDeviceOnline(String udId, Integer platform) {
        if (!StringUtils.hasText(udId) || platform == null) {
            return false;
        }
        switch (platform)  {
            case PlatformType.ANDROID:
                IDevice rebootDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
                return rebootDevice != null;
            case PlatformType.IOS:
                return SibTool.getDeviceList().contains(udId + "");
        }
        return false;
    }

    @Override
    public Boolean checkDeviceDebugging(String udId) {
        return androidScreenWSServer.getUdIdSet().contains(udId) ||
                androidWSServer.getUdIdSet().contains(udId) ||
                ioswsServer.getUdIdSet().contains(udId);
    }

    @Override
    public Boolean checkDeviceTesting(String udId) {
        return TaskManager.udIdRunning(udId);
    }

    @Override
    public Boolean checkSuiteRunning(Integer rid) {
        return TaskManager.ridRunning(rid);
    }


}
