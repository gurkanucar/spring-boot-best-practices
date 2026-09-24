package com.gucardev.eronetomany.author.dto;

import com.gucardev.eronetomany.book.dto.BookRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** {@code books} is optional: an author can be created without books and get them later. */
public record CreateAuthorRequest(

        @NotBlank(message = "must not be blank")
        String name,

        List<@Valid BookRequest> books) {
}
