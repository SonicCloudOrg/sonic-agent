/**
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
package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.HandleDes;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.enums.ConditionEnum;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * while 条件步骤
 *
 * @author JayWenStar
 * @date 2022/3/13 2:33 下午
 */
@Component
public class WhileHandler implements StepHandler {

    @Autowired
    private NoneConditionHandler noneConditionHandler;
    @Autowired
    private StepHandlers stepHandlers;

    @Override
    public HandleDes runStep(JSONObject stepJSON, HandleDes handleDes, RunStepThread thread) throws Throwable {
        if (thread.isStopped()) {
            return null;
        }
        handleDes.clear();

        // 取出 while 下的步骤集合
        JSONObject conditionStep = stepJSON.getJSONObject("step");
        List<JSONObject> steps = conditionStep.getJSONArray("childSteps").toJavaList(JSONObject.class);
        // 设置了判断条件步骤，则先运行判断条件的步骤
        thread.getLogTool().sendStepLog(StepType.PASS, "开始执行「while」步骤", "");
        noneConditionHandler.runStep(stepJSON, handleDes, thread);
        int i = 1;
        while(handleDes.getE() == null && !thread.isStopped()) {
            // 条件步骤成功，取出while下所属的步骤丢给stepHandlers处理
            thread.getLogTool().sendStepLog(StepType.PASS, "「while」步骤通过，开始执行第「" + i + "」次子步骤循环", "");
            for (JSONObject step : steps) {
                stepHandlers.runStep(handlerPublicStep(step), handleDes, thread);
            }
            thread.getLogTool().sendStepLog(StepType.PASS, "第「" + i + "」次子步骤执行完毕", "");

            handleDes.clear();
            thread.getLogTool().sendStepLog(StepType.PASS, "开始执行第「" + (i+1) + "」次「while」步骤", "");
            noneConditionHandler.runStep(stepJSON, handleDes, thread);

            i++;
        }
        if (handleDes.getE() != null) {
            thread.getLogTool().sendStepLog(StepType.WARN, "「while」步骤执行失败，循环结束", "");
        }
        if (thread.isStopped()) {
            thread.getLogTool().sendStepLog(StepType.WARN, "「while」被强制中断", "");
        }
        // 不满足条件则返回
        return handleDes;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.WHILE;
    }
}
