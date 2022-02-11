package org.cloud.sonic.agent.tests.common;

/**
 * @author JayWenStar
 * @date 2022/2/11 10:49 上午
 */
public class RunStepThread extends Thread {

    protected volatile boolean stopped = false;

    public boolean isStopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }
}
