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
package org.cloud.sonic.agent.tools;

import com.alibaba.fastjson.JSONObject;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.service.EchoService;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.common.maps.AndroidPasswordMap;
import org.cloud.sonic.agent.common.maps.HandlerMap;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.agent.tests.TaskManager;
import org.cloud.sonic.agent.tests.android.AndroidRunStepThread;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.cloud.sonic.agent.tests.ios.IOSRunStepThread;
import org.cloud.sonic.agent.tests.ios.IOSTestTaskBootThread;
import org.cloud.sonic.common.models.domain.Devices;
import org.cloud.sonic.common.models.domain.Users;
import org.cloud.sonic.common.services.*;
import org.cloud.sonic.common.tools.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @author ZhouYiXun
 * @des Agent管理者
 * @date 2021/8/26 22:23
 */
@Component
public class AgentManagerTool {
    private final static Logger logger = LoggerFactory.getLogger(AgentManagerTool.class);

    private static ConfigurableApplicationContext context;

    @DubboReference private TestCasesService testCasesService;
    @DubboReference private DevicesService devicesService;
    @DubboReference private UsersService usersService;
    @DubboReference private AgentsService agentsService;
    @DubboReference private ResultDetailService resultDetailService;

    @Autowired
    public void setContext(ConfigurableApplicationContext c) {
        AgentManagerTool.context = c;
    }

    public static void stop() {
        stop("Bye！");
    }

    public static void stop(String tips) {
        try {
            context.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info(tips);
        System.exit(0);
    }

    /**
     * Android 步骤调试
     */
    public void runAndroidStep(JSONObject jsonObject) {

        AndroidPasswordMap.getMap().put(jsonObject.getString("udId"), jsonObject.getString("pwd"));
        AndroidStepHandler androidStepHandler = HandlerMap.getAndroidMap().get(jsonObject.getString("sessionId"));
        if (androidStepHandler == null) {
            return;
        }
        androidStepHandler.resetResultDetailStatus();
        androidStepHandler.setGlobalParams(jsonObject.getJSONObject("gp"));

        AndroidTestTaskBootThread dataBean = new AndroidTestTaskBootThread(jsonObject, androidStepHandler);
        AndroidRunStepThread task = new AndroidRunStepThread(dataBean) {
            @Override
            public void run() {
                super.run();
                androidStepHandler.sendStatus();
            }
        };
        TaskManager.startChildThread(task.getName(), task);
    }

    /**
     * IOS步骤调试
     */
    public void runIOSStep(JSONObject jsonObject) {
        IOSStepHandler iosStepHandler = HandlerMap.getIOSMap().get(jsonObject.getString("sessionId"));
        if (iosStepHandler == null) {
            return;
        }
        iosStepHandler.resetResultDetailStatus();
        iosStepHandler.setGlobalParams(jsonObject.getJSONObject("gp"));

        IOSTestTaskBootThread dataBean = new IOSTestTaskBootThread(jsonObject, iosStepHandler);

        IOSRunStepThread task = new IOSRunStepThread(dataBean) {
            @Override
            public void run() {
                super.run();
                iosStepHandler.sendStatus();
            }
        };
        TaskManager.startChildThread(task.getName(), task);
    }

    public JSONObject findSteps(Integer caseId, String sessionId, String pwd, String udId) {
        JSONObject j = testCasesService.findSteps(caseId);
        JSONObject steps = new JSONObject();
        steps.put("cid", caseId);
        steps.put("pf", j.get("pf"));
        steps.put("steps", j.get("steps"));
        steps.put("gp", j.get("gp"));
        steps.put("sessionId", sessionId);
        steps.put("pwd", pwd);
        steps.put("udId", udId);
        return steps;
    }


    public void updateDebugUser(String udId, String token) {
        Users users = usersService.getUserInfo(token);
        Devices devices = devicesService.findByAgentIdAndUdId(AgentZookeeperRegistry.currentAgent.getId(), udId);
        devices.setUser(users.getUserName());
        devicesService.save(devices);
    }

    /**
     * server端更新设备状态
     *
     * @param jsonMsg  具体参数请参考server的实现，暂时没来得及抛弃map传参
     */
    public void devicesStatus(JSONObject jsonMsg) {
        devicesService.deviceStatus(jsonMsg);
    }

    /**
     * @return server在线则返回true
     */
    public boolean checkServerOnline() {
        String msg = "OK";
        String res = "";
        EchoService echoService = (EchoService) agentsService;
        try {
            res = echoService.$echo(msg) + "";
            return msg.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * server端保存step(用例日志)、perform(性能数据)、record（录像url数据）、status（用例状态）
     *
     * @param jsonMsg  具体参数请参考server的实现，暂时没来得及抛弃map传参
     */
    public void saveByTransport(JSONObject jsonMsg) {
        resultDetailService.saveByTransport(jsonMsg);
    }
}
