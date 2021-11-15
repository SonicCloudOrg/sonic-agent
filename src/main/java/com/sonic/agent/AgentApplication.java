package com.sonic.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;

/**
 * @author ZhouYiXun
 * @des Agent端启动类
 * @date 2021/08/16 19:26
 */
@SpringBootApplication
public class AgentApplication {
    @Value("${sonic.agent.port}")
    private int port;

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

    @Bean
    public TomcatServletWebServerFactory servletContainer(){
        return new TomcatServletWebServerFactory(port) ;
    }
}
