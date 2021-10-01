package com.sonic.agent.receiver;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.rabbitmq.client.Channel;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.ios.LibIMobileDeviceTool;
import com.sonic.agent.interfaces.PlatformType;
import com.sonic.agent.maps.AndroidPasswordMap;
import com.sonic.agent.maps.HandlerMap;
import com.sonic.agent.rabbitmq.RabbitMQThread;
import com.sonic.agent.Tests.AndroidTests;
import com.sonic.agent.tools.AgentTool;
import com.sonic.agent.tools.GetWebStartPort;
import com.sonic.agent.tools.LocalHostTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/9/16 18:40
 */
@Component
public class MsgReceiver {
    @Value("${spring.version}")
    private String version;
    private final Logger logger = LoggerFactory.getLogger(MsgReceiver.class);

    @RabbitListener(queues = "MsgQueue-${sonic.agent.key}")
    public void process(JSONObject jsonObject, Channel channel, Message message) throws IOException {
        logger.info("MsgReceiver消费者收到消息  : " + jsonObject.toString());
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        switch (jsonObject.getString("msg")) {
            case "auth":
                logger.info("当前apex-agent版本为：" + version);
                AgentTool.agentId = jsonObject.getInteger("id");
                RabbitMQThread.isPass = true;
                JSONObject agentInfo = new JSONObject();
                agentInfo.put("msg", "agentInfo");
                agentInfo.put("port", GetWebStartPort.getTomcatPort());
                agentInfo.put("version", version);
                agentInfo.put("systemType", System.getProperty("os.name"));
                agentInfo.put("host", LocalHostTool.getHostIp());
                RabbitMQThread.send(agentInfo);
                channel.basicAck(deliveryTag, true);
                break;
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
                channel.basicAck(deliveryTag, true);
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
                channel.basicAck(deliveryTag, true);
                break;
            case "suite":
                JSONObject device = jsonObject.getJSONObject("device");
                if (AndroidDeviceBridgeTool.getIDeviceByUdId(device.getString("udId")) != null) {
                    AndroidPasswordMap.getMap().put(device.getString("udId")
                            , device.getString("password"));
                    AndroidTests androidTests = new AndroidTests();
                    try {
                        androidTests.run(channel, deliveryTag, jsonObject);
                    } catch (Exception e) {
                        channel.basicReject(deliveryTag, true);
                    }
                } else {
                    //取消本次测试
                    JSONObject subResultCount = new JSONObject();
                    subResultCount.put("rid", jsonObject.getInteger("rid"));
                    RabbitMQThread.send(subResultCount);
                    channel.basicAck(deliveryTag, true);
                }
                break;
        }
    }
}
