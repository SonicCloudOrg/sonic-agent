package com.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.sonic.agent.interfaces.PlatformType;
import com.sonic.agent.maps.AndroidDeviceManagerMap;
import com.sonic.agent.rabbitmq.RabbitMQThread;
import com.sonic.agent.tools.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ZhouYiXun
 * @des adb上下线监听，发送对应给server
 * @date 2021/08/16 19:26
 */
public class AndroidDeviceStatusListener implements AndroidDebugBridge.IDeviceChangeListener {
    private final Logger logger = LoggerFactory.getLogger(AndroidDeviceStatusListener.class);

    /**
     * @param device
     * @return void
     * @author ZhouYiXun
     * @des 发送设备状态
     * @date 2021/8/16 19:58
     */
    private void send(IDevice device) {
        JSONObject deviceDetail = new JSONObject();
        deviceDetail.put("msg", "deviceDetail");
        deviceDetail.put("udId", device.getSerialNumber());
        deviceDetail.put("name", device.getProperty("ro.product.name"));
        deviceDetail.put("model", device.getProperty(IDevice.PROP_DEVICE_MODEL));
        deviceDetail.put("status", device.getState());
        deviceDetail.put("platform", PlatformType.ANDROID);
        deviceDetail.put("version", device.getProperty(IDevice.PROP_BUILD_VERSION));
        deviceDetail.put("size", AndroidDeviceBridgeTool.getScreenSize(device));
        deviceDetail.put("cpu", device.getProperty(IDevice.PROP_DEVICE_CPU_ABI));
        deviceDetail.put("manufacturer", device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER));
        deviceDetail.put("agentId", AgentTool.agentId);
        RabbitMQThread.send(deviceDetail);
    }

    @Override
    public void deviceConnected(IDevice device) {
        logger.info("Android设备：" + device.getSerialNumber() + " ONLINE！");
        AndroidDeviceManagerMap.getMap().remove(device.getSerialNumber());
        send(device);
    }

    @Override
    public void deviceDisconnected(IDevice device) {
        logger.info("Android设备：" + device.getSerialNumber() + " OFFLINE！");
        AndroidDeviceManagerMap.getMap().remove(device.getSerialNumber());
        send(device);
    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        if (device.isOnline()) {
            logger.info("Android设备：" + device.getSerialNumber() + " ONLINE！");
        } else {
            logger.info("Android设备：" + device.getSerialNumber() + " OFFLINE！");
        }
        send(device);
    }
}