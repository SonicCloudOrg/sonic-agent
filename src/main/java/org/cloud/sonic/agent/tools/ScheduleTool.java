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
package org.cloud.sonic.agent.tools;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author JayWenStar
 * @date 2022/4/25 11:21 上午
 */
public class ScheduleTool {

    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors() << 1
    );

    public static void scheduleAtFixedRate(Runnable command,
                                           long initialDelay,
                                           long period,
                                           TimeUnit unit) {
        scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

}
