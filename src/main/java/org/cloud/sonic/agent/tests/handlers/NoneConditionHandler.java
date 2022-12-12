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
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.common.models.HandleDes;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.enums.ConditionEnum;
import org.cloud.sonic.agent.tests.android.AndroidRunStepThread;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.ios.IOSRunStepThread;
import org.springframework.stereotype.Component;

/**
 * 非条件步骤
 *
 * @author JayWenStar
 * @date 2022/3/13 2:20 下午
 */
@Component
public class NoneConditionHandler implements StepHandler {

    @Override
    public HandleDes runStep(JSONObject stepJSON, HandleDes handleDes, RunStepThread thread) throws Throwable {
        if (thread.isStopped()) {
            return null;
        }
        handleDes.clear();

        switch (thread.getPlatformType()) {
            case PlatformType.ANDROID:
                AndroidRunStepThread androidRunStepThread = (AndroidRunStepThread) thread;
                AndroidStepHandler androidStepHandler = androidRunStepThread.getAndroidTestTaskBootThread().getAndroidStepHandler();
                androidStepHandler.runStep(stepJSON, handleDes);
                break;
            case PlatformType.IOS:
                IOSRunStepThread iosRunStepThread = (IOSRunStepThread) thread;
                IOSStepHandler iosStepHandler = iosRunStepThread.getIosTestTaskBootThread().getIosStepHandler();
                iosStepHandler.runStep(stepJSON, handleDes);
                break;
        }
        return handleDes;
    }

    @Override
    public ConditionEnum getCondition() {
        return ConditionEnum.NONE;
    }
}
