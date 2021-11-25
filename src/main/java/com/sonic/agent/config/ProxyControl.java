package com.sonic.agent.config;

import com.google.common.collect.ImmutableMap;
import org.mitre.dsmiley.httpproxy.URITemplateProxyServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProxyControl {
//    @Bean
//    public ServletRegistrationBean proxyServletRegistration() {
//        ServletRegistrationBean registrationBean = new ServletRegistrationBean(new URITemplateProxyServlet(), "/iosScreen/*");
//        Map<String, String> params = ImmutableMap.of(
//                "targetUri", "http://localhost:{_port}",
//                "log", "false");
//        registrationBean.setInitParameters(params);
//        return registrationBean;
//    }
}
