/**
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
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.automation.HandleDes;
import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.enums.ConditionEnum;
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
