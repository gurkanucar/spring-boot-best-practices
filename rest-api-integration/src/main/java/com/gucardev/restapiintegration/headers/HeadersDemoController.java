package com.gucardev.restapiintegration.headers;

import com.gucardev.restapiintegration.client.CorrelationIdInterceptor;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Calls the remote echo endpoint and returns what the remote side received, to show the three
 * sources of request headers: defaults of the client (Basic auth, X-Api-Key, User-Agent),
 * interceptors (X-Correlation-Id) and the individual request (everything set below).
 */
@RestController
@RequestMapping("/api/demo/headers")
public class HeadersDemoController {

    private final RestClient restClient;

    public HeadersDemoController(@Qualifier("remoteApi") RestClient restClient) {
        this.restClient = restClient;
    }

    @GetMapping
    public Map<String, Object> headers(
            @RequestParam(defaultValue = "false") boolean bearer,
            @RequestHeader(name = CorrelationIdInterceptor.HEADER, required = false) String correlationId) {
        return restClient.get()
                .uri("/echo?page={page}", 1)
                .header("X-Request-Source", "headers-demo")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR")
                .headers(headers -> {
                    // Propagate the incoming correlation id; the interceptor only creates one when absent.
                    if (correlationId != null) {
                        headers.set(CorrelationIdInterceptor.HEADER, correlationId);
                    }
                    // A per-request header replaces the client's default of the same name.
                    if (bearer) {
                        headers.setBearerAuth("demo-token");
                    }
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
