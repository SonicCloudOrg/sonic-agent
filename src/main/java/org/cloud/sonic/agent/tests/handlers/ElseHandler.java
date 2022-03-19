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
    public HandleDes runStep(JSONObject stepJSON, HandleDes handleDes, RunStepThread thread) throws Throwable {
        if (thread.isStopped()) {
            return null;
        }
        // else 前应当必定有if，如果前面的if执行成功，则直接跳过
        if (handleDes.getE() == null) {
            thread.getLogTool().sendStepLog(StepType.WARN, "「else」前的条件步骤执行通过，「else」跳过", "");
            return handleDes;
        }
        thread.getLogTool().sendStepLog(StepType.PASS, "「else」前的条件步骤失败，开始执行「else」下的步骤", "");

        // 取出 else 下所属的步骤，丢给stepHandlers处理
        List<JSONObject> steps = stepJSON.getJSONObject("step").getJSONArray("childSteps").toJavaList(JSONObject.class);
        // 上述步骤有异常则取出 else 下的步骤，再次丢给 stepHandlers 处理
        if (handleDes.getE() != null) {
            handleDes.clear();
            for (JSONObject step : steps) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("step", step);
                stepHandlers.runStep(jsonObject, handleDes, thread);
            }
        }
        thread.getLogTool().sendStepLog(StepType.PASS, "「else」步骤执行完毕", "");
        return handleDes;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.ELSE;
    }
}
