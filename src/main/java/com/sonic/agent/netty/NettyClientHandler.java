package com.sonic.agent.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.automation.IOSStepHandler;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.ios.TIDeviceTool;
import com.sonic.agent.interfaces.DeviceStatus;
import com.sonic.agent.interfaces.PlatformType;
import com.sonic.agent.interfaces.ResultDetailStatus;
import com.sonic.agent.maps.AndroidPasswordMap;
import com.sonic.agent.maps.HandlerMap;
import com.sonic.agent.tests.AndroidTests;
import com.sonic.agent.tests.IOSTests;
import com.sonic.agent.tests.TaskManager;
import com.sonic.agent.tools.SpringTool;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import javax.websocket.Session;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NettyClientHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<String, Session>();
    private NettyClient nettyClient;
    public volatile static Channel channel = null;



    public NettyClientHandler(NettyClient nettyClient, Channel channel) {
        this.nettyClient = nettyClient;
        this.channel = channel;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.info("Agent:{} 连接到服务器 {} 成功!", ctx.channel().localAddress(), ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        JSONObject jsonObject = JSON.parseObject((String) msg);
        logger.info("Agent:{} 收到服务器 {} 消息: {}", ctx.channel().localAddress(), ctx.channel().remoteAddress(), jsonObject);
        NettyThreadPool.cachedThreadPool.execute(() -> {
            switch (jsonObject.getString("msg")) {
                case "reboot":
                    if (jsonObject.getInteger("platform") == PlatformType.ANDROID) {
                        IDevice rebootDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(jsonObject.getString("udId"));
                        if (rebootDevice != null) {
                            AndroidDeviceBridgeTool.reboot(rebootDevice);
                        }
                    }
                    if (jsonObject.getInteger("platform") == PlatformType.IOS) {
                        if (TIDeviceTool.getDeviceList().contains(jsonObject.getString("udId"))) {
                            TIDeviceTool.reboot(jsonObject.getString("udId"));
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
                        AndroidPasswordMap.getMap().put(jsonObject.getString("udId")
                                , jsonObject.getString("pwd"));
                        AndroidStepHandler androidStepHandler = HandlerMap.getAndroidMap().get(jsonObject.getString("sessionId"));
                        androidStepHandler.resetResultDetailStatus();
                        androidStepHandler.setGlobalParams(jsonObject.getJSONObject("gp"));
                        List<JSONObject> steps = jsonObject.getJSONArray("steps").toJavaList(JSONObject.class);
                        for (JSONObject step : steps) {
                            try {
                                androidStepHandler.runStep(step);
                            } catch (Throwable e) {
                                break;
                            }
                        }
                        androidStepHandler.sendStatus();
                    }
                    if (jsonObject.getInteger("pf") == PlatformType.IOS) {
                        IOSStepHandler iosStepHandler = HandlerMap.getIOSMap().get(jsonObject.getString("sessionId"));
                        iosStepHandler.resetResultDetailStatus();
                        iosStepHandler.setGlobalParams(jsonObject.getJSONObject("gp"));
                        List<JSONObject> steps = jsonObject.getJSONArray("steps").toJavaList(JSONObject.class);
                        for (JSONObject step : steps) {
                            try {
                                iosStepHandler.runStep(step);
                            } catch (Throwable e) {
                                break;
                            }
                        }
                        iosStepHandler.sendStatus();
                    }
                    break;
                case "suite":
                    List<JSONObject> cases = jsonObject.getJSONArray("cases").toJavaList(JSONObject.class);
                    TestNG tng = new TestNG();
                    List<XmlSuite> suiteList = new ArrayList<>();
                    XmlSuite xmlSuite = new XmlSuite();
                    for (JSONObject dataInfo : cases) {
                        XmlTest xmlTest = new XmlTest(xmlSuite);
                        Map<String, String> parameters = new HashMap<>();
                        parameters.put("dataInfo", dataInfo.toJSONString());
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
        if (channel != null) {
            channel.close();
        }
        channel = null;
        nettyClient.doConnect();
    }

    public static Map<String, Session> getMap() {
        return sessionMap;
    }
}
