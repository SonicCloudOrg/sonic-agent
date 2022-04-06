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
