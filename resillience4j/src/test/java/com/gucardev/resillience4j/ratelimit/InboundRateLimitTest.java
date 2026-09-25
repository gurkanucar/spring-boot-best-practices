package com.gucardev.resillience4j.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Limits from application.yaml: free plan 20/min, anonymous browser 5/min, anonymous per IP
 * 30/min, campus network (10.20.0.0/16) 300/min. The test client connects from localhost, a
 * trusted proxy, so {@code X-Forwarded-For} simulates clients at different IPs.
 * Each test uses its own IPs and keys, because the counters are shared by the whole context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
        for (int i = 0; i < 20; i++) {
            assertThat(call("198.51.100.1", null, "demo-free-key").getStatusCode().value()).isEqualTo(200);
        }
        ResponseEntity<Map> rejected = call("198.51.100.1", null, "demo-free-key");
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(rejected.getBody()).containsEntry("bucket", "client:acme-mobile");

        // Another application behind the very same IP is not affected.
        assertThat(call("198.51.100.1", null, "demo-pro-key").getStatusCode().value()).isEqualTo(200);
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
    void unknownApiKeyIsRejected() {
        assertThat(call("198.51.100.4", null, "made-up").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void responsesTellClientsHowMuchIsLeft() {
        ResponseEntity<Map> response = call("198.51.100.5", null, "demo-pro-key");
        assertThat(response.getHeaders().getFirst("RateLimit-Limit")).isEqualTo("200");
        assertThat(response.getHeaders().getFirst("RateLimit-Remaining")).isEqualTo("199");
    }

    /** Makes a first request as a new browser and returns the cookie the server issued. */
    private String anonymousCookie(String ip) {
        String setCookie = call(ip, null, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("anon_id=");
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private ResponseEntity<Map> call(String clientIp, String cookie, String apiKey) {
        return http.get().uri("/api/whoami")
                .headers(headers -> {
                    headers.set("X-Forwarded-For", clientIp);
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
