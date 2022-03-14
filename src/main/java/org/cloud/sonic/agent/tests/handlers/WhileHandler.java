package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.HandleDes;
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

        // 取出 while 判断条件的步骤
        JSONObject conditionStep = null;
        int count = 1;
        int i = 0;
        if (stepJSON.containsKey("conditionStep")) {
            conditionStep = stepJSON.getJSONObject("conditionStep");
        } else {
            count = stepJSON.getInteger("count");
        }
        List<JSONObject> steps = stepJSON.getJSONArray("steps").toJavaList(JSONObject.class);
        // while 可以手动设置循环次数，至少为一次，在没有判断条件的步骤时才会进入
        if (conditionStep == null) {
            while (i < count) {
                for (JSONObject step : steps) {
                    stepHandlers.runStep(step, handleDes, thread);
                }
                i++;
            }
        } else {
            // 设置了判断条件步骤，则先运行判断条件的步骤
            noneConditionHandler.runStep(conditionStep, handleDes, thread);
            while(handleDes.getE() == null) {
                // 条件步骤成功，取出while下所属的步骤丢给stepHandlers处理
                for (JSONObject step : steps) {
                    stepHandlers.runStep(step, handleDes, thread);
                }
                noneConditionHandler.runStep(conditionStep, handleDes, thread);
            }
            // 不满足条件则返回
        }
        return handleDes;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.WHILE;
    }
}
