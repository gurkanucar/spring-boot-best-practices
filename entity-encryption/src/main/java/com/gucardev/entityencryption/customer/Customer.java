package com.gucardev.entityencryption.customer;

import com.gucardev.entityencryption.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Not sensitive: stored as plain text and freely searchable. */
    @Column(nullable = false)
    private String name;

    /** Encrypted, and searchable through {@link #emailHash}. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 512)
    private String email;

    /** Blind index of the email (HMAC-SHA256 hex): the only thing a lookup can use. */
    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    /** Encrypted, not searchable. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id", nullable = false, length = 512)
    private String nationalId;

    /** Encrypted, optional. A null stays a null in the database. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String phone;

    public Customer(String name, String email, String emailHash, String nationalId, String phone) {
        update(name, email, emailHash, nationalId, phone);
    }

    /** The email and its hash always change together, so they are set through one method. */
    public void update(String name, String email, String emailHash, String nationalId, String phone) {
        this.name = name;
        this.email = email;
        this.emailHash = emailHash;
        this.nationalId = nationalId;
        this.phone = phone;
    }
}
