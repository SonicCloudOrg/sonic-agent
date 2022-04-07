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
package org.cloud.sonic.agent.automation;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/8/27 17:24
 */
public class AppiumServer {
    public static AppiumDriverLocalService service;

    /**
     * @return void
     * @author ZhouYiXun
     * @des 启动appium服务
     * @date 2021/8/16 20:01
     */
    public static void start(int port) {
        AppiumServiceBuilder appiumServiceBuilder = new AppiumServiceBuilder();
        if (port == 0) {
            appiumServiceBuilder.usingAnyFreePort();
        } else {
            appiumServiceBuilder.usingPort(port);
        }
        service = AppiumDriverLocalService.buildService(appiumServiceBuilder
                .withArgument(GeneralServerFlag.LOG_LEVEL, "error")
                .withArgument(GeneralServerFlag.ALLOW_INSECURE, "chromedriver_autodownload"));
        service.start();
    }

    public static int getPort() {
        if (service != null && service.isRunning()) {
            return service.getUrl().getPort();
        } else {
            return 0;
        }
    }

    public static void close() {
        if (service != null && service.isRunning()) {
            service.stop();
        }
    }
}
