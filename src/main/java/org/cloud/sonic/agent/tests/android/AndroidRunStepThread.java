package org.cloud.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.automation.HandleDes;
import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.StepHandlers;
import org.cloud.sonic.agent.tools.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * android测试任务步骤运行线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:30 上午
 */
public class AndroidRunStepThread extends RunStepThread {

    private final Logger log = LoggerFactory.getLogger(AndroidRunStepThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_RUN_STEP_TASK_PRE = "android-run-step-task-%s-%s-%s";

    private final AndroidTestTaskBootThread androidTestTaskBootThread;

    public AndroidRunStepThread(AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_RUN_STEP_TASK_PRE));
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    @Override
    public void run() {
        StepHandlers stepHandlers = SpringTool.getBean(StepHandlers.class);
        JSONObject jsonObject = androidTestTaskBootThread.getJsonObject();
        List<JSONObject> steps = jsonObject.getJSONArray("steps").toJavaList(JSONObject.class);
        setPlatformType(PlatformType.ANDROID);

        for (JSONObject step : steps) {
            if (isStopped()) {
                return;
            }
            try {
                stepHandlers.runStep(step, new HandleDes(), this);
            } catch (Throwable e) {
                break;
            }
        }

    }
}