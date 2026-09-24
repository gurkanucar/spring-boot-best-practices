package com.gucardev.entityencryption.crypto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.encryption")
public record EncryptionProperties(
        String activeKeyId,
        Map<String, String> keys,
        String blindIndexKey) {
}
