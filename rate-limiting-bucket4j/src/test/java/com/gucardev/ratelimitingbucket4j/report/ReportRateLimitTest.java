package com.gucardev.ratelimitingbucket4j.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitPolicy;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * {@code report-per-email}: 3 reports per hour per e-mail, charged by {@link ReportService}, on top of
 * the filter's limits. Calls use the pro API key (200/min) so that only the e-mail limit is hit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportRateLimitTest {

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
    void eachEmailGetsThreeReportsPerHour() {
        for (int i = 0; i < 3; i++) {
            assertThat(report("alice@example.com").getStatusCode().value()).isEqualTo(200);
        }
        ResponseEntity<Map> rejected = report("alice@example.com");
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(rejected.getBody()).containsEntry("bucket", "report-per-email:alice@example.com");
        assertThat(rejected.getHeaders().getFirst("RateLimit-Policy")).isEqualTo("3;w=3600");
        // One token every 20 minutes.
        assertThat(Long.parseLong(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1100L, 1200L);

        // Same API key, same IP, another e-mail: its own bucket.
        assertThat(report("bob@example.com").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void emailIsNormalisedSoCaseAndSpacesDoNotGiveAFreshBucket() {
        for (int i = 0; i < 3; i++) {
            report("carol@example.com");
        }
        assertThat(report("  Carol@Example.COM ").getStatusCode().value()).isEqualTo(429);
    }

    private ResponseEntity<Map> report(String email) {
        return http.post().uri("/api/reports/monthly")
                .header(RateLimitPolicy.API_KEY_HEADER, "demo-pro-key")
                .header(ReportController.EMAIL_HEADER, email)
                .retrieve().toEntity(Map.class);
    }
}
