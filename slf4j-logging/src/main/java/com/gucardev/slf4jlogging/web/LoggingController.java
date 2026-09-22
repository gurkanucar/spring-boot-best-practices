package com.gucardev.slf4jlogging.web;

import com.gucardev.slf4jlogging.async.AsyncLoggingExamples;
import com.gucardev.slf4jlogging.examples.AuditExamples;
import com.gucardev.slf4jlogging.examples.LoggingExamples;
import com.gucardev.slf4jlogging.examples.OrderExamples;
import com.gucardev.slf4jlogging.examples.SafeLoggingExamples;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logging")
@RequiredArgsConstructor
public class LoggingController {

    private final LoggingExamples examples;
    private final OrderExamples orders;
    private final AuditExamples audit;
    private final SafeLoggingExamples safe;
    private final AsyncLoggingExamples async;

    public record Acknowledgement(String example, String requestId) {
    }

    public record SensitiveRequest(@NotBlank @Email @Size(max = 254) String email,
                                   @NotBlank String password, @NotBlank String accessToken) {
        @Override
        public String toString() {
            return "SensitiveRequest[REDACTED]";
        }
    }

    public record NoteRequest(@NotBlank @Size(max = 1_000) String text) {
    }

    @PostMapping("/levels/{orderId}")
    public Acknowledgement levels(@PathVariable @Min(1) long orderId) {
        examples.levels(orderId);
        return acknowledge("levels");
    }

    @PostMapping("/parameterized/{orderId}")
    public Acknowledgement parameterized(@PathVariable @Min(1) long orderId) {
        examples.parameterized(orderId);
        return acknowledge("parameterized");
    }

    @PostMapping("/lazy/{orderId}")
    public Acknowledgement lazy(@PathVariable @Min(1) long orderId) {
        examples.lazy(orderId, () -> "summary-for-order-" + orderId);
        return acknowledge("lazy");
    }

    @PostMapping("/structured/{orderId}")
    public Acknowledgement structured(@PathVariable @Min(1) long orderId) {
        examples.structured(orderId);
        return acknowledge("structured");
    }

    @PostMapping("/audit/{orderId}")
    public Acknowledgement audit(@PathVariable @Min(1) long orderId) {
        audit.orderProcessed(orderId);
        return acknowledge("audit");
    }

    @PostMapping("/orders/{orderId}")
    public OrderExamples.Result order(@PathVariable @Min(1) long orderId,
            @RequestParam(defaultValue = "SUCCESS") OrderExamples.Mode mode) {
        return orders.process(orderId, mode);
    }

    @PostMapping("/sensitive")
    public Acknowledgement sensitive(@Valid @RequestBody SensitiveRequest request) {
        safe.loginAttempt(request.email());
        return acknowledge("sensitive");
    }

    @PostMapping("/safe-text")
    public Acknowledgement safeText(@Valid @RequestBody NoteRequest request) {
        safe.userSuppliedText(request.text());
        return acknowledge("safe-text");
    }

    @PostMapping("/async/{jobId}")
    public AsyncLoggingExamples.Result async(@PathVariable @Min(1) long jobId) {
        // This demo waits so you can see the worker's requestId in the HTTP response.
        // Separate bean invocation is required for the @Async proxy to run.
        return async.run(jobId).join();
    }

    private Acknowledgement acknowledge(String example) {
        return new Acknowledgement(example, MDC.get("requestId"));
    }
}
