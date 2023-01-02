package org.cloud.sonic.agent.tests.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * android性能数据获取线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:29 上午
 */
public class AndroidPerfDataThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(AndroidPerfDataThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_PERF_DATA_TASK_PRE = "android-perf-data-task-%s-%s-%s";

    private final AndroidTestTaskBootThread androidTestTaskBootThread;

    public AndroidPerfDataThread(AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_PERF_DATA_TASK_PRE));
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    @Override
    public void run() {

//        AndroidStepHandler androidStepHandler = androidTestTaskBootThread.getAndroidStepHandler();
//        AndroidRunStepThread runStepThread = androidTestTaskBootThread.getRunStepThread();

//        int tryTime = 0;
//        while (runStepThread.isAlive()) {
//            if (androidStepHandler.getAndroidDriver() == null) {
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    log.error("获取driver失败，错误信息{}" + e.getMessage());
//                    e.printStackTrace();
//                }
//                continue;
//            }
//            try {
//                androidStepHandler.getPerform();
//                Thread.sleep(30000);
//            } catch (Exception e) {
//                tryTime++;
//            }
//            if (tryTime > 10) {
//                break;
//            }
//        }
    }
}
