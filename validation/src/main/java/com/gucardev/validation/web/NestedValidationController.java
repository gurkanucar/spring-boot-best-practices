package com.gucardev.validation.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.gucardev.validation.user.dto.NestedUserRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 6 - error paths mirror the object graph: {@code address.city}, {@code contacts[1].value},
 * so a client can attach each error to the right form field.
 */
@RestController
@RequestMapping("/api/nested/users")
public class NestedValidationController {

    @PostMapping
    public NestedUserRequest create(@Valid @RequestBody NestedUserRequest request) {
        return request;
    }

    @PostMapping("/bulk")
    public List<NestedUserRequest> createBulk(@Valid @RequestBody BulkRequest body) {
        return body.requests();
    }

    /**
     * DELEGATING is required: without it Jackson 3 treats the canonical constructor as a
     * property-based creator and rejects a plain array. Element-type {@code @Valid} alone cascades.
     */
    public record BulkRequest(List<@Valid NestedUserRequest> requests) {

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static BulkRequest of(List<NestedUserRequest> requests) {
            return new BulkRequest(requests);
        }
    }
}
