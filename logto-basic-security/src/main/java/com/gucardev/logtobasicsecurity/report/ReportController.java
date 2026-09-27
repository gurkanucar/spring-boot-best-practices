package com.gucardev.logtobasicsecurity.report;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** Logto role admin or user; checked by {@link ReportService#list()}. */
    @GetMapping("/reports")
    public Map<String, Object> reports() {
        return reportService.list();
    }

    /**
     * Generated on another thread ({@code @Async}); the response shows which thread and for whom.
     * Checked here rather than on the async method: a denial inside the task would only surface
     * through the future, as a failed task instead of a plain 403.
     */
    @PreAuthorize("hasAnyRole('admin', 'user')")
    @PostMapping("/reports/generate")
    public CompletableFuture<Map<String, Object>> generate() {
        return reportService.generate();
    }
}
