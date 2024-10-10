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

import android.os.BatteryManager;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.maps.DevicesBatteryMap;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.StringTool;
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
        	log.debug("TransportWorker.client is null.");
            return;
        }

        IDevice[] deviceList = AndroidDeviceBridgeTool.getRealOnLineDevices();
        if (deviceList == null || deviceList.length == 0) {
        	log.debug("No Android devices are online to check battery.");
            return;
        }
        List<JSONObject> detail = new ArrayList<>();
        final String strStartLine = "Max charging voltage:";
        for (IDevice iDevice : deviceList) {
        	String udid = null;
        	try {
        		udid = iDevice.getSerialNumber(); // just in case that device disconnects in the middle of executing the following code
	            JSONObject jsonObject = new JSONObject();
	            String battery = AndroidDeviceBridgeTool.executeCommand(iDevice, "dumpsys battery");
	            if (!StringUtils.hasText(battery)) { continue; } // end if
	            int pMaxVolt = battery.indexOf(strStartLine);
	            if (pMaxVolt<0) { continue; } // end if
	            battery = battery.substring(battery.indexOf("\n", pMaxVolt+strStartLine.length()));
                final String realTem = battery.substring(battery.indexOf(BatteryManager.EXTRA_TEMPERATURE+":")).trim();
                final int tem = BytesTool.getInt(realTem.substring(BatteryManager.EXTRA_TEMPERATURE.length()+2, realTem.indexOf("\n")));
                final String realLevel = battery.substring(battery.indexOf(BatteryManager.EXTRA_LEVEL+":")).trim();
                final int level = BytesTool.getInt(realLevel.substring(BatteryManager.EXTRA_LEVEL.length()+2, realLevel.indexOf("\n")));
                final String realVol = battery.substring(battery.indexOf(BatteryManager.EXTRA_VOLTAGE+":")).trim();
                final int vol = BytesTool.getInt(realVol.substring(BatteryManager.EXTRA_VOLTAGE.length()+2, realVol.indexOf("\n")));                
                final String realStatus = battery.substring(battery.indexOf(BatteryManager.EXTRA_STATUS+":")).trim();
                final int intStatus = Integer.parseInt(realStatus.substring(BatteryManager.EXTRA_STATUS.length()+2, realStatus.indexOf("\n")), 10);
                final String realHealth = battery.substring(battery.indexOf(BatteryManager.EXTRA_HEALTH+":")).trim();
                final int intHealth = Integer.parseInt(realHealth.substring(BatteryManager.EXTRA_HEALTH.length()+2, realHealth.indexOf("\n")), 10);
                jsonObject.put("udId", udid);
                jsonObject.put("tem", tem);
                jsonObject.put("level", level);
                jsonObject.put("vol", vol);
                jsonObject.put(BatteryManager.EXTRA_STATUS, intStatus);
                jsonObject.put(BatteryManager.EXTRA_HEALTH, intHealth);
                detail.add(jsonObject);
                
                // control
                if (tem >= BytesTool.highTemp * 10) {
                	final JSONObject errCall = new JSONObject();
                	errCall.put("msg", "errCall");
                	errCall.put("udId", udid);
                	errCall.put("tem", tem);
                    Integer times = DevicesBatteryMap.getTempMap().get(udid);
                    if (times == null) {
                    	times = 1;
                    } else {
                        times += 1;
                    } // end if
                    DevicesBatteryMap.getTempMap().put(udid, times);
                    if (times >= (BytesTool.highTempTime * 2)) {
                        errCall.put("type", 2); // Send shutdown Msg
                        log.error("Android device {} battery temperature too high (over {} times; threshold= {}, current= {}), shutting down...", udid, times, BytesTool.highTemp, tem);
                        AndroidDeviceBridgeTool.shutdown(iDevice);
                        DevicesBatteryMap.getTempMap().remove(udid);
                    } else {
                    	errCall.put("type", 1); // Send Error Msg
                    	log.warn("Android device {} battery temperature too high ({} time; threshold= {}, current= {})", udid, StringTool.ordinal(times), BytesTool.highTemp, tem);
                    } // end if
                    TransportWorker.send(errCall);
                } else {
                    DevicesBatteryMap.getTempMap().remove(udid);
                } // end if
        	} catch (Exception e) {
        		log.atWarn().addKeyValue("udid", udid).log("Failed to get battery status: ↴", e);
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
