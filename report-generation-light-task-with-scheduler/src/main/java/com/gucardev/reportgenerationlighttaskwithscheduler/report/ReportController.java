package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportService.ReportRequested;
import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportService.ReportShared;
import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportService.ReportView;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    /** {@code requestedBy} stands in for the logged-in user (in a real app: from the security context). */
    public record ReportRequestBody(String reportType, String requestedBy) {
    }

    public record ShareBody(String recipientEmail) {
    }

    private final ReportService service;

    /** The "Export" button. Returns 202 at once; the UI polls {@code reportUrl} until {@code ready}. */
    @PostMapping
    public ResponseEntity<ReportRequested> request(@RequestBody ReportRequestBody body) {
        ReportRequested requested = service.request(body.reportType(), body.requestedBy());
        return ResponseEntity.accepted().location(URI.create(requested.reportUrl())).body(requested);
    }

    @GetMapping("/{id}")
    public ReportView get(@PathVariable Long id) {
        return service.get(id);
    }

    /** The "Share" button on a finished report: emails it to a colleague in the background. */
    @PostMapping("/{id}/share")
    public ResponseEntity<ReportShared> share(@PathVariable Long id, @RequestBody ShareBody body) {
        return ResponseEntity.accepted().body(service.share(id, body.recipientEmail()));
    }

    @ExceptionHandler(TooManyReportRequestsException.class)
    public ResponseEntity<ProblemDetail> tooMany(TooManyReportRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }
}
