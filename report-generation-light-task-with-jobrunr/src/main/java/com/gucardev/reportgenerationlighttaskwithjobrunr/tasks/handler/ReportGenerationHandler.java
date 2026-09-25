package com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.handler;

import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportRepository;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportRequestRepository;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportService;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.TaskDispatcher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportGenerationHandler {

    private final ReportRequestRepository requests;
    private final ReportRepository reports;
    private final ReportService service;
    private final TaskDispatcher dispatcher;

    @Job(name = "Generate report %0")
    public void handle(Long requestId) {
        if (reports.existsByReportRequestId(requestId)) {
            return;
        }
        var request = requests.findById(requestId)
                .orElseThrow(() -> new JobRunrException("Report request not found: " + requestId, true));
        // Demo: replace with XLSX rendering/upload. Keep expensive work outside the DB transaction.
        String content = "%s report for %s".formatted(request.getReportType(), request.getRequestedBy());
        UUID emailId = service.completeGeneration(requestId, content);
        dispatcher.tryDispatch(emailId); // The result transaction has completed.
    }
}
