package com.gucardev.resillience4j.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

/**
 * Finds the real client IP behind load balancers, without trusting what the client says.
 *
 * <p>{@code X-Forwarded-For} is a plain header: anyone can send {@code X-Forwarded-For: 1.2.3.4}
 * and get a fresh rate limit bucket with every request. So the header is only read when the
 * direct peer is one of our own proxies, and it is read from the RIGHT: each proxy appends the
 * address it saw, so the right-most entries were written by our proxies, and the first entry
 * that is not one of ours is the client. Everything left of it was written by the client itself.
 *
 * <p>Spring Boot can do this for you with {@code server.forward-headers-strategy=native} plus
 * Tomcat's {@code server.tomcat.remoteip.internal-proxies}; this class shows what that does.
 */
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final List<CidrRange> trustedProxies;

    public ClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream().map(CidrRange::parse).toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrustedProxy(peer)) {
            return peer;
        }
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return peer;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (parse(hop).isEmpty()) {
                return peer; // garbage in the header: do not guess, use the address we can see
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return hops[0].trim(); // every hop is one of our proxies
    }

    private boolean isTrustedProxy(String ip) {
        return parse(ip).map(address -> trustedProxies.stream().anyMatch(range -> range.contains(address))).orElse(false);
    }

    /** Only IP literals. {@code InetAddress.getByName} would do a DNS lookup for a host name. */
    static Optional<InetAddress> parse(String ip) {
        try {
            return Optional.of(InetAddress.ofLiteral(ip));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
