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
import org.cloud.sonic.agent.common.enums.ConditionEnum;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.models.HandleContext;
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
    @IteratorCheck
    public HandleContext runStep(JSONObject stepJSON, HandleContext handleContext, RunStepThread thread) throws Throwable {
        if (thread.isStopped()) {
            return null;
        }
        handleContext = new HandleContext();

        // 取出 while 下的步骤集合
        JSONObject conditionStep = stepJSON.getJSONObject("step");
        List<JSONObject> steps = conditionStep.getJSONArray("childSteps").toJavaList(JSONObject.class);
        // 设置了判断条件步骤，则先运行判断条件的步骤
        thread.getLogTool().sendStepLog(StepType.PASS, "开始执行「while」步骤", "");
        try {
            noneConditionHandler.runStep(stepJSON, handleContext, thread);
        } catch (Throwable e) {
            handleContext.setE(e);
        }
        int i = 1;
        while (handleContext.getE() == null && !thread.isStopped()) {
            // 条件步骤成功，取出while下所属的步骤丢给stepHandlers处理
            thread.getLogTool().sendStepLog(StepType.PASS, "「while」步骤通过，开始执行第「" + i + "」次子步骤循环", "");
            for (JSONObject step : steps) {
                stepHandlers.runStep(handlerPublicStep(step), handleContext, thread);
            }
            thread.getLogTool().sendStepLog(StepType.PASS, "第「" + i + "」次子步骤执行完毕", "");

            handleContext.clear();
            thread.getLogTool().sendStepLog(StepType.PASS, "开始执行第「" + (i + 1) + "」次「while」步骤", "");
            noneConditionHandler.runStep(stepJSON, handleContext, thread);

            i++;
        }
        if (handleContext.getE() != null) {
            if ("exit while".equals(handleContext.getE().getMessage())) {
                handleContext.setE(null);
                thread.getLogTool().sendStepLog(StepType.WARN, "「while」步骤执行退出，循环结束", "");
            } else {
                thread.getLogTool().sendStepLog(StepType.WARN, "「while」步骤执行失败，循环结束", "");
            }
        }
        if (thread.isStopped()) {
            thread.getLogTool().sendStepLog(StepType.WARN, "「while」被强制中断", "");
        }
        // 不满足条件则返回
        return handleContext;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.WHILE;
    }
}
