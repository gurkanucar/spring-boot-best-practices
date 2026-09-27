package com.gucardev.logtobasicsecurity.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Checks that a webhook request really comes from Logto: Logto signs the raw request body with the
 * webhook's signing key (HMAC-SHA256, hex) and sends it in {@code logto-signature-sha-256}. Anyone can
 * send a POST to the endpoint; only Logto knows the key.
 */
@Component
public class LogtoWebhookSignature {

    public static final String HEADER = "logto-signature-sha-256";

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] signingKey;

    /** @param signingKey from the webhook's page in the Logto console; empty rejects every request */
    public LogtoWebhookSignature(@Value("${app.logto.webhook-signing-key:}") String signingKey) {
        // strip(): a key pasted with a trailing newline or \r (a .env saved on Windows) would silently
        // fail every signature. Logto's keys contain no whitespace.
        this.signingKey = signingKey.strip().getBytes(StandardCharsets.UTF_8);
    }

    public boolean isConfigured() {
        return signingKey.length > 0;
    }

    /**
     * @param body      the raw bytes as received: parsing and re-serialising the JSON would change them
     *                  (spacing, key order) and the signature would no longer match
     * @param signature the header value
     */
    public boolean isValid(byte[] body, String signature) {
        if (!isConfigured() || signature == null) {
            return false;
        }
        byte[] expected = HexFormat.of().formatHex(hmac(body)).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        // Constant time: equals() stops at the first difference, which leaks how much of a guess was right.
        return MessageDigest.isEqual(expected, actual);
    }

    byte[] hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, ALGORITHM));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
