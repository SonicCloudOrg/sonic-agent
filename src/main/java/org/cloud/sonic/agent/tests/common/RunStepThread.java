package org.cloud.sonic.agent.tests.common;

import org.cloud.sonic.agent.tests.LogUtil;

/**
 * @author JayWenStar
 * @date 2022/2/11 10:49 上午
 */
public class RunStepThread extends Thread {

    protected volatile boolean stopped = false;

    protected volatile int platformType;

    protected LogUtil logUtil;

    public int getPlatformType() {
        return platformType;
    }

    public void setPlatformType(int platformType) {
        this.platformType = platformType;
    }

    public LogUtil getLogTool() {
        return logUtil;
    }

    public void setLogTool(LogUtil logUtil) {
        this.logUtil = logUtil;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }
}
