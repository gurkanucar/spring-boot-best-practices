package com.gucardev.jackson.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

// Deliberate gotcha, kept here instead of removed: @JsonIgnoreProperties(ignoreUnknown =
// false) looks like it should re-enable strict rejection for just this class, but it does
// NOT - once the module-wide DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES is disabled
// (spring.jackson.deserialization.fail-on-unknown-properties=false in application.yaml),
// Jackson ignores unknown properties if EITHER the class annotation says so OR the global
// feature is off; the annotation can only grant leniency under an otherwise-strict global
// default, never take it away once the global default already grants it. Confirmed by
// LegacyOrderRequestTest.classLevelIgnoreUnknownFalseDoesNotOverrideTheLenientGlobalDefault
// - an unknown field here still returns 200, not 400.
@JsonIgnoreProperties(ignoreUnknown = false)
public record LegacyOrderRequest(
        @NotBlank String sku,
        // Accepts the old field name "qty" as well as the canonical "quantity".
        @JsonAlias("qty") @Positive int quantity) {}
