package com.gucardev.restapiintegration.client;

import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Adds an {@code X-Correlation-Id} to every outgoing request (unless the caller already set one)
 * so a single request can be traced across services in the logs.
 */
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsHeader(HEADER)) {
            request.getHeaders().set(HEADER, UUID.randomUUID().toString());
        }
        return execution.execute(request, body);
    }
}
