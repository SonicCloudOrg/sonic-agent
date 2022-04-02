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
