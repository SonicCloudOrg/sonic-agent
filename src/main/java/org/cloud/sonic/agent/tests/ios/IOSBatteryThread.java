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
import org.cloud.sonic.agent.netty.NettyClientHandler;
import org.cloud.sonic.agent.netty.NettyThreadPool;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.shc.SHCService;
import org.cloud.sonic.agent.tools.SpringTool;
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
        if (NettyClientHandler.channel == null) {
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
            if (cabinetEnable && BytesTool.currentCabinet != null) {
                if (dB.getInteger("level") >= BytesTool.currentCabinet.getHighLevel()) {
                    SHCService.setGear(dB.getString("serialNumber"), BytesTool.currentCabinet.getLowGear());
                }
                if (dB.getInteger("level") <= BytesTool.currentCabinet.getLowLevel()) {
                    SHCService.setGear(dB.getString("serialNumber"), BytesTool.currentCabinet.getHighGear());
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("msg", "battery");
        result.put("detail", detail);
        try {
            NettyThreadPool.send(result);
        } catch (Exception e) {
            log.error("Send battery msg failed, cause: ", e);
        }
    }
}
