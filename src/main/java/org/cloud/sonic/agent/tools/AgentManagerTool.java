/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @author ZhouYiXun
 * @des Agent管理者
 * @date 2021/8/26 22:23
 */
@Component
public class AgentManagerTool {
    private final static Logger logger = LoggerFactory.getLogger(AgentManagerTool.class);

    private static ConfigurableApplicationContext context;

    @Autowired
    public void setContext(ConfigurableApplicationContext c) {
        AgentManagerTool.context = c;
    }

    public static void stop() {
        try {
            context.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("Bye！");
        System.exit(0);
    }
}
