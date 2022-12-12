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
