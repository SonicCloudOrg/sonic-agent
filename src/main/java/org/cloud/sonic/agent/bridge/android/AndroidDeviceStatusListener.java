/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.common.interfaces.IsHMStatus;
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
        deviceDetail.put("platform", PlatformType.ANDROID);
        if (device.getProperty("ro.config.ringtone") != null && device.getProperty("ro.config.ringtone").contains("Harmony")) {
            deviceDetail.put("version", device.getProperty("hw_sc.build.platform.version"));
            deviceDetail.put("isHm", IsHMStatus.IS_HM);
        } else {
            deviceDetail.put("version", device.getProperty(IDevice.PROP_BUILD_VERSION));
            deviceDetail.put("isHm", IsHMStatus.IS_ANDROID);
        }

        deviceDetail.put("size", AndroidDeviceBridgeTool.getScreenSize(device));
        deviceDetail.put("cpu", device.getProperty(IDevice.PROP_DEVICE_CPU_ABI));
        deviceDetail.put("manufacturer", device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER));
        TransportWorker.send(deviceDetail);
    }

    @Override
    public void deviceConnected(IDevice device) {
        logger.info("Android device: " + device.getSerialNumber() + " ONLINE！");
        AndroidDeviceManagerMap.getStatusMap().remove(device.getSerialNumber());
        DevicesBatteryMap.getTempMap().remove(device.getSerialNumber());
        send(device);
    }

    @Override
    public void deviceDisconnected(IDevice device) {
        logger.info("Android device: " + device.getSerialNumber() + " OFFLINE！");
        AndroidDeviceManagerMap.getStatusMap().remove(device.getSerialNumber());
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
