package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.HandleDes;
import org.cloud.sonic.agent.enums.ConditionEnum;
import org.cloud.sonic.agent.enums.SonicEnum;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author JayWenStar
 * @date 2022/3/13 3:23 下午
 */
@Component
public class StepHandlers {

    private final Logger logger = LoggerFactory.getLogger(StepHandlers.class);

    private final ConcurrentHashMap<ConditionEnum, StepHandler> stepHandlers =
            new ConcurrentHashMap<>(8);

    public StepHandlers(ApplicationContext applicationContext) {
        addConditionHandlers(applicationContext.getBeansOfType(StepHandler.class).values());
        logger.info("Registered {} condition handler(s)", stepHandlers.size());
    }

    public HandleDes runStep(JSONObject stepJSON, HandleDes handleDes, RunStepThread thread) throws Throwable {
        JSONObject step = stepJSON.getJSONObject("step");
        Integer conditionType = step.getInteger("conditionType");
        getSupportedCondition(SonicEnum.valueToEnum(ConditionEnum.class, conditionType))
                .runStep(stepJSON, handleDes, thread);
        return handleDes;
    }

    @NonNull
    public StepHandlers addConditionHandlers(@Nullable Collection<StepHandler> stepHandlers) {
        if (!CollectionUtils.isEmpty(stepHandlers)) {
            for (StepHandler handler : stepHandlers) {
                if (this.stepHandlers.containsKey(handler.getCondition())) {
                    throw new RuntimeException("Same condition type implements must be unique");
                }
                this.stepHandlers.put(handler.getCondition(), handler);
            }
        }
        return this;
    }

    private StepHandler getSupportedCondition(ConditionEnum conditionEnum) {
        StepHandler handler = stepHandlers.getOrDefault(conditionEnum, null);
        if (handler == null) {
            throw new RuntimeException("condition handler for 「" + conditionEnum + "」 not found");
        }
        return handler;
    }
}
