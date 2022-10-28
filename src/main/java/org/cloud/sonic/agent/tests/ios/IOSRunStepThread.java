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
import org.cloud.sonic.agent.common.models.HandleDes;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.StepHandlers;
import org.cloud.sonic.agent.tools.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class IOSRunStepThread extends RunStepThread {

    private final Logger log = LoggerFactory.getLogger(IOSRunStepThread.class);

    public final static String IOS_RUN_STEP_TASK_PRE = "ios-run-step-task-%s-%s-%s";

    private final IOSTestTaskBootThread iosTestTaskBootThread;

    public IOSRunStepThread(IOSTestTaskBootThread iosTestTaskBootThread) {
        this.iosTestTaskBootThread = iosTestTaskBootThread;

        this.setDaemon(true);
        this.setName(iosTestTaskBootThread.formatThreadName(IOS_RUN_STEP_TASK_PRE));
        setPlatformType(PlatformType.IOS);
        setLogTool(iosTestTaskBootThread.getIosStepHandler().getLog());
    }

    public IOSTestTaskBootThread getIosTestTaskBootThread() {
        return iosTestTaskBootThread;
    }

    @Override
    public void run() {
        StepHandlers stepHandlers = SpringTool.getBean(StepHandlers.class);
        JSONObject jsonObject = iosTestTaskBootThread.getJsonObject();
        List<JSONObject> steps = jsonObject.getJSONArray("steps").toJavaList(JSONObject.class);

        // 复用同一个handleDes
        HandleDes handleDes = new HandleDes();
        for (JSONObject step : steps) {
            if (isStopped()) {
                return;
            }
            try {
                stepHandlers.runStep(step, handleDes, this);
            } catch (Throwable e) {
                break;
            }
        }

    }
}