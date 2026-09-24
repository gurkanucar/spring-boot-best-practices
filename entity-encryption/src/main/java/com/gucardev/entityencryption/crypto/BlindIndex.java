package com.gucardev.entityencryption.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Deterministic keyed hash (HMAC-SHA256) used to look rows up by an encrypted value.
 *
 * <p>Encryption with a random IV is deliberately non-deterministic, so {@code where email = ?}
 * can never match. A blind index stores a second, searchable column: equal inputs give equal
 * hashes, and without the key the hash cannot be brute-forced from a stolen database.
 * The input is normalized first so "Ann@Mail.com " and "ann@mail.com" are the same lookup.
 */
@Component
public class BlindIndex {

    private final SecretKeySpec key;

    public BlindIndex(EncryptionProperties properties) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.blindIndexKey()), "HmacSHA256");
    }

    public String of(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Blind index computation failed", e);
        }
    }
}
