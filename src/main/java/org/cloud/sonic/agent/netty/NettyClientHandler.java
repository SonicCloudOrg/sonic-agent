package org.cloud.sonic.agent.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.cloud.sonic.agent.tests.android.AndroidRunStepThread;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.cloud.sonic.agent.tests.ios.IOSRunStepThread;
import org.cloud.sonic.agent.tests.ios.IOSTestTaskBootThread;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import javax.websocket.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NettyClientHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<String, Session>();
    private NettyClient nettyClient;
    public static Channel channel = null;

    public static volatile boolean serverOnline = false;

    public NettyClientHandler(NettyClient nettyClient, Channel channel) {
        this.nettyClient = nettyClient;
        this.channel = channel;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        serverOnline = true;
        logger.info("Agent:{} 连接到服务器 {} 成功!", ctx.channel().localAddress(), ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        JSONObject jsonObject = JSON.parseObject((String) msg);
        logger.info("Agent:{} 收到服务器 {} 消息: {}", ctx.channel().localAddress(), ctx.channel().remoteAddress(), jsonObject);
        NettyThreadPool.cachedThreadPool.execute(() -> {
            switch (jsonObject.getString("msg")) {
                case "stop": {
                    AgentManagerTool.stop();
                    break;
                }
                case "reboot":
                    if (jsonObject.getInteger("platform") == PlatformType.ANDROID) {
                        IDevice rebootDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(jsonObject.getString("udId"));
                        if (rebootDevice != null) {
                            AndroidDeviceBridgeTool.reboot(rebootDevice);
                        }
                    }
                    if (jsonObject.getInteger("platform") == PlatformType.IOS) {
                        if (SibTool.getDeviceList().contains(jsonObject.getString("udId"))) {
                            SibTool.reboot(jsonObject.getString("udId"));
                        }
                    }
                    break;
                case "heartBeat":
                    JSONObject heartBeat = new JSONObject();
                    heartBeat.put("msg", "heartBeat");
                    heartBeat.put("status", "alive");
                    NettyThreadPool.send(heartBeat);
                    break;
                case "runStep":
                    if (jsonObject.getInteger("pf") == PlatformType.ANDROID) {
                        runAndroidStep(jsonObject);
                    }
                    if (jsonObject.getInteger("pf") == PlatformType.IOS) {
                        runIOSStep(jsonObject);
                    }
                    break;
                // fixme 已经实现对应的rpc，记得之后删除
                case "suite":
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
                    tng.run();
                    break;
                case "forceStopSuite":
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
                    break;
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("服务器: {} 发生异常 {}", ctx.channel().remoteAddress(), cause.fillInStackTrace());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("服务器: {} 连接断开", ctx.channel().remoteAddress());
        NettyThreadPool.isPassSecurity = false;
        serverOnline = false;
        if (channel != null) {
            channel.close();
        }
        channel = null;
        nettyClient.doConnect();
    }

    public static Map<String, Session> getMap() {
        return sessionMap;
    }

    /**
     * Android 步骤调试
     */
    private void runAndroidStep(JSONObject jsonObject) {

        AndroidPasswordMap.getMap().put(jsonObject.getString("udId"), jsonObject.getString("pwd"));
        AndroidStepHandler androidStepHandler = HandlerMap.getAndroidMap().get(jsonObject.getString("sessionId"));
        if (androidStepHandler == null) {
            return;
        }
        androidStepHandler.resetResultDetailStatus();
        androidStepHandler.setGlobalParams(jsonObject.getJSONObject("gp"));

        AndroidTestTaskBootThread dataBean = new AndroidTestTaskBootThread(jsonObject, androidStepHandler);
        AndroidRunStepThread task = new AndroidRunStepThread(dataBean) {
            @Override
            public void run() {
                super.run();
                androidStepHandler.sendStatus();
            }
        };
        TaskManager.startChildThread(task.getName(), task);
    }

    /**
     * IOS步骤调试
     */
    private void runIOSStep(JSONObject jsonObject) {
        IOSStepHandler iosStepHandler = HandlerMap.getIOSMap().get(jsonObject.getString("sessionId"));
        if (iosStepHandler == null) {
            return;
        }
        iosStepHandler.resetResultDetailStatus();
        iosStepHandler.setGlobalParams(jsonObject.getJSONObject("gp"));

        IOSTestTaskBootThread dataBean = new IOSTestTaskBootThread(jsonObject, iosStepHandler);

        IOSRunStepThread task = new IOSRunStepThread(dataBean) {
            @Override
            public void run() {
                super.run();
                iosStepHandler.sendStatus();
            }
        };
        TaskManager.startChildThread(task.getName(), task);
    }
}