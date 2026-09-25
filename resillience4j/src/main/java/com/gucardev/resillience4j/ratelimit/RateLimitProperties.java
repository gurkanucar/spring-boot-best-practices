package com.gucardev.resillience4j.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.rate-limit.*}: limits for requests coming INTO our API. */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Store store,
        List<String> trustedProxies,
        Map<String, ApiKey> apiKeys,
        Map<String, Limit> plans,
        Anonymous anonymous,
        List<SharedNetwork> sharedNetworks) {

    public RateLimitProperties {
        store = store == null ? Store.IN_MEMORY : store;
        trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
        apiKeys = apiKeys == null ? Map.of() : apiKeys;
        plans = plans == null ? Map.of() : plans;
        sharedNetworks = sharedNetworks == null ? List.of() : sharedNetworks;
    }

    public enum Store { IN_MEMORY, REDIS }

    /** {@code limit} requests per {@code period}. */
    public record Limit(int limit, Duration period) {
    }

    public record ApiKey(String clientId, String plan) {
    }

    /**
     * @param perClient one anonymous browser, recognised by its {@code anon_id} cookie
     * @param perIp     all anonymous traffic from one IP together; a ceiling against cookie-dropping clients
     */
    public record Anonymous(Limit perClient, Limit perIp) {
    }

    /** A network where many people share one public IP; its per-IP ceiling is higher. */
    public record SharedNetwork(String name, String cidr, Limit perIp) {
    }
}
