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
package org.cloud.sonic.agent.tests.android;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.maps.DevicesBatteryMap;
import org.cloud.sonic.agent.netty.NettyClientHandler;
import org.cloud.sonic.agent.netty.NettyThreadPool;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.SpringTool;
import org.cloud.sonic.agent.tools.shc.SHCService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eason
 * @date 2022/4/24 20:45
 */
public class AndroidBatteryThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(AndroidBatteryThread.class);
    /**
     * second
     */
    public static final long DELAY = 30;

    public static final String THREAD_NAME = "android-battery-thread";

    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    Boolean cabinetEnable = Boolean.valueOf(SpringTool.getPropertiesValue("sonic.agent.cabinet.enable"));

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        if (NettyClientHandler.channel == null) {
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
                    .executeCommand(iDevice, "dumpsys battery");
            if (StringUtils.hasText(battery)) {
                try {
                    String realTem = battery.substring(battery.indexOf("temperature")).trim();
                    int tem = getInt(realTem.substring(13, realTem.indexOf("\n")));
                    String realLevel = battery.substring(battery.indexOf("level")).trim();
                    int level = getInt(realLevel.substring(7, realLevel.indexOf("\n")));
                    jsonObject.put("udId", iDevice.getSerialNumber());
                    jsonObject.put("tem", tem);
                    jsonObject.put("level", level);
                    detail.add(jsonObject);
                    //control
                    if (cabinetEnable && BytesTool.currentCabinet != null) {
                        boolean needReset = false;
                        Integer times = SHCService.getTemp(iDevice.getSerialNumber());
                        if (tem >= BytesTool.currentCabinet.getHighTemp() * 10) {
                            if (times == null) {
                                //Send Error Msg
                                JSONObject errCall = new JSONObject();
                                errCall.put("msg", "errCall");
                                errCall.put("cabinet", JSON.toJSONString(BytesTool.currentCabinet));
                                errCall.put("udId", iDevice.getSerialNumber());
                                errCall.put("tem", tem);
                                errCall.put("type", 1);
                                NettyThreadPool.send(errCall);
                                DevicesBatteryMap.getTempMap().put(iDevice.getSerialNumber(), 1);
                                SHCService.setGear(iDevice.getSerialNumber(), BytesTool.currentCabinet.getLowGear());
                            } else {
                                DevicesBatteryMap.getTempMap().put(iDevice.getSerialNumber(), times + 1);
                            }
                            int out = BytesTool.currentCabinet.getHighTempTime();
                            if (SHCService.getTemp(iDevice.getSerialNumber()) >= (out / 2)) {
                                //Send shutdown Msg
                                JSONObject errCall = new JSONObject();
                                errCall.put("msg", "errCall");
                                errCall.put("cabinet", JSON.toJSONString(BytesTool.currentCabinet));
                                errCall.put("udId", iDevice.getSerialNumber());
                                errCall.put("tem", tem);
                                errCall.put("type", 2);
                                NettyThreadPool.send(errCall);
                                AndroidDeviceBridgeTool.shutdown(iDevice);
                                DevicesBatteryMap.getTempMap().remove(iDevice.getSerialNumber());
                                DevicesBatteryMap.getGearMap().remove(iDevice.getSerialNumber());
                            }
                            continue;
                        } else {
                            if (times != null) {
                                //Send Reset Msg
                                needReset = true;
                                DevicesBatteryMap.getTempMap().remove(iDevice.getSerialNumber());
                            }
                        }
                        if (level >= BytesTool.currentCabinet.getHighLevel()) {
                            SHCService.setGear(iDevice.getSerialNumber(), BytesTool.currentCabinet.getLowGear());
                        } else if (needReset) {
                            SHCService.setGear(iDevice.getSerialNumber(), BytesTool.currentCabinet.getHighGear());
                        }
                        if (level <= BytesTool.currentCabinet.getLowLevel()) {
                            SHCService.setGear(iDevice.getSerialNumber(), BytesTool.currentCabinet.getHighGear());
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        JSONObject result = new JSONObject();
        result.put("msg", "battery");
        result.put("detail", detail);
        try {
            NettyThreadPool.send(result);
        } catch (Exception e) {
            logger.error("Send battery msg failed, cause: ", e);
        }
    }

    public int getInt(String a) {
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(a);
        return Integer.parseInt(m.replaceAll("").trim());
    }
}
