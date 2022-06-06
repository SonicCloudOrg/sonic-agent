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

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.maps.DevicesBatteryMap;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.shc.SHCService;
import org.cloud.sonic.common.services.CabinetService;
import org.cloud.sonic.common.services.DevicesService;
import org.cloud.sonic.common.tools.SpringTool;
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
@Slf4j
public class AndroidBatteryThread implements Runnable {

    /**
     * second
     */
    public static final long DELAY = 30;

    public static final String THREAD_NAME = "android-battery-thread";

    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    Boolean cabinetEnable = Boolean.valueOf(SpringTool.getPropertiesValue("sonic.agent.cabinet.enable"));
    private CabinetService cabinetService = SpringTool.getBean(CabinetService.class);

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        AgentManagerTool agentManagerTool = SpringTool.getBean(AgentManagerTool.class);
        if (!agentManagerTool.checkServerOnline()) {
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
                    if (cabinetEnable && AgentZookeeperRegistry.currentCabinet != null) {
                        boolean needReset = false;
                        Integer times = SHCService.getTemp(iDevice.getSerialNumber());
                        if (tem >= AgentZookeeperRegistry.currentCabinet.getHighTemp() * 10) {
                            if (times == null) {
                                //Send Error Msg
                                cabinetService.errorCall(AgentZookeeperRegistry.currentCabinet, iDevice.getSerialNumber(), tem, 1);
                                DevicesBatteryMap.getTempMap().put(iDevice.getSerialNumber(), 1);
                                SHCService.setGear(iDevice.getSerialNumber(), AgentZookeeperRegistry.currentCabinet.getLowGear());
                            } else {
                                DevicesBatteryMap.getTempMap().put(iDevice.getSerialNumber(), times + 1);
                            }
                            int out = AgentZookeeperRegistry.currentCabinet.getHighTempTime();
                            if (SHCService.getTemp(iDevice.getSerialNumber()) >= (out / 2)) {
                                //Send shutdown Msg
                                cabinetService.errorCall(AgentZookeeperRegistry.currentCabinet, iDevice.getSerialNumber(), tem, 2);
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
                        if (level >= AgentZookeeperRegistry.currentCabinet.getHighLevel()) {
                            SHCService.setGear(iDevice.getSerialNumber(), AgentZookeeperRegistry.currentCabinet.getLowGear());
                        } else if (needReset) {
                            SHCService.setGear(iDevice.getSerialNumber(), AgentZookeeperRegistry.currentCabinet.getHighGear());
                        }
                        if (level <= AgentZookeeperRegistry.currentCabinet.getLowLevel()) {
                            SHCService.setGear(iDevice.getSerialNumber(), AgentZookeeperRegistry.currentCabinet.getHighGear());
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        DevicesService devicesService = SpringTool.getBean(DevicesService.class);
        JSONObject result = new JSONObject();
        result.put("msg", "battery");
        result.put("detail", detail);
        result.put("agentId", AgentZookeeperRegistry.currentAgent.getId());
        try {
            devicesService.refreshDevicesBattery(result);
        } catch (Exception e) {
            log.error("Send battery msg failed, cause: ", e);
        }
    }

    public int getInt(String a) {
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(a);
        return Integer.parseInt(m.replaceAll("").trim());
    }
}
