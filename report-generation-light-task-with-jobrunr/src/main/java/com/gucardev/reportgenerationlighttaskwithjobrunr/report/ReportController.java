package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportService.*;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.TaskDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    // Demo identity. In production use the authenticated principal and enforce ownership/tenant access.
    public record ReportRequestBody(@NotBlank @Size(max = 50) String reportType,
                                    @NotBlank @Email @Size(max = 254) String requestedBy) {}
    public record ShareBody(@NotBlank @Email @Size(max = 254) String recipientEmail) {}

    private final ReportService service;
    private final TaskDispatcher dispatcher;

    @PostMapping
    public ResponseEntity<ReportRequested> request(@Valid @RequestBody ReportRequestBody body) {
        var result = service.request(body.reportType(), body.requestedBy());
        dispatcher.tryDispatch(result.jobId()); // Business transaction has committed and released its connection.
        return ResponseEntity.accepted().location(URI.create(result.reportUrl())).body(result);
    }

    @GetMapping("/{id}")
    public ReportView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ReportShared> share(@PathVariable Long id, @Valid @RequestBody ShareBody body) {
        var result = service.share(id, body.recipientEmail());
        dispatcher.tryDispatch(result.jobId());
        return ResponseEntity.accepted().body(result);
    }

    @ExceptionHandler(TooManyReportRequestsException.class)
    public ResponseEntity<ProblemDetail> tooMany(TooManyReportRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }
}
