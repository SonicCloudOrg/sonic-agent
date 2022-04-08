package org.cloud.sonic.agent.service.impl;

import com.alibaba.fastjson.JSONObject;
import org.apache.dubbo.config.annotation.DubboService;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.tests.AndroidTests;
import org.cloud.sonic.agent.tests.IOSTests;
import org.cloud.sonic.agent.tests.SuiteListener;
import org.cloud.sonic.common.services.AgentsClientService;
import org.cloud.sonic.common.services.AgentsService;
import org.cloud.sonic.common.services.TestSuitesService;
import org.springframework.stereotype.Service;
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
}
