package com.gucardev.jackson.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.NotBlank;

// @JsonUnwrapped flattens address's fields (street/city/zipCode) directly into this
// object's JSON instead of nesting them under an "address" key.
public record Contact(@NotBlank String name, @JsonUnwrapped Address address) {}
