package com.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.automation.AndroidStepHandler;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author JayWenStar
 * @date 2021/12/2 12:30 上午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class AndroidRunStepThread extends Thread {

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    @Setter(value = AccessLevel.NONE)
    public final static String ANDROID_RUN_STEP_TASK_PRE = "android-run-step-task-%s-%s-%s";

    private final AndroidTestTaskBootThread androidTestTaskBootThread;

    public AndroidRunStepThread(AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_RUN_STEP_TASK_PRE));
    }

    @Override
    public void run() {
        JSONObject jsonObject = androidTestTaskBootThread.getJsonObject();
        AndroidStepHandler androidStepHandler = androidTestTaskBootThread.getAndroidStepHandler();
        List<JSONObject> steps = jsonObject.getJSONArray("steps").toJavaList(JSONObject.class);

        for (JSONObject step : steps) {
            try {
                androidStepHandler.runStep(step);
            } catch (Throwable e) {
                break;
            }
        }

    }
}