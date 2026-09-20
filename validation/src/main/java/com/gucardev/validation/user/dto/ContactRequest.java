package com.gucardev.validation.user.dto;

import com.gucardev.validation.constraint.EnumValue;
import com.gucardev.validation.user.ContactType;
import jakarta.validation.constraints.NotBlank;

/** Validated as a collection element; error paths look like {@code contacts[0].value}. */
public record ContactRequest(

        @NotBlank(message = "{validation.contact.type.notblank}")
        @EnumValue(enumClass = ContactType.class, message = "{validation.common.enum.invalid}")
        String type,

        @NotBlank(message = "{validation.contact.value.notblank}")
        String value) {
}
