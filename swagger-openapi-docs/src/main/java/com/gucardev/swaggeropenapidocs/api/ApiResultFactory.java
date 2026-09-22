package com.gucardev.swaggeropenapidocs.api;

import java.util.List;
import org.springframework.stereotype.Component;

// A thin web-layer collaborator, not a domain service - controllers depend on it to build
// the envelope that wraps every response.
@Component
public class ApiResultFactory {

    public <T> ApiResult<T> ok(T data) {
        return ok(data, "OK");
    }

    public <T> ApiResult<T> ok(T data, String message) {
        return ApiResult.success(data, null, message);
    }

    public <T> ApiResult<List<T>> page(List<T> content, int pageNumber, int pageSize, long totalElements) {
        return page(content, pageNumber, pageSize, totalElements, "OK");
    }

    public <T> ApiResult<List<T>> page(
            List<T> content, int pageNumber, int pageSize, long totalElements, String message) {
        return ApiResult.success(content, PageInfo.of(pageNumber, pageSize, totalElements), message);
    }
}
