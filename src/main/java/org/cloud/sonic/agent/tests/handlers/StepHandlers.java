/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.models.HandleDes;
import org.cloud.sonic.agent.common.enums.ConditionEnum;
import org.cloud.sonic.agent.common.enums.SonicEnum;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
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
public class StepHandlers implements ApplicationListener<ContextRefreshedEvent> {


    private final Logger logger = LoggerFactory.getLogger(StepHandlers.class);

    private final ConcurrentHashMap<ConditionEnum, StepHandler> stepHandlers =
            new ConcurrentHashMap<>(8);

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

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        addConditionHandlers(event.getApplicationContext().getBeansOfType(StepHandler.class).values());
        logger.info("Registered {} condition handler(s)", stepHandlers.size());
    }
}
