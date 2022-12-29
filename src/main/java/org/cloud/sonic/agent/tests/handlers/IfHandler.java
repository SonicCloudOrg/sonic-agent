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
import org.cloud.sonic.agent.aspect.IteratorCheck;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.enums.ConditionEnum;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * if 条件步骤
 *
 * @author JayWenStar
 * @date 2022/3/13 2:20 下午
 */
@Component
public class IfHandler implements StepHandler {

    @Autowired
    private NoneConditionHandler noneConditionHandler;
    @Autowired
    private StepHandlers stepHandlers;

    @Override
    @IteratorCheck
    public HandleContext runStep(JSONObject stepJSON, HandleContext handleContext, RunStepThread thread) throws Throwable {
        if (thread.isStopped()) {
            return null;
        }
        handleContext.clear();

        // 取出if下的步骤集合
        JSONObject conditionStep = stepJSON.getJSONObject("step");
        List<JSONObject> steps = conditionStep.getJSONArray("childSteps").toJavaList(JSONObject.class);
        // 执行条件步骤
        thread.getLogTool().sendStepLog(StepType.PASS, "开始执行「if」步骤", "");
        noneConditionHandler.runStep(stepJSON, handleContext, thread);
        // 上述步骤无异常则取出if下的步骤，再次丢给 stepHandlers 处理
        if (handleContext.getE() == null) {
            thread.getLogTool().sendStepLog(StepType.PASS, "「if」步骤通过，开始执行子步骤", "");
            for (JSONObject step : steps) {
                stepHandlers.runStep(handlerPublicStep(step), handleContext, thread);
            }
            thread.getLogTool().sendStepLog(StepType.PASS, "「if」子步骤执行完毕", "");
        } else {
            thread.getLogTool().sendStepLog(StepType.WARN, "「if」步骤执行失败，跳过", "");
        }
        return handleContext;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.IF;
    }
}
