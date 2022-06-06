/*
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
package org.cloud.sonic.agent.tools;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author JayWenStar
 * @date 2022/4/25 11:21 上午
 */
public class ScheduleTool {

    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors() << 1,
            new DefaultThreadFactory("agent-scheduled")
    );

    public static void scheduleAtFixedRate(Runnable command,
                                    long initialDelay,
                                    long period,
                                    TimeUnit unit) {
        scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

}
