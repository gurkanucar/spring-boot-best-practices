package com.gucardev.jackson.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;

// spring.jackson.default-property-inclusion=non_null (application.yaml) means a null
// nickname is simply absent from the JSON, not written as "nickname":null.
public record UserProfile(
        // READ_ONLY: accepted from nowhere on input (the server assigns it), but always
        // present on output.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) Long id,
        String fullName,
        String nickname,
        // Overrides the module-wide date format for just this field.
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate birthDate,
        // No @JsonFormat here: falls back to spring.jackson.datatype.datetime.write-dates-as-timestamps=false,
        // so this renders as an ISO-8601 string, not an epoch-seconds array.
        Instant createdAt,
        // @JsonIgnore excludes this from BOTH directions - unlike READ_ONLY/WRITE_ONLY
        // above, which each expose exactly one direction, this never appears on output no
        // matter what the server sets it to, AND any value a client sends for it is
        // dropped before this record is even constructed.
        @JsonIgnore Integer internalRiskScore) {}
