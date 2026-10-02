package com.gucardev.openobserve.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OTEL appender declared in logback-spring.xml starts out detached; events logged before
 * this runs are buffered, then flushed to the SDK once it is installed.
 */
@Configuration(proxyBeanMethods = false)
public class OpenTelemetryLogAppenderConfig {

    @Bean
    InitializingBean openTelemetryAppenderInstaller(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }
}
