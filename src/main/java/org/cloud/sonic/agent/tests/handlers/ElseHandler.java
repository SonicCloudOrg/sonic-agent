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
package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.aspect.PocoIteratorCheck;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.enums.ConditionEnum;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * else 条件步骤
 *
 * @author JayWenStar
 * @date 2022/3/13 2:30 下午
 */
@Component
public class ElseHandler implements StepHandler {

    @Autowired
    private StepHandlers stepHandlers;

    @Override
    @PocoIteratorCheck
    public HandleContext runStep(JSONObject stepJSON, HandleContext handleContext, RunStepThread thread) throws Throwable {
        if (thread.isStopped()) {
            return null;
        }
        // else 前应当必定有if，如果前面的if执行成功，则直接跳过
        if (handleContext.getE() == null) {
            thread.getLogTool().sendStepLog(StepType.WARN, "「else」前的条件步骤执行通过，「else」跳过", "");
            return handleContext;
        }
        thread.getLogTool().sendStepLog(StepType.PASS, "「else」前的条件步骤失败，开始执行「else」下的步骤", "");

        // 取出 else 下所属的步骤，丢给stepHandlers处理
        List<JSONObject> steps = stepJSON.getJSONObject("step").getJSONArray("childSteps").toJavaList(JSONObject.class);
        // 上述步骤有异常则取出 else 下的步骤，再次丢给 stepHandlers 处理
        if (handleContext.getE() != null) {
            handleContext.clear();
            for (JSONObject step : steps) {
                stepHandlers.runStep(handlerPublicStep(step), handleContext, thread);
            }
        }
        thread.getLogTool().sendStepLog(StepType.PASS, "「else」步骤执行完毕", "");
        return handleContext;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.ELSE;
    }
}
