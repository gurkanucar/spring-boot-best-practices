package com.gucardev.validation.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Without {@code @Valid} on {@code address}, nested constraints never run;
 * on collections {@code @Valid} goes on the element type, {@code @NotEmpty} on the list itself.
 */
public record NestedUserRequest(

        @NotBlank(message = "{validation.user.fullName.notblank}")
        String fullName,

        @NotNull(message = "{validation.address.notnull}")
        @Valid
        AddressRequest address,

        @NotEmpty(message = "{validation.contacts.notempty}")
        List<@Valid ContactRequest> contacts) {
}
