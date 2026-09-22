package com.gucardev.jackson.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// A "type" discriminator field is added to every subtype's JSON on the way out, and read
// back on the way in to pick which record to construct - Jackson never needs to guess.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmailNotification.class, name = "email"),
    @JsonSubTypes.Type(value = SmsNotification.class, name = "sms")
})
public sealed interface Notification permits EmailNotification, SmsNotification {}
