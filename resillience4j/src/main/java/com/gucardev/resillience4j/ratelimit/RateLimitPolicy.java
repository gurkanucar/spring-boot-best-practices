package com.gucardev.resillience4j.ratelimit;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * Decides WHO a request belongs to, and therefore which limits it counts against.
 *
 * <p>The naive key is the client IP. That breaks for shared networks: a whole school, office or
 * mobile carrier (CGNAT) reaches us through one public IP, so one busy student uses up the limit
 * of the whole campus. The fix is to key on identity whenever there is one:
 * <ol>
 *   <li>{@code X-API-Key}: the calling application, with the limit of its plan. The IP is ignored.
 *       (With Spring Security, a logged-in user's name works the same way.)</li>
 *   <li>Anonymous: the {@code anon_id} cookie identifies one browser, so classmates behind the
 *       same IP get separate small buckets. A cookie is free to throw away, though, so all
 *       anonymous traffic from one IP also shares a larger per-IP ceiling. That ceiling is higher
 *       for known shared networks ({@code app.rate-limit.shared-networks}).</li>
 * </ol>
 */
public class RateLimitPolicy {

    public static final String API_KEY_HEADER = "X-API-Key";
    static final String ANONYMOUS_COOKIE = "anon_id";

    private final RateLimitProperties properties;
    private final ClientIpResolver ipResolver;
    private final List<SharedNetworkRange> sharedNetworks;

    public RateLimitPolicy(RateLimitProperties properties, ClientIpResolver ipResolver) {
        this.properties = properties;
        this.ipResolver = ipResolver;
        this.sharedNetworks = properties.sharedNetworks().stream()
                .map(network -> new SharedNetworkRange(CidrRange.parse(network.cidr()), network))
                .toList();
    }

    /** The buckets this request is charged against, in order. May set the anonymous cookie on {@code response}. */
    public List<RateLimitBucket> bucketsFor(HttpServletRequest request, HttpServletResponse response) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null) {
            RateLimitProperties.ApiKey client = properties.apiKeys().get(apiKey);
            if (client == null) {
                throw new InvalidApiKeyException();
            }
            return List.of(new RateLimitBucket("client:" + client.clientId(), properties.plans().get(client.plan())));
        }
        String ip = ipResolver.resolve(request);
        return List.of(
                new RateLimitBucket("ip:" + ip, perIpLimit(ip)),
                new RateLimitBucket("anon:" + anonymousId(request, response), properties.anonymous().perClient()));
    }

    private RateLimitProperties.Limit perIpLimit(String ip) {
        return ClientIpResolver.parse(ip)
                .flatMap(address -> sharedNetworks.stream().filter(n -> n.range().contains(address)).findFirst())
                .map(n -> n.network().perIp())
                .orElse(properties.anonymous().perIp());
    }

    /** Reuses the browser's id, or issues a new one. Only a random id: it grants nothing, it only splits buckets. */
    private static String anonymousId(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> existing = Optional.ofNullable(request.getCookies()).stream()
                .flatMap(Arrays::stream)
                .filter(cookie -> ANONYMOUS_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(RateLimitPolicy::isUuid)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        String id = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from(ANONYMOUS_COOKIE, id)
                .httpOnly(true).sameSite("Lax").path("/").maxAge(Duration.ofDays(365)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return id;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return value.length() == 36;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private record SharedNetworkRange(CidrRange range, RateLimitProperties.SharedNetwork network) {
    }

    public static class InvalidApiKeyException extends RuntimeException {
        InvalidApiKeyException() {
            super("Unknown API key");
        }
    }
}
