package com.gucardev.restapiintegration.common.error;

import com.gucardev.restapiintegration.client.error.RemoteApiException;
import com.gucardev.restapiintegration.product.ProductNotFoundException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates remote failures into this application's own responses. The remote API's body is
 * logged, not returned: our clients should not depend on (or see) another system's internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleNotFound(ProductNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** The remote API answered with an error we could not handle: 502 Bad Gateway. */
    @ExceptionHandler(RemoteApiException.class)
    public ProblemDetail handleRemoteError(RemoteApiException ex) {
        log.warn("{}; response body: {}", ex.getMessage(), ex.getResponseBody());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The remote API returned an error");
        problem.setProperty("remoteStatus", ex.getStatus());
        return problem;
    }

    /** No usable HTTP response at all: timeout (504), TLS failure or connection problem (502). */
    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail handleIoError(ResourceAccessException ex) {
        log.warn("Remote call failed: {}", ex.getMessage());
        if (hasCause(ex, HttpTimeoutException.class) || hasCause(ex, SocketTimeoutException.class)) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "The remote API did not respond in time");
        }
        if (hasCause(ex, SSLException.class)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                    "TLS handshake with the remote API failed (certificate not trusted?)");
            problem.setProperty("cause", rootCause(ex).getClass().getSimpleName());
            return problem;
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The remote API is unreachable");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
