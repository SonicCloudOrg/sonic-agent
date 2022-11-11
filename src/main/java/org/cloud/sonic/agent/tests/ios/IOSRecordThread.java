package org.cloud.sonic.agent.tests.ios;

import org.cloud.sonic.agent.automation.IOSStepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IOSRecordThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(IOSRecordThread.class);

    public final static String IOS_RECORD_TASK_PRE = "ios-record-task-%s-%s-%s";

    private final IOSTestTaskBootThread iosTestTaskBootThread;

    public IOSRecordThread(IOSTestTaskBootThread iosTestTaskBootThread) {
        this.iosTestTaskBootThread = iosTestTaskBootThread;

        this.setDaemon(true);
        this.setName(iosTestTaskBootThread.formatThreadName(IOS_RECORD_TASK_PRE));
    }

    public IOSTestTaskBootThread getIosTestTaskBootThread() {
        return iosTestTaskBootThread;
    }

    @Override
    public void run() {
//        IOSStepHandler iosStepHandler = iosTestTaskBootThread.getIosStepHandler();
//        IOSRunStepThread runStepThread = iosTestTaskBootThread.getRunStepThread();
//
//        while (runStepThread.isAlive()) {
//            if (iosStepHandler.getDriver() == null) {
//                try {
//                    Thread.sleep(500);
//                } catch (InterruptedException e) {
//                    log.error(e.getMessage());
//                }
//                continue;
//            }
//            try {
//                iosStepHandler.startRecord();
//            } catch (Exception e) {
//                log.error(e.getMessage());
//            }
//            int w = 0;
//            while (w < 10 && (runStepThread.isAlive())) {
//                try {
//                    Thread.sleep(10000);
//                } catch (InterruptedException e) {
//                    log.error(e.getMessage());
//                }
//                w++;
//            }
//            //处理录像
//            if (iosStepHandler.getStatus() == 3) {
//                iosStepHandler.stopRecord();
//                return;
//            } else {
//                iosStepHandler.getDriver().stopRecordingScreen();
//            }
//        }
    }
}
