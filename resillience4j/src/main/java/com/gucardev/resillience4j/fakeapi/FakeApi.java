package com.gucardev.resillience4j.fakeapi;

/** The simulated third-party services. Each one is used to demonstrate one pattern. */
public enum FakeApi {
    RATES,           // retry
    RECOMMENDATIONS, // circuit breaker
    SHIPPING,        // time limiter
    INVOICES,        // bulkhead
    SMS,             // outbound rate limiter
    PAYMENTS         // retry + circuit breaker together
}
