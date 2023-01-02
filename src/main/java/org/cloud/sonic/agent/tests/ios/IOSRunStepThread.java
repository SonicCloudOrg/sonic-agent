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
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.models.HandleContext;
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
        HandleContext handleContext = new HandleContext();
        for (JSONObject step : steps) {
            if (isStopped()) {
                return;
            }
            try {
                stepHandlers.runStep(step, handleContext, this);
            } catch (Throwable e) {
                break;
            }
        }

    }
}