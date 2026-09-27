package com.gucardev.logtobasicsecurity.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LogtoWebhookSignatureTest {

    private static final byte[] BODY = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);

    /** HMAC-SHA256 with key "key" of the sentence above: the well-known test vector. */
    private static final String SIGNATURE = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";

    private final LogtoWebhookSignature signature = new LogtoWebhookSignature("key");

    @Test
    void acceptsTheHmacOfTheBody() {
        assertThat(signature.isValid(BODY, SIGNATURE)).isTrue();
        assertThat(signature.isValid(BODY, SIGNATURE.toUpperCase())).isTrue();
    }

    @Test
    void rejectsAChangedBodyOrAWrongSignature() {
        byte[] changed = "The quick brown fox jumps over the lazy cat".getBytes(StandardCharsets.UTF_8);

        assertThat(signature.isValid(changed, SIGNATURE)).isFalse();
        assertThat(signature.isValid(BODY, "0".repeat(64))).isFalse();
        assertThat(signature.isValid(BODY, null)).isFalse();
    }

    @Test
    void whitespaceAroundTheKeyIsIgnored() {
        // e.g. LOGTO_WEBHOOK_SIGNING_KEY=key\r from a .env file saved on Windows
        assertThat(new LogtoWebhookSignature("key\r\n").isValid(BODY, SIGNATURE)).isTrue();
    }

    @Test
    void withoutASigningKeyNothingIsAccepted() {
        var notConfigured = new LogtoWebhookSignature("");

        assertThat(notConfigured.isConfigured()).isFalse();
        assertThat(notConfigured.isValid(BODY, SIGNATURE)).isFalse();
    }
}
