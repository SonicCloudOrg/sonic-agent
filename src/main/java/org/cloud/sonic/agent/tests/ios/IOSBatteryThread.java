/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.tests.ios;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.cloud.sonic.agent.tools.BytesTool;
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

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        if (TransportWorker.client == null) {
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
