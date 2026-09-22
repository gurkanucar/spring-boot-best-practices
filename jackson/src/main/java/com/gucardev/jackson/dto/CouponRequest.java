package com.gucardev.jackson.dto;

import com.gucardev.jackson.convert.UppercaseDeserializer;
import com.gucardev.jackson.convert.UppercaseSerializer;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

public record CouponRequest(
        @NotBlank
                @JsonSerialize(using = UppercaseSerializer.class)
                @JsonDeserialize(using = UppercaseDeserializer.class)
                String code) {}
