package org.cloud.sonic.agent;

import org.apache.dubbo.config.spring.context.annotation.DubboComponentScan;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.common.config.APIDocumentConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * @author ZhouYiXun
 * @des Agent端启动类
 * @date 2021/08/16 19:26
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"org.cloud.sonic.agent", "org.cloud.sonic.common"},
        excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = APIDocumentConfig.class)}
)
@EnableFeignClients(basePackages = {"org.cloud.sonic.common.feign"})
@EnableDiscoveryClient
@DubboComponentScan(basePackages = "org.cloud.sonic.agent.service.impl")
public class AgentApplication {
    @Value("${sonic.agent.port}")
    private int port;

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(port);
        factory.addConnectorCustomizers((TomcatConnectorCustomizer) connector -> connector.setProperty("relaxedQueryChars", "|{}[]\\"));
        return factory;
    }
}
