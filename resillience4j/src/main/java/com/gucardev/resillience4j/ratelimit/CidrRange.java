package com.gucardev.resillience4j.ratelimit;

import java.net.InetAddress;

/** An IPv4 or IPv6 range such as {@code 10.20.0.0/16} or {@code ::1/128}. A plain address means that one address. */
final class CidrRange {

    private final String text;
    private final byte[] network;
    private final int prefixLength;

    private CidrRange(String text, byte[] network, int prefixLength) {
        this.text = text;
        this.network = network;
        this.prefixLength = prefixLength;
    }

    static CidrRange parse(String cidr) {
        String[] parts = cidr.trim().split("/");
        byte[] address = InetAddress.ofLiteral(parts[0]).getAddress();
        int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : address.length * 8;
        if (prefix < 0 || prefix > address.length * 8) {
            throw new IllegalArgumentException("Invalid prefix length in " + cidr);
        }
        return new CidrRange(cidr, address, prefix);
    }

    boolean contains(InetAddress address) {
        byte[] candidate = address.getAddress();
        if (candidate.length != network.length) {
            return false; // IPv4 vs IPv6
        }
        int fullBytes = prefixLength / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != network[i]) {
                return false;
            }
        }
        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }

    @Override
    public String toString() {
        return text;
    }
}
