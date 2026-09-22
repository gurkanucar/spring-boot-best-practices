package com.gucardev.swaggeropenapidocs.api;

import java.util.List;

// Every response, success or failure, uses this shape. Null fields are dropped by
// spring.jackson.default-property-inclusion, so only what applies shows up.
//
// Named ApiResult rather than ApiResponse because Swagger's @ApiResponse/@ApiResponses
// annotations hold those simple names; importing both is a compile error.
public record ApiResult<T>(
        boolean success, String errorCode, String message, T data, PageInfo page, List<FieldError> errors) {

    public record FieldError(String field, String message) {}

    static <T> ApiResult<T> success(T data, PageInfo page, String message) {
        return new ApiResult<>(true, null, message, data, page, null);
    }

    public static ApiResult<Void> error(String errorCode, String message, List<FieldError> errors) {
        return new ApiResult<>(false, errorCode, message, null, null, errors);
    }
}
