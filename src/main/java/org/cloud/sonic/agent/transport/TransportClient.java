package org.cloud.sonic.agent.transport;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.AndroidPasswordMap;
import org.cloud.sonic.agent.common.maps.HandlerMap;
import org.cloud.sonic.agent.models.Cabinet;
import org.cloud.sonic.agent.tests.AndroidTests;
import org.cloud.sonic.agent.tests.IOSTests;
import org.cloud.sonic.agent.tests.SuiteListener;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.android.AndroidRunStepThread;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.cloud.sonic.agent.tests.ios.IOSRunStepThread;
import org.cloud.sonic.agent.tests.ios.IOSTestTaskBootThread;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.SpringTool;
import org.cloud.sonic.agent.tools.shc.SHCService;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TransportClient extends WebSocketClient {
    String host = String.valueOf(SpringTool.getPropertiesValue("sonic.agent.host"));
    String version = String.valueOf(SpringTool.getPropertiesValue("spring.version"));
    Integer storey = Integer.valueOf(SpringTool.getPropertiesValue("sonic.agent.cabinet.storey"));
    Integer port = Integer.valueOf(SpringTool.getPropertiesValue("sonic.agent.port"));

    public TransportClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("Connected and auth...");
    }

    @Override
    public void onMessage(String s) {
        JSONObject jsonObject = JSON.parseObject(s);
        log.info("Agent <- Server message: {}", jsonObject);
        TransportWorker.cachedThreadPool.execute(() -> {
            switch (jsonObject.getString("msg")) {
                case "auth": {
                    if (jsonObject.getString("result").equals("pass")) {
                        log.info("server auth successful!");
                        if (jsonObject.getString("cabinetAuth") != null && jsonObject.getString("cabinetAuth").equals("pass")) {
                            SHCService.connect();
                            if (SHCService.status != SHCService.SHCStatus.OPEN) {
                                BytesTool.currentCabinet = JSON.parseObject("cabinet", Cabinet.class);
                                BytesTool.storey = storey;
                            } else {
                                log.info("SHC连接失败！请确保您使用的是Sonic机柜，" +
                                        "如果仍然连接不上，请在对应Agent所在主机执行[sudo service shc restart]");
                            }
                        } else {
                            log.info("cabinet not found!");
                        }
                        BytesTool.agentId = jsonObject.getInteger("id");
                        BytesTool.agentHost = host;
                        TransportWorker.client = this;
                        JSONObject agentInfo = new JSONObject();
                        agentInfo.put("msg", "agentInfo");
                        agentInfo.put("agentId", jsonObject.getInteger("id"));
                        agentInfo.put("port", port);
                        agentInfo.put("version", "v" + version);
                        agentInfo.put("systemType", System.getProperty("os.name"));
                        agentInfo.put("host", host);
                        agentInfo.put("cabinetId", BytesTool.currentCabinet.getId());
                        agentInfo.put("storey", BytesTool.storey);
                        TransportWorker.client.send(agentInfo.toJSONString());
                    } else {
                        TransportWorker.isKeyAuth = false;
                        log.info("server auth failed!");
                    }
                    break;
                }
                case "position": {
                    SHCService.positionMap.put(jsonObject.getString("udId"), jsonObject.getInteger("position"));
                    break;
                }
                case "shutdown": {
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
                    TransportWorker.send(heartBeat);
                    break;
                case "runStep":
                    if (jsonObject.getInteger("pf") == PlatformType.ANDROID) {
                        runAndroidStep(jsonObject);
                    }
                    if (jsonObject.getInteger("pf") == PlatformType.IOS) {
                        runIOSStep(jsonObject);
                    }
                    break;
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
    public void onClose(int i, String s, boolean b) {
        log.info("Server disconnected. Retry in 10s...");
        TransportWorker.client = null;
    }

    @Override
    public void onError(Exception e) {
        log.info(e.getMessage());
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
