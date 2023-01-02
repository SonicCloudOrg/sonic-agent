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
package org.cloud.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.maps.DevicesBatteryMap;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Eason
 * @date 2022/4/24 20:45
 */
@Slf4j
public class AndroidBatteryThread implements Runnable {
    /**
     * second
     */
    public static final long DELAY = 30;

    public static final String THREAD_NAME = "android-battery-thread";

    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        if (TransportWorker.client == null) {
            return;
        }

        IDevice[] deviceList = AndroidDeviceBridgeTool.getRealOnLineDevices();
        if (deviceList == null || deviceList.length == 0) {
            return;
        }
        List<JSONObject> detail = new ArrayList<>();
        for (IDevice iDevice : deviceList) {
            JSONObject jsonObject = new JSONObject();
            String battery = AndroidDeviceBridgeTool
                    .executeCommand(iDevice, "dumpsys battery").replace("Max charging voltage", "");
            if (StringUtils.hasText(battery)) {
                String realTem = battery.substring(battery.indexOf("temperature")).trim();
                int tem = BytesTool.getInt(realTem.substring(13, realTem.indexOf("\n")));
                String realLevel = battery.substring(battery.indexOf("level")).trim();
                int level = BytesTool.getInt(realLevel.substring(7, realLevel.indexOf("\n")));
                String realVol = battery.substring(battery.indexOf("voltage")).trim();
                int vol = BytesTool.getInt(realVol.substring(9, realVol.indexOf("\n")));
                jsonObject.put("udId", iDevice.getSerialNumber());
                jsonObject.put("tem", tem);
                jsonObject.put("level", level);
                jsonObject.put("vol", vol);
                detail.add(jsonObject);
                //control
                if (tem >= BytesTool.highTemp * 10) {
                    Integer times = DevicesBatteryMap.getTempMap().get(iDevice.getSerialNumber());
                    if (times == null) {
                        //Send Error Msg
                        JSONObject errCall = new JSONObject();
                        errCall.put("msg", "errCall");
                        errCall.put("udId", iDevice.getSerialNumber());
                        errCall.put("tem", tem);
                        errCall.put("type", 1);
                        TransportWorker.send(errCall);
                        DevicesBatteryMap.getTempMap().put(iDevice.getSerialNumber(), 1);
                    } else {
                        DevicesBatteryMap.getTempMap().put(iDevice.getSerialNumber(), times + 1);
                    }
                    times = DevicesBatteryMap.getTempMap().get(iDevice.getSerialNumber());
                    if (times >= (BytesTool.highTempTime * 2)) {
                        //Send shutdown Msg
                        JSONObject errCall = new JSONObject();
                        errCall.put("msg", "errCall");
                        errCall.put("udId", iDevice.getSerialNumber());
                        errCall.put("tem", tem);
                        errCall.put("type", 2);
                        TransportWorker.send(errCall);
                        AndroidDeviceBridgeTool.shutdown(iDevice);
                        DevicesBatteryMap.getTempMap().remove(iDevice.getSerialNumber());
                    }
                } else {
                    DevicesBatteryMap.getTempMap().remove(iDevice.getSerialNumber());
                }
            }
        }
        JSONObject result = new JSONObject();
        result.put("msg", "battery");
        result.put("detail", detail);
        try {
            TransportWorker.send(result);
        } catch (Exception e) {
            log.error("Send battery msg failed, cause: ", e);
        }
    }
}
