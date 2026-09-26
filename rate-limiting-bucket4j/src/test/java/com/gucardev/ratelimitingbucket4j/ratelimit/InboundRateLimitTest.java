package com.gucardev.ratelimitingbucket4j.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Limits from application.yaml: free plan 20/min and 5/s, pro 200/min and 20/s, anonymous browser
 * 5/min, anonymous per IP 30/min, campus network (10.20.0.0/16) 300/min, export costs 5 tokens.
 * The test client connects from localhost, a trusted proxy, so {@code X-Forwarded-For} simulates
 * clients at different IPs. Counters are shared by the whole context, so every test uses its own
 * IPs and API keys (extra free-plan keys below).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.rate-limit.api-keys.free-key-a.client-id=client-a", "app.rate-limit.api-keys.free-key-a.plan=free",
        "app.rate-limit.api-keys.free-key-b.client-id=client-b", "app.rate-limit.api-keys.free-key-b.plan=free",
        "app.rate-limit.api-keys.free-key-c.client-id=client-c", "app.rate-limit.api-keys.free-key-c.plan=free"
})
class InboundRateLimitTest {

    @LocalServerPort
    int port;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void apiClientsHaveTheirOwnQuotaWhateverTheirIp() {
        for (int i = 0; i < 5; i++) { // the free plan allows a burst of 5 per second
            assertThat(call("198.51.100.1", null, "free-key-a").getStatusCode().value()).isEqualTo(200);
        }
        ResponseEntity<Map> rejected = call("198.51.100.1", null, "free-key-a");
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(rejected.getBody()).containsEntry("bucket", "client:client-a");

        // Another application behind the very same IP is not affected.
        assertThat(call("198.51.100.1", null, "free-key-b").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void tokensComeBackGraduallyNotAtTheEndOfAWindow() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            call("198.51.100.6", null, "free-key-c");
        }
        assertThat(call("198.51.100.6", null, "free-key-c").getStatusCode().value()).isEqualTo(429);

        Thread.sleep(250); // 5 per second = one token every 200 ms
        assertThat(call("198.51.100.6", null, "free-key-c").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void expensiveEndpointCostsMoreTokens() {
        // Export costs 5 tokens: one anonymous browser (5/min) can export once, then nothing is left.
        String browser = anonymousCookie("198.51.100.7"); // 1 token
        assertThat(exportAs("198.51.100.7", browser).getStatusCode().value()).isEqualTo(429); // needs 5, has 4

        String other = "anon_id=" + UUID.randomUUID();
        ResponseEntity<Map> export = exportAs("198.51.100.7", other);
        assertThat(export.getStatusCode().value()).isEqualTo(200);
        assertThat(export.getHeaders().getFirst("RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void browsersBehindOneIpGetSeparateBuckets() {
        String alice = anonymousCookie("198.51.100.2");
        String bob = anonymousCookie("198.51.100.2");

        for (int i = 0; i < 4; i++) { // the cookie request above was number 1
            assertThat(call("198.51.100.2", alice, null).getStatusCode().value()).isEqualTo(200);
        }
        ResponseEntity<Map> rejected = call("198.51.100.2", alice, null);
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat((String) rejected.getBody().get("bucket")).startsWith("anon:");

        // Bob shares Alice's IP, but not her bucket.
        assertThat(call("198.51.100.2", bob, null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void droppingTheCookieDoesNotEscapeThePerIpCeiling() {
        for (int i = 0; i < 30; i++) {
            assertThat(call("198.51.100.3", null, null).getStatusCode().value()).isEqualTo(200);
        }
        ResponseEntity<Map> rejected = call("198.51.100.3", null, null);
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(rejected.getBody()).containsEntry("bucket", "ip:198.51.100.3");
    }

    @Test
    void sharedNetworkHasAHigherCeiling() {
        for (int i = 0; i < 60; i++) { // twice the normal per-IP ceiling, each a different browser
            assertThat(call("10.20.4.4", null, null).getStatusCode().value()).isEqualTo(200);
        }
    }

    @Test
    void forgedLeftMostForwardedForIsIgnored() {
        ResponseEntity<Map> response = call("6.6.6.6, 198.51.100.8", null, null);

        assertThat(response.getHeaders().getFirst("X-RateLimit-Bucket")).isNotNull();
        assertThat(response.getBody().get("buckets").toString()).contains("ip:198.51.100.8").doesNotContain("6.6.6.6");
    }

    @Test
    void unknownApiKeyIsRejected() {
        assertThat(call("198.51.100.4", null, "made-up").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void responsesTellClientsHowMuchIsLeft() {
        ResponseEntity<Map> response = call("198.51.100.5", null, "demo-pro-key");

        // Pro: 200/min and 20/s. After one request the per-second limit is the tightest: 19 left.
        assertThat(response.getHeaders().getFirst("RateLimit-Limit")).isEqualTo("20");
        assertThat(response.getHeaders().getFirst("RateLimit-Remaining")).isEqualTo("19");
        assertThat(response.getHeaders().getFirst("RateLimit-Policy")).isEqualTo("200;w=60, 20;w=1");
    }

    /** Makes a first request as a new browser and returns the cookie the server issued. */
    private String anonymousCookie(String ip) {
        String setCookie = call(ip, null, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("anon_id=");
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private ResponseEntity<Map> call(String forwardedFor, String cookie, String apiKey) {
        return request(HttpMethod.GET, "/api/whoami", forwardedFor, cookie, apiKey);
    }

    private ResponseEntity<Map> exportAs(String forwardedFor, String cookie) {
        return request(HttpMethod.POST, "/api/reports/export", forwardedFor, cookie, null);
    }

    private ResponseEntity<Map> request(HttpMethod method, String uri, String forwardedFor, String cookie, String apiKey) {
        return http.method(method).uri(uri)
                .headers(headers -> {
                    headers.set("X-Forwarded-For", forwardedFor);
                    if (cookie != null) {
                        headers.set(HttpHeaders.COOKIE, cookie);
                    }
                    if (apiKey != null) {
                        headers.set(RateLimitPolicy.API_KEY_HEADER, apiKey);
                    }
                })
                .retrieve().toEntity(Map.class);
    }
}
