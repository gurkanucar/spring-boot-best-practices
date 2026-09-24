package com.gucardev.restapiintegration.client.error;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * The remote API answered with an error status. The two subclasses matter: a
 * {@link RemoteApiServerException} (5xx) may succeed if retried, a {@link RemoteApiClientException}
 * (4xx) never will, so retry and circuit breaker policies only react to the former.
 */
public abstract class RemoteApiException extends RuntimeException {

    private static final int MAX_BODY_CHARS = 500;

    private final int status;
    private final String responseBody;

    protected RemoteApiException(String message, int status, String responseBody) {
        super(message);
        this.status = status;
        this.responseBody = responseBody;
    }

    public static RemoteApiException from(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String body = readLimited(response.getBody());
        // The URL path only: a query string may contain values that should not end up in logs.
        String message = "%s %s failed with status %d".formatted(request.getMethod(), request.getURI().getPath(), status);
        return status >= 500
                ? new RemoteApiServerException(message, status, body)
                : new RemoteApiClientException(message, status, body);
    }

    private static String readLimited(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_BODY_CHARS * 4);
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.length() > MAX_BODY_CHARS ? text.substring(0, MAX_BODY_CHARS) : text;
    }

    public int getStatus() {
        return status;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
