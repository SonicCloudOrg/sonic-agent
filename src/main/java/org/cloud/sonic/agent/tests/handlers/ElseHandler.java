package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.HandleDes;
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
            return handleDes;
        }

        // 取出 else 下所属的步骤，丢给stepHandlers处理
        List<JSONObject> steps = stepJSON.getJSONArray("steps").toJavaList(JSONObject.class);
        // 上述步骤无异常则取出else if下的步骤，再次丢给 stepHandlers 处理
        if (handleDes.getE() == null) {
            for (JSONObject step : steps) {
                stepHandlers.runStep(step, handleDes, thread);
            }
        }
        return handleDes;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.ELSE;
    }
}