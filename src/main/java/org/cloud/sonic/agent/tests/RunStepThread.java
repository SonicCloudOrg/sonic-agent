/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.tests;

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
