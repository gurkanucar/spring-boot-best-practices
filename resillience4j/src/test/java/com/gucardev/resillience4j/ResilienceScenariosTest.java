package com.gucardev.resillience4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.resillience4j.fakeapi.FakeApi;
import com.gucardev.resillience4j.fakeapi.FakeApiRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Every pattern end to end: our API -> Resilience4j -> real HTTP -> fake third-party API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.rate-limit.enabled=false") // inbound limiting has its own test
class ResilienceScenariosTest {

    @LocalServerPort
    int port;
    @Autowired
    FakeApiRegistry fakeApis;
    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }) // assert on status instead
                .build();
        fakeApis.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void retryHidesShortOutages() {
        fakeApis.state(FakeApi.RATES).configure(2, null, null);

        ResponseEntity<Map> response = get("/api/rates/USD/EUR");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(hits(FakeApi.RATES)).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    void retryGivesUpAfterMaxAttempts() {
        fakeApis.state(FakeApi.RATES).configure(null, true, null);

        assertThat(get("/api/rates/USD/EUR").getStatusCode().value()).isEqualTo(502);
        assertThat(hits(FakeApi.RATES)).isEqualTo(4);
    }

    @Test
    void clientErrorsAreNotRetried() {
        assertThat(get("/api/rates/USD/XXX").getStatusCode().value()).isEqualTo(502);
        assertThat(hits(FakeApi.RATES)).isEqualTo(1);
    }

    @Test
    void openCircuitServesFallbackWithoutCallingTheRemote() {
        fakeApis.state(FakeApi.RECOMMENDATIONS).configure(null, true, null);

        for (int i = 0; i < 5; i++) {
            assertThat(get("/api/products/1/recommendations").getBody()).containsEntry("source", "fallback-bestsellers");
        }
        assertThat(circuitBreakers.circuitBreaker("recommendations").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        for (int i = 0; i < 3; i++) {
            assertThat(get("/api/products/1/recommendations").getBody())
                    .containsEntry("fallbackReason", "circuit open, recommendation engine not called");
        }
        assertThat(hits(FakeApi.RECOMMENDATIONS)).isEqualTo(5);
    }

    @Test
    void timeLimiterAnswersWithFlatRateWhenCourierIsSlow() {
        fakeApis.state(FakeApi.SHIPPING).configure(null, null, 3000L);

        long start = System.currentTimeMillis();
        ResponseEntity<Map> response = get("/api/shipping/quote?city=Izmir");

        assertThat(System.currentTimeMillis() - start).isLessThan(2500);
        assertThat(response.getBody()).containsEntry("source", "flat-rate-fallback");
    }

    @Test
    void bulkheadRejectsCallsBeyondTheConcurrencyLimit() throws Exception {
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                String orderId = "order-" + i;
                statuses.add(executor.submit(() -> http.post().uri("/api/orders/{id}/invoice", orderId)
                        .retrieve().toBodilessEntity().getStatusCode().value()));
            }
        }
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> status : statuses) {
            codes.add(status.get());
        }

        assertThat(codes).containsOnly(200, 503);
        assertThat(codes.stream().filter(c -> c == 200).count()).isEqualTo(2);
        assertThat(hits(FakeApi.INVOICES)).isEqualTo(2);
    }

    @Test
    void outboundRateLimiterStaysUnderTheProviderQuota() throws Exception {
        Thread.sleep(1100); // start in a fresh provider window, whatever other tests sent

        Map<String, Object> result = post("/api/sms/campaign?messages=15&throttle=true").getBody();

        assertThat(result).containsEntry("sent", 15).containsEntry("rejectedByProvider429", 0);
        assertThat(((Number) result.get("elapsedMs")).longValue()).isGreaterThanOrEqualTo(2000);
    }

    @Test
    void withoutRateLimiterABurstIsRejectedByTheProvider() throws Exception {
        Thread.sleep(1100);

        Map<String, Object> result = post("/api/sms/campaign?messages=30&throttle=false").getBody();

        assertThat(((Number) result.get("rejectedByProvider429")).intValue()).isPositive();
    }

    @Test
    void retriedPaymentIsChargedOnlyOnce() {
        fakeApis.state(FakeApi.PAYMENTS).configure(2, null, null);

        ResponseEntity<Map> response = http.post().uri("/api/payments")
                .body(Map.of("amount", 49.90, "currency", "EUR")).retrieve().toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(hits(FakeApi.PAYMENTS)).isEqualTo(3);
        assertThat(fakeApis.charges()).hasSize(1);
    }

    @Test
    void openCircuitIsNotRetried() {
        fakeApis.state(FakeApi.PAYMENTS).configure(null, true, null);

        // Request 1: 3 attempts, 3 failed calls, the retry gives up -> 502.
        assertThat(post("/api/payments", Map.of("amount", 10, "currency", "EUR")).getStatusCode().value()).isEqualTo(502);
        // Request 2: attempts 4 and 5 fail, 5 calls is enough to open the circuit. Attempt 6 is
        // refused by the open circuit, and the retry stops there instead of waiting and trying again.
        assertThat(post("/api/payments", Map.of("amount", 10, "currency", "EUR")).getStatusCode().value()).isEqualTo(503);
        assertThat(circuitBreakers.circuitBreaker("payments").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(hits(FakeApi.PAYMENTS)).isEqualTo(5);

        ResponseEntity<Map> rejected = post("/api/payments", Map.of("amount", 10, "currency", "EUR"));

        assertThat(rejected.getStatusCode().value()).isEqualTo(503);
        assertThat(rejected.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(hits(FakeApi.PAYMENTS)).isEqualTo(5); // the gateway was not called at all
    }

    private ResponseEntity<Map> get(String uri) {
        return http.get().uri(uri).retrieve().toEntity(Map.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity<Map> post(String uri) {
        return http.post().uri(uri).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> post(String uri, Object body) {
        return http.post().uri(uri).body(body).retrieve().toEntity(Map.class);
    }

    private int hits(FakeApi api) {
        return fakeApis.state(api).snapshot().hits();
    }
}
