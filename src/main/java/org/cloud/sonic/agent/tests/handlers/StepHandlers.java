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
    public HandleContext runStep(JSONObject stepJSON, HandleContext handleContext, RunStepThread thread) throws Throwable {
        JSONObject step = stepJSON.getJSONObject("step");
        Integer conditionType = step.getInteger("conditionType");
        getSupportedCondition(SonicEnum.valueToEnum(ConditionEnum.class, conditionType))
                .runStep(stepJSON, handleContext, thread);
        return handleContext;
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
