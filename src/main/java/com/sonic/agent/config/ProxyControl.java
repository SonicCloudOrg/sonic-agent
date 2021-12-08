package com.sonic.agent.config;

import com.google.common.collect.ImmutableMap;
import org.mitre.dsmiley.httpproxy.URITemplateProxyServlet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//import org.springframework.stereotype.Component;
//
//import java.util.Map;
//
//@Configuration
//@ConditionalOnProperty(value = "modules.ios.enable", havingValue = "true")
//public class ProxyControl {
//    //bug
//    @Bean
//    public ServletRegistrationBean proxyServletRegistration() {
//        ServletRegistrationBean registrationBean = new ServletRegistrationBean(new URITemplateProxyServlet(), "/iosScreen/*");
//        Map<String, String> params = ImmutableMap.of(
//                "targetUri", "http://localhost:{s_id}",
//                "log", "false");
//        registrationBean.setInitParameters(params);
//        return registrationBean;
//    }
//}
