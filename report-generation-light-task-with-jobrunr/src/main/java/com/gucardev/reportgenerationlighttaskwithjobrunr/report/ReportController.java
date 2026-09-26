package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportService.*;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    // Demo identity. In production use the authenticated principal and enforce ownership/tenant access.
    public record ReportRequestBody(String reportType, String requestedBy) {}
    public record ShareBody(String recipientEmail) {}

    private final ReportService service;

    @PostMapping
    public ResponseEntity<ReportRequested> request(@RequestBody ReportRequestBody body) {
        var result = service.request(body.reportType(), body.requestedBy());
        return ResponseEntity.accepted().location(URI.create(result.reportUrl())).body(result);
    }

    @GetMapping("/{id}")
    public ReportView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ReportShared> share(@PathVariable Long id, @RequestBody ShareBody body) {
        return ResponseEntity.accepted().body(service.share(id, body.recipientEmail()));
    }
}
