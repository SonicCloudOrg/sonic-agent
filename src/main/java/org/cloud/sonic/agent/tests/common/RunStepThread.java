/**
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
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
