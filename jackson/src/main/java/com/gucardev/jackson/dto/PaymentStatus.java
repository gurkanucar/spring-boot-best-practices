package com.gucardev.jackson.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {
    PENDING("pending"),
    PAID("paid"),
    FAILED("failed");

    private final String code;

    PaymentStatus(String code) {
        this.code = code;
    }

    // Serializes as the lowercase code, not the Java constant name.
    @JsonValue
    public String code() {
        return code;
    }

    // Parses leniently: either the code ("paid") or the enum name ("PAID"), case-insensitive.
    @JsonCreator
    public static PaymentStatus fromValue(String value) {
        for (PaymentStatus status : values()) {
            if (status.code.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment status: " + value);
    }
}
