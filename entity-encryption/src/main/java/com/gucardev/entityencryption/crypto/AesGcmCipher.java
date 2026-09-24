package com.gucardev.entityencryption.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM with a fresh random IV per value.
 *
 * <p>Stored format: {@code v1:<keyId>:<base64url(iv || ciphertext || tag)>}. The key id travels
 * with the value, so several keys can be active for reading while one key is used for writing;
 * that is what makes key rotation possible. GCM is authenticated: a modified value fails to
 * decrypt instead of returning garbage.
 */
@Component
public class AesGcmCipher {

    private static final String VERSION = "v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKey> keys = new HashMap<>();
    private final String activeKeyId;

    public AesGcmCipher(EncryptionProperties properties) {
        properties.keys().forEach((id, base64) -> keys.put(requireValidKeyId(id), toKey(id, base64)));
        this.activeKeyId = properties.activeKeyId();
        if (!keys.containsKey(activeKeyId)) {
            throw new IllegalStateException("app.encryption.active-key-id '" + activeKeyId + "' is not in app.encryption.keys");
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            // A Cipher instance is not thread-safe, so create one per call.
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[IV_BYTES + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, IV_BYTES);
            System.arraycopy(encrypted, 0, payload, IV_BYTES, encrypted.length);
            return VERSION + ":" + activeKeyId + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        String[] parts = stored.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("Value is not in the expected encrypted format");
        }
        SecretKey key = keys.get(parts[1]);
        if (key == null) {
            throw new IllegalStateException("No key configured for key id '" + parts[1] + "'");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Deliberately no value in the message: it may be sensitive.
            throw new IllegalStateException("Decryption failed (wrong key or tampered data)", e);
        }
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    /** Prefix shared by every value written with the active key, e.g. {@code v1:k2:}. */
    public String activePrefix() {
        return VERSION + ":" + activeKeyId + ":";
    }

    private static String requireValidKeyId(String id) {
        if (id.isBlank() || id.contains(":")) {
            throw new IllegalStateException("Key id must not be blank or contain ':' but was '" + id + "'");
        }
        return id;
    }

    private static SecretKey toKey(String id, String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("Key '" + id + "' must be 256 bits (32 bytes) but was " + bytes.length + " bytes");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
