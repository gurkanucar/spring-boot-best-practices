package com.gucardev.jackson.dto;

import com.gucardev.jackson.convert.PartialMaskSerializer;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonSerialize;

// No @JsonDeserialize here on purpose: apiKey is only ever masked on the way OUT. There is
// nothing to "un-mask" on the way in - a caller either sends the real key it was issued,
// or doesn't send this endpoint a key at all.
public record ApiKeyRequest(
        @NotBlank String name,
        @NotBlank @JsonSerialize(using = PartialMaskSerializer.class) String apiKey) {}
