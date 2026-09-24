package com.gucardev.entityencryption.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Encrypts a String attribute when it is written and decrypts it when it is read. Opt in per
 * field with {@code @Convert(converter = EncryptedStringConverter.class)}.
 *
 * <p>Spring Boot lets Hibernate obtain converters from the application context, so the cipher
 * can be injected here.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesGcmCipher cipher;

    public EncryptedStringConverter(AesGcmCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : cipher.decrypt(dbData);
    }
}
