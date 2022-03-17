package org.cloud.sonic.agent.tests.common;

import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.tools.LogTool;

/**
 * @author JayWenStar
 * @date 2022/2/11 10:49 上午
 */
public class RunStepThread extends Thread {

    protected volatile boolean stopped = false;

    protected volatile int platformType;

    protected LogTool logTool;

    public int getPlatformType() {
        return platformType;
    }

    public void setPlatformType(int platformType) {
        this.platformType = platformType;
    }

    public LogTool getLogTool() {
        return logTool;
    }

    public void setLogTool(LogTool logTool) {
        this.logTool = logTool;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }
}
