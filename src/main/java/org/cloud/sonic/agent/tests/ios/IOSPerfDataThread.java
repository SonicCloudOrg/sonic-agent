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
package org.cloud.sonic.agent.tests.ios;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.tests.handlers.IOSStepHandler;
import org.cloud.sonic.agent.bridge.ios.SibTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        JSONObject perf = iosTestTaskBootThread.getJsonObject().getJSONObject("perf");
        if (perf.getInteger("isOpen") == 1) {
            String udId = iosTestTaskBootThread.getUdId();
            IOSStepHandler iosStepHandler = iosTestTaskBootThread.getIosStepHandler();
            SibTool.startPerfmon(udId, "", null,
                    iosStepHandler.getLog(), perf.getInteger("perfInterval"));
            boolean hasTarget = false;
            while (iosTestTaskBootThread.getRunStepThread().isAlive()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
                if (iosStepHandler.getTargetPackage().length() != 0 && !hasTarget) {
                    SibTool.startPerfmon(udId, iosStepHandler.getTargetPackage(), null,
                            iosStepHandler.getLog(), perf.getInteger("perfInterval"));
                    hasTarget = true;
                }
            }
            SibTool.stopPerfmon(udId);
        }
    }
}
