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
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.common.services.DevicesService;
import org.cloud.sonic.common.tools.SpringTool;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class AndroidBatteryThread extends Thread {
    @Override
    public void run() {
        AgentManagerTool agentManagerTool = SpringTool.getBean(AgentManagerTool.class);
        while (agentManagerTool.checkServerOnline()) {
            IDevice[] deviceList = AndroidDeviceBridgeTool.getRealOnLineDevices();
            if (deviceList == null) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            List<JSONObject> detail = new ArrayList<>();
            for (IDevice iDevice : deviceList) {
                JSONObject jsonObject = new JSONObject();
                String temper = AndroidDeviceBridgeTool
                        .executeCommand(iDevice, "dumpsys battery");
                if (StringUtils.hasText(temper)) {
                    String realTem = temper.substring(temper.indexOf("temperature")).trim();
                    int tem = getInt(realTem.substring(13, realTem.indexOf("\n")));
                    String realLevel = temper.substring(temper.indexOf("level")).trim();
                    int level = getInt(realLevel.substring(7, realLevel.indexOf("\n")));
                    jsonObject.put("udId", iDevice.getSerialNumber());
                    jsonObject.put("tem", tem);
                    jsonObject.put("level", level);
                    detail.add(jsonObject);
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
                log.error("发送电量信息失败，错误信息：", e);
            }
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public int getInt(String a) {
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(a);
        return Integer.parseInt(m.replaceAll("").trim());
    }
}
