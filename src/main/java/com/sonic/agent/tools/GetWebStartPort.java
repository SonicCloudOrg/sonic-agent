package com.sonic.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class GetWebStartPort implements ApplicationListener<WebServerInitializedEvent> {
    private final Logger logger = LoggerFactory.getLogger(GetWebStartPort.class);

    private static int serverPort = 0;

    public static int getTomcatPort(){
        return serverPort;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        serverPort = event.getWebServer().getPort();
        logger.info("tomcat开启端口： {}", serverPort);
    }
}