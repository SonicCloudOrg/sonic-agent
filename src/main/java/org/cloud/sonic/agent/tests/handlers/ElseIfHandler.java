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
 * else if 条件步骤
 *
 * @author JayWenStar
 * @date 2022/3/13 2:23 下午
 */
@Component
public class ElseIfHandler implements StepHandler {

    @Autowired
    private NoneConditionHandler noneConditionHandler;
    @Autowired
    private StepHandlers stepHandlers;

    @Override
    public HandleDes runStep(JSONObject stepJSON, HandleDes handleDes, RunStepThread thread) throws Throwable {
        if (thread.isStopped()) {
            return null;
        }
        // else if 前应当必定有if，如果前面的if执行成功，则直接跳过
        if (handleDes.getE() == null) {
            thread.getLogTool().sendStepLog(StepType.WARN, "「else if」前的条件步骤执行通过，「else if」跳过", "");
            return handleDes;
        }
        handleDes.clear();

        // 取出 else if下的步骤集合
        JSONObject conditionStep = stepJSON.getJSONObject("step");
        List<JSONObject> steps = conditionStep.getJSONArray("childSteps").toJavaList(JSONObject.class);
        // 执行条件步骤
        thread.getLogTool().sendStepLog(StepType.PASS, "开始执行「else if」步骤", "");
        noneConditionHandler.runStep(stepJSON, handleDes, thread);
        // 上述步骤没有异常则取出else if下的步骤，再次丢给 stepHandlers 处理
        if (handleDes.getE() == null) {
            thread.getLogTool().sendStepLog(StepType.PASS, "「else if」步骤通过，开始执行「else if」子步骤", "");
            handleDes.clear();
            for (JSONObject step : steps) {
                stepHandlers.runStep(handlerPublicStep(step), handleDes, thread);
            }
            thread.getLogTool().sendStepLog(StepType.PASS, "「else if」子步骤执行完毕", "");
        } else {
            thread.getLogTool().sendStepLog(StepType.WARN, "「else if」步骤执行失败，跳过", "");
        }
        return handleDes;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.ELSE_IF;
    }
}
