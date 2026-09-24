package com.gucardev.entityencryption.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

    private static final String K1 = randomKey();
    private static final String K2 = randomKey();
    private static final String INDEX_KEY = randomKey();

    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static EncryptionProperties props(String active, Map<String, String> keys) {
        return new EncryptionProperties(active, keys, INDEX_KEY);
    }

    private final AesGcmCipher cipher = new AesGcmCipher(props("k1", Map.of("k1", K1)));

    @Test
    void roundTripsIncludingUnicodeAndEmptyStrings() {
        for (String value : new String[]{"12345678901", "", "Gürkan Uçar", "日本語 🔐"}) {
            assertThat(cipher.decrypt(cipher.encrypt(value))).isEqualTo(value);
        }
    }

    @Test
    void storedValueHidesThePlaintextAndCarriesTheKeyId() {
        String stored = cipher.encrypt("12345678901");

        assertThat(stored).startsWith("v1:k1:").doesNotContain("12345678901");
    }

    @Test
    void sameValueEncryptsDifferentlyEveryTime() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void tamperedCiphertextIsRejectedInsteadOfDecryptedToGarbage() {
        String stored = cipher.encrypt("secret");
        // Flip a character inside the payload (not the last one: its low bits are base64 padding).
        int i = stored.length() - 5;
        String tampered = stored.substring(0, i) + (stored.charAt(i) == 'A' ? 'B' : 'A') + stored.substring(i + 1);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Decryption failed")
                .hasMessageNotContaining("secret");
    }

    @Test
    void valueEncryptedWithAnotherKeyCannotBeRead() {
        AesGcmCipher other = new AesGcmCipher(props("k1", Map.of("k1", K2)));

        assertThatThrownBy(() -> cipher.decrypt(other.encrypt("secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void oldKeysStillReadWhileNewValuesUseTheActiveKey() {
        String oldValue = cipher.encrypt("legacy");
        AesGcmCipher rotated = new AesGcmCipher(props("k2", Map.of("k1", K1, "k2", K2)));

        assertThat(rotated.decrypt(oldValue)).isEqualTo("legacy");
        assertThat(rotated.encrypt("fresh")).startsWith("v1:k2:");
    }

    @Test
    void unknownKeyIdAndForeignFormatsAreRejected() {
        assertThatThrownBy(() -> cipher.decrypt("v1:zzz:AAAA")).hasMessageContaining("No key configured");
        assertThatThrownBy(() -> cipher.decrypt("plain text")).hasMessageContaining("expected encrypted format");
    }

    @Test
    void misconfigurationFailsAtStartup() {
        assertThatThrownBy(() -> new AesGcmCipher(props("missing", Map.of("k1", K1))))
                .hasMessageContaining("active-key-id");
        assertThatThrownBy(() -> new AesGcmCipher(props("k1", Map.of("k1", Base64.getEncoder().encodeToString(new byte[16])))))
                .hasMessageContaining("256 bits");
        assertThatThrownBy(() -> new AesGcmCipher(props("a:b", Map.of("a:b", K1))))
                .hasMessageContaining("must not");
    }

    @Test
    void blindIndexIsDeterministicNormalizedAndKeyed() {
        BlindIndex index = new BlindIndex(props("k1", Map.of("k1", K1)));
        BlindIndex otherKey = new BlindIndex(new EncryptionProperties("k1", Map.of("k1", K1), K2));

        assertThat(index.of("Ann@Mail.com ")).isEqualTo(index.of("ann@mail.com"));
        assertThat(index.of("ann@mail.com")).hasSize(64).isNotEqualTo(index.of("bob@mail.com"));
        assertThat(index.of("ann@mail.com")).isNotEqualTo(otherKey.of("ann@mail.com"));
    }
}
