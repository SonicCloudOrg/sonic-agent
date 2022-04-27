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
package org.cloud.sonic.agent.tests.ios;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.common.interfaces.HubGear;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.shc.SHCService;
import org.cloud.sonic.common.services.DevicesService;
import org.cloud.sonic.common.tools.SpringTool;
import org.springframework.util.CollectionUtils;

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

    Boolean cabinetEnable = Boolean.valueOf(SpringTool.getPropertiesValue("sonic.agent.cabinet.enable"));

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        AgentManagerTool agentManagerTool = SpringTool.getBean(AgentManagerTool.class);
        if (!agentManagerTool.checkServerOnline()) {
            return;
        }

        List<String> deviceList = SibTool.getDeviceList();
        if (CollectionUtils.isEmpty(deviceList)) {
            return;
        }

        List<JSONObject> detail = new ArrayList<>();
        JSONObject devicesBattery;
        try {
            devicesBattery = SibTool.getAllDevicesBattery();
        } catch (Exception e) {
            return;
        }
        List<JSONObject> batteryList = devicesBattery.getJSONArray("batteryList").toJavaList(JSONObject.class);
        for (JSONObject dB : batteryList) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("udId", dB.getString("serialNumber"));
            jsonObject.put("tem", dB.getInteger("temperature"));
            jsonObject.put("level", dB.getInteger("level"));
            detail.add(jsonObject);
            //control
            if (cabinetEnable) {
                if (dB.getInteger("level") >= AgentZookeeperRegistry.currentCabinet.getHighLevel()) {
                    SHCService.setGear(dB.getString("serialNumber"), HubGear.LOW);
                }
                if (dB.getInteger("level") <= AgentZookeeperRegistry.currentCabinet.getLowLevel()) {
                    SHCService.setGear(dB.getString("serialNumber"), HubGear.HIGH);
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
}
