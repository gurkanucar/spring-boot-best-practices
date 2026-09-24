package com.gucardev.restapiintegration.remote;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RemoteConfig {

    @Bean
    FilterRegistrationBean<RemoteAuthFilter> remoteAuthFilter(MockRemoteProperties properties) {
        var registration = new FilterRegistrationBean<>(new RemoteAuthFilter(properties));
        registration.addUrlPatterns("/remote-api/products", "/remote-api/products/*");
        return registration;
    }
}
