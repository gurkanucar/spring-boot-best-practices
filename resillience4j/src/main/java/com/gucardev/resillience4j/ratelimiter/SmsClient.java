package com.gucardev.resillience4j.ratelimiter;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OUTBOUND RATE LIMITER: the SMS gateway allows 10 requests per second and answers 429 above
 * that. Instead of hitting the limit and handling 429s, we never send faster than the gateway
 * allows: {@code smsProvider} hands out one permit every 200 ms, and a caller without a permit
 * waits (up to {@code timeout-duration}) for the next one.
 *
 * <p>Keep headroom below the provider's limit: our time windows and theirs are not aligned,
 * and network delays bunch requests together.
 *
 * <p>This limiter is per application instance. With 3 instances the provider sees 3x the rate;
 * divide the limit by the number of instances, or use a shared limiter (see the ratelimit package).
 */
@Component
public class SmsClient {

    private final RestClient fakeApi;

    public SmsClient(@Lazy RestClient fakeApi) {
        this.fakeApi = fakeApi;
    }

    @RateLimiter(name = "smsProvider")
    public String send(String to, String text) {
        return sendUnthrottled(to, text);
    }

    /** The same call without the limiter, to compare. */
    public String sendUnthrottled(String to, String text) {
        Map<?, ?> response = fakeApi.post().uri("/sms").body(Map.of("to", to, "text", text)).retrieve().body(Map.class);
        return (String) response.get("messageId");
    }
}
