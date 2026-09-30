package com.gucardev.jwtauthrefreshtokenroles.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sha256TokenHasherTest {

    private final TokenHasher hasher = new Sha256TokenHasher();

    @Test
    void producesLowercaseHexSha256() {
        // NIST test vector for "abc".
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void isDeterministic() {
        assertThat(hasher.hash("token")).isEqualTo(hasher.hash("token"));
    }
}
