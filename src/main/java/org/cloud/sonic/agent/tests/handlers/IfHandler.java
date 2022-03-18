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
    public HandleDes runStep(JSONObject stepJSON, HandleDes handleDes, RunStepThread thread) throws Throwable {
        if (thread.isStopped()) {
            return null;
        }
        handleDes.clear();

        // 取出if下的步骤集合
        JSONObject conditionStep = stepJSON.getJSONObject("step");
        List<JSONObject> steps = conditionStep.getJSONArray("childSteps").toJavaList(JSONObject.class);
        // 执行条件步骤
        thread.getLogTool().sendStepLog(StepType.PASS, "开始执行「if」步骤", "");
        noneConditionHandler.runStep(stepJSON, handleDes, thread);
        // 上述步骤无异常则取出if下的步骤，再次丢给 stepHandlers 处理
        if (handleDes.getE() == null) {
            thread.getLogTool().sendStepLog(StepType.PASS, "「if」步骤通过，开始执行子步骤", "");
            for (JSONObject step : steps) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("step", step);
                stepHandlers.runStep(jsonObject, handleDes, thread);
            }
            thread.getLogTool().sendStepLog(StepType.PASS, "「if」子步骤执行完毕", "");
        } else {
            thread.getLogTool().sendStepLog(StepType.WARN, "「if」步骤执行失败，跳过", "");
        }
        return handleDes;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.IF;
    }
}
