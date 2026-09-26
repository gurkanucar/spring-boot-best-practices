package com.gucardev.ratelimitingbucket4j.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.rate-limit.*}: limits for requests coming INTO our API.
 *
 * <p>Every limit is a list: a request needs a token from each of them, e.g. "20 per minute and
 * 5 per second" allows short bursts but not a whole minute's quota in one second.
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Store store,
        List<String> trustedProxies,
        Map<String, ApiKey> apiKeys,
        Map<String, List<Limit>> plans,
        Anonymous anonymous,
        List<SharedNetwork> sharedNetworks,
        Map<String, Integer> costs) {

    public RateLimitProperties {
        store = store == null ? Store.IN_MEMORY : store;
        trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
        apiKeys = apiKeys == null ? Map.of() : apiKeys;
        plans = plans == null ? Map.of() : plans;
        sharedNetworks = sharedNetworks == null ? List.of() : sharedNetworks;
        costs = costs == null ? Map.of() : costs;
    }

    public enum Store { IN_MEMORY, REDIS }

    /** A bucket of {@code capacity} tokens, refilled evenly over {@code period} (a token bucket). */
    public record Limit(int capacity, Duration period) {
    }

    public record ApiKey(String clientId, String plan) {
    }

    /**
     * @param perClient one anonymous browser, recognised by its {@code anon_id} cookie
     * @param perIp     all anonymous traffic from one IP together; a ceiling against cookie-dropping clients
     */
    public record Anonymous(List<Limit> perClient, List<Limit> perIp) {
    }

    /** A network where many people share one public IP; its per-IP ceiling is higher. */
    public record SharedNetwork(String name, String cidr, List<Limit> perIp) {
    }
}
