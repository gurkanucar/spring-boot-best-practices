package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.task.BackgroundTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Routes a claimed task to its step. Both payloads are the report id. */
@Component
@RequiredArgsConstructor
public class ReportTaskHandler {

    private final ReportGenerator reportGenerator;
    private final ReportEmailSender reportEmailSender;

    public void handle(BackgroundTask.Type type, String payload) {
        Long reportId = Long.parseLong(payload);
        switch (type) {
            case GENERATE_REPORT -> reportGenerator.generateReport(reportId);
            case SEND_REPORT_READY_EMAIL -> reportEmailSender.sendReportReadyEmail(reportId);
        }
    }
}
