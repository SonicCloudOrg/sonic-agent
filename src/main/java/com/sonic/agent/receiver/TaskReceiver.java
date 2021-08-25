package com.sonic.agent.receiver;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.rabbitmq.client.Channel;
import com.sonic.agent.bridge.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.LibIMobileDeviceTool;
import com.sonic.agent.interfaces.PlatformType;
import com.sonic.agent.maps.AndroidDeviceManagerMap;
import com.sonic.agent.maps.IOSDeviceManagerMap;
import com.sonic.agent.rabbitmq.RabbitMQThread;
import com.sonic.agent.testng.AndroidTests;
import com.sonic.agent.testng.IOSTests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.IOException;
import java.util.*;

@Component
public class TaskReceiver {
    private final Logger logger = LoggerFactory.getLogger(TaskReceiver.class);

    @RabbitListener(queues = "AgentQueue-#{queueId}")
    public void process(JSONObject jsonObject, Channel channel, Message message) throws IOException {
        logger.info("TaskReceiver消费者收到消息  : " + jsonObject.toString());
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        switch (jsonObject.getString("msg")) {
            case "reboot":
                if (jsonObject.getInteger("platform") == PlatformType.ANDROID) {
                    IDevice rebootDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(jsonObject.getString("udId"));
                    if (rebootDevice != null) {
                        AndroidDeviceBridgeTool.reboot(rebootDevice);
                    }
                }
                if (jsonObject.getInteger("platform") == PlatformType.IOS) {
                    if (LibIMobileDeviceTool.getDeviceList().contains(jsonObject.getString("udId"))) {
                        LibIMobileDeviceTool.reboot(jsonObject.getString("udId"));
                    }
                }
                break;
            case "suite":
                //获取要执行的设备
                JSONObject assist = jsonObject.getJSONObject("assist");
                List<String> localUdIdList = new ArrayList<>();
                JSONArray testUdIdList = new JSONArray();
                if (jsonObject.getInteger("sp") == PlatformType.ANDROID) {
                    IDevice[] deviceList = AndroidDeviceBridgeTool.getRealOnLineDevices();
                    //获取本地正常的设备列表
                    for (IDevice iDevice : deviceList) {
                        if (iDevice.getState().equals(IDevice.DeviceState.ONLINE)) {
                            localUdIdList.add(iDevice.getSerialNumber());
                        }
                    }
                    //找出任务下发的设备列表里是否本地存在
                    for (String udId : assist.keySet()) {
                        if (localUdIdList.contains(udId)) {
                            testUdIdList.add(udId);
                        }
                    }
                }
                //组装套件
                JSONArray suiteArray = jsonObject.getJSONArray("suite");
                TestNG tng = new TestNG();
                List<XmlSuite> suiteList = new ArrayList<>();
                Map<String, JSONArray> moduleMap = new HashMap<>();
                for (Object caseDetail : suiteArray) {
                    JSONObject caseDetailJson = (JSONObject) caseDetail;
                    JSONObject testCase = caseDetailJson.getJSONObject("case");
                    if (moduleMap.containsKey(testCase.getString("module"))) {
                        moduleMap.get(testCase.getString("module")).add(caseDetailJson);
                    } else {
                        JSONArray stepArray = new JSONArray();
                        stepArray.add(caseDetailJson);
                        moduleMap.put(testCase.getString("module"), stepArray);
                    }
                }
                for (String module : moduleMap.keySet()) {
                    XmlSuite xmlSuite = new XmlSuite();
                    xmlSuite.setName(module);
                    xmlSuite.setDataProviderThreadCount(jsonObject.getInteger("dt"));
                    xmlSuite.setParallel(XmlSuite.ParallelMode.TESTS);//并发级别
                    xmlSuite.setThreadCount(jsonObject.getInteger("ct"));//并发线程数
                    JSONArray caseArray = moduleMap.get(module);
                    for (Object caseInfo : caseArray) {
                        XmlTest xmlTest = new XmlTest(xmlSuite);
                        JSONObject caseInfoJson = (JSONObject) caseInfo;
                        JSONObject testCase = caseInfoJson.getJSONObject("case");
                        xmlTest.setName(testCase.getString("name"));
                        Map<String, String> parameters = new HashMap<>();
                        parameters.put("rid", jsonObject.getInteger("rid") + "");
                        parameters.put("gp", jsonObject.getJSONObject("gp").toJSONString());
                        parameters.put("dataInfo", caseInfoJson.toJSONString());
                        parameters.put("udIdList",testUdIdList.toJSONString());
                        xmlTest.setParameters(parameters);
                        List<XmlClass> classes = new ArrayList<>();
                        if (testCase.getInteger("platform") == PlatformType.ANDROID) {
                            classes.add(new XmlClass(AndroidTests.class));
                        }
                        if (testCase.getInteger("platform") == PlatformType.IOS) {
                            classes.add(new XmlClass(IOSTests.class));
                        }
                        xmlTest.setXmlClasses(classes);
                    }
                    suiteList.add(xmlSuite);
                }
                tng.setSuiteThreadPoolSize(jsonObject.getInteger("mt"));
                tng.setXmlSuites(suiteList);
                tng.run();
                JSONObject suiteResult = new JSONObject();
                suiteResult.put("msg", "suiteResult");
                suiteResult.put("rid", jsonObject.getInteger("rid"));
                suiteResult.put("detail", "finish");
                RabbitMQThread.send(suiteResult);
                break;
        }
        channel.basicAck(deliveryTag, true);
    }
}