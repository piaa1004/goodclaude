package com.example.board.global.config;

import com.example.board.global.error.ProblemDetailErrorReportValve;
import org.apache.catalina.core.StandardHost;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat Host의 기본 ErrorReportValve(HTML)를 ProblemDetail 응답 Valve로 교체한다.
 */
@Configuration(proxyBeanMethods = false)
public class TomcatErrorReportConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> problemDetailErrorReportValveCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(ProblemDetailErrorReportValve.class.getName());
            }
        });
    }
}
