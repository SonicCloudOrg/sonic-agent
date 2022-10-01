package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.models.HandleDes;
import org.cloud.sonic.agent.common.enums.ConditionEnum;
import org.cloud.sonic.agent.tests.common.RunStepThread;

/**
 * @author JayWenStar
 * @date 2022/3/13 1:29 下午
 */
public interface StepHandler {

    /**
     * 如果返回null则表示任务停止了
     */
    HandleDes runStep(JSONObject step, HandleDes handleDes, RunStepThread thread) throws Throwable;

    ConditionEnum getCondition();

    default JSONObject handlerPublicStep(JSONObject step) {
        if (step.containsKey("pubSteps")) {
            return step;
        }
        return new JSONObject(){
            {
                put("step", step);
            }
        };
    }
}
