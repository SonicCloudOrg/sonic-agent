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
package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.models.HandleDes;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.enums.ConditionEnum;
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
