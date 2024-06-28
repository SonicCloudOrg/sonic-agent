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
package org.cloud.sonic.agent.tests.ios;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.maps.DevicesBatteryMap;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.StringTool;
import org.cloud.sonic.agent.transport.TransportWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author JayWenStar, Eason
 * @date 2022/4/25 11:45 上午
 */
@Slf4j
public class IOSBatteryThread implements Runnable {

    /**
     * second
     */
    public static final long DELAY = 30;

    public static final String THREAD_NAME = "ios-battery-thread";

    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        if (TransportWorker.client == null) {
        	log.debug("TransportWorker.client is null.");
            return;
        }

        List<String> deviceList = SibTool.getDeviceList();
        if (deviceList.size() == 0) {
        	log.debug("No iOS devices to check battery.");
            return;
        }

        List<JSONObject> detail = new ArrayList<>();
        for (String udId : deviceList) {
            try {
            	final JSONObject devicesBattery = SibTool.getBattery(udId);
                final JSONObject jsonObject = new JSONObject();
                jsonObject.put("udId", udId);
                jsonObject.put("vol", devicesBattery.getInteger("Voltage"));
                final int tem = devicesBattery.getInteger("Temperature")/10;
                jsonObject.put("tem", tem);
                jsonObject.put("level", devicesBattery.getInteger("CurrentCapacity"));
                detail.add(jsonObject);
                
                // control
                if (tem>=BytesTool.highTemp*10) {
                    Integer times = DevicesBatteryMap.getTempMap().get(udId);
                    final JSONObject errCall = new JSONObject();
                    errCall.put("msg", "errCall");
                    errCall.put("udId", udId);
                    errCall.put("tem", tem);
                    if (times==null) {
                        times = 1;
                    } else {
                        times += 1;
                    } // end if
                    DevicesBatteryMap.getTempMap().put(udId, times);
                    if (times >= (BytesTool.highTempTime * 2)) {
                        errCall.put("type", 2); // Send shutdown Msg
                        log.error("iOS device {} battery temperature too high (over {} times; threshold= {}, current= {}), shutting down...", udId, times, BytesTool.highTemp, tem);
                        SibTool.shutdown(udId);
                        DevicesBatteryMap.getTempMap().remove(udId);
                    } else {
                    	errCall.put("type", 1); // Send Error Msg
                    	log.warn("iOS device {} battery temperature too high ({} time; threshold= {}, current= {})", udId, StringTool.ordinal(times), BytesTool.highTemp, tem);
                    } // end if
                    TransportWorker.send(errCall);
                } else {
                    DevicesBatteryMap.getTempMap().remove(udId);
                } // end if
            } catch (Exception e) {
                log.atWarn().addKeyValue("udid", udId).log("Failed to get battery level of iOS device: ↴", e);
            } // end try
        }
        JSONObject result = new JSONObject();
        result.put("msg", "battery");
        result.put("detail", detail);
        try {
            TransportWorker.send(result);
        } catch (Exception e) {
            log.error("Send battery msg failed: ↴", e);
        }
    }
}
