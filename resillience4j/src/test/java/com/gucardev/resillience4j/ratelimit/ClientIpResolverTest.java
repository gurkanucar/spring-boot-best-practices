package com.gucardev.resillience4j.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    // Our load balancers live in 10.0.0.0/24.
    private final ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/24"));

    @Test
    void directClientIsIdentifiedByItsAddress() {
        assertThat(resolver.resolve(request("198.51.100.7", null))).isEqualTo("198.51.100.7");
    }

    @Test
    void forwardedForFromAnUntrustedPeerIsIgnored() {
        // A client talking to us directly cannot pick its own bucket by sending the header.
        assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
    }

    @Test
    void behindOurProxyTheForwardedClientIsUsed() {
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void entriesTheClientPrependedAreIgnored() {
        // The client sent "X-Forwarded-For: 6.6.6.6"; our proxy appended the address it really saw.
        assertThat(resolver.resolve(request("10.0.0.5", "6.6.6.6, 203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void chainedTrustedProxiesAreSkipped() {
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 10.0.0.8"))).isEqualTo("203.0.113.9");
    }

    @Test
    void garbageInTheHeaderFallsBackToThePeer() {
        assertThat(resolver.resolve(request("10.0.0.5", "not-an-ip"))).isEqualTo("10.0.0.5");
    }

    @Test
    void cidrMatchingWorksForIpv4AndIpv6() {
        CidrRange campus = CidrRange.parse("10.20.0.0/16");
        assertThat(campus.contains(InetAddress.ofLiteral("10.20.255.1"))).isTrue();
        assertThat(campus.contains(InetAddress.ofLiteral("10.21.0.1"))).isFalse();
        assertThat(campus.contains(InetAddress.ofLiteral("::1"))).isFalse();

        CidrRange v6 = CidrRange.parse("2001:db8::/33");
        assertThat(v6.contains(InetAddress.ofLiteral("2001:db8:7fff::1"))).isTrue();
        assertThat(v6.contains(InetAddress.ofLiteral("2001:db8:8000::1"))).isFalse();
    }

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
