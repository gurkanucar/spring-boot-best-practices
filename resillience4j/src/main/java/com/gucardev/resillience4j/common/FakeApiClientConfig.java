package com.gucardev.resillience4j.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

@Configuration
class FakeApiClientConfig {

    /**
     * One client for all fake APIs. Timeouts come from {@code spring.http.clients.*}.
     *
     * <p>{@code @Lazy} only because the fake APIs run inside this same app: the base URL contains
     * {@code local.server.port}, which is known once the web server has started. Clients inject it
     * with {@code @Lazy} too, so it is created on the first call. A real remote URL needs none of this.
     */
    @Bean
    @Lazy
    RestClient fakeApiRestClient(RestClient.Builder builder, @Value("${fake-api.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    /** Runs the calls that a time limiter watches. Virtual threads: blocking I/O is cheap. */
    @Bean(destroyMethod = "close")
    ExecutorService remoteCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
