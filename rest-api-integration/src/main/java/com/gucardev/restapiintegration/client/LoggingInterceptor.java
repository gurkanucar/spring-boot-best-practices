package com.gucardev.restapiintegration.client;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Logs method, path, status and duration of every call. Deliberately NOT the query string or the
 * headers (they may carry search terms or credentials), and not the bodies (they may carry
 * personal data, and reading the response body here would consume the stream the caller needs).
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getURI().getRawPath(),
                    response.getStatusCode().value(), (System.nanoTime() - start) / 1_000_000);
            return response;
        } catch (IOException e) {
            log.warn("{} {} -> {} ({} ms)", request.getMethod(), request.getURI().getRawPath(),
                    e.getClass().getSimpleName(), (System.nanoTime() - start) / 1_000_000);
            throw e;
        }
    }
}
