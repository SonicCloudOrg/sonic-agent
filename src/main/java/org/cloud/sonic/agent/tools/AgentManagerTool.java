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
package org.cloud.sonic.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
