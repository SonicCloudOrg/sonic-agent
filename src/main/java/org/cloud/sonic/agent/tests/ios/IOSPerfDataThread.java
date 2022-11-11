package org.cloud.sonic.agent.tests.ios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 暂未开放
 */
public class IOSPerfDataThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(IOSPerfDataThread.class);

    public final static String IOS_PERF_DATA_TASK_PRE = "ios-perf-data-task-%s-%s-%s";

    private final IOSTestTaskBootThread iosTestTaskBootThread;

    public IOSPerfDataThread(IOSTestTaskBootThread iosTestTaskBootThread) {
        this.iosTestTaskBootThread = iosTestTaskBootThread;

        this.setDaemon(true);
        this.setName(iosTestTaskBootThread.formatThreadName(IOS_PERF_DATA_TASK_PRE));
    }

    public IOSTestTaskBootThread getIosTestTaskBootThread() {
        return iosTestTaskBootThread;
    }

    @Override
    public void run() {
        return;
    }
}
