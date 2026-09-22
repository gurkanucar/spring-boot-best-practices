package com.gucardev.jackson.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        // WRITE_ONLY: accepted on input, but never written back out - echoing this record
        // in a response drops the field entirely instead of nulling or masking it.
        @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {}
