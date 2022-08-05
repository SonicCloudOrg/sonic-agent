/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.maps.AndroidDeviceManagerMap;
import org.cloud.sonic.agent.common.maps.DevicesBatteryMap;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author ZhouYiXun
 * @des adb上下线监听，发送对应给server
 * @date 2021/08/16 19:26
 */
@Component
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
        deviceDetail.put("status", device.getState() == null ? null : device.getState().toString());
        if(device.getProperty("hw_sc.build.os.enable") != null){
            deviceDetail.put("version", device.getProperty("hw_sc.build.platform.version"));
            deviceDetail.put("platform", PlatformType.HARMONYOS);
        }else{
            deviceDetail.put("version", device.getProperty(IDevice.PROP_BUILD_VERSION));
            deviceDetail.put("platform", PlatformType.ANDROID);
        }

        deviceDetail.put("size", AndroidDeviceBridgeTool.getScreenSize(device));
        deviceDetail.put("cpu", device.getProperty(IDevice.PROP_DEVICE_CPU_ABI));
        deviceDetail.put("manufacturer", device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER));
        TransportWorker.send(deviceDetail);
    }

    @Override
    public void deviceConnected(IDevice device) {
        logger.info("Android device: " + device.getSerialNumber() + " ONLINE！");
        AndroidDeviceManagerMap.getMap().remove(device.getSerialNumber());
        DevicesBatteryMap.getTempMap().remove(device.getSerialNumber());
        send(device);
    }

    @Override
    public void deviceDisconnected(IDevice device) {
        logger.info("Android device: " + device.getSerialNumber() + " OFFLINE！");
        AndroidDeviceManagerMap.getMap().remove(device.getSerialNumber());
        DevicesBatteryMap.getTempMap().remove(device.getSerialNumber());
        send(device);
    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        IDevice.DeviceState state = device.getState();
        if (state == IDevice.DeviceState.OFFLINE) {
            return;
        }
        send(device);
    }
}
