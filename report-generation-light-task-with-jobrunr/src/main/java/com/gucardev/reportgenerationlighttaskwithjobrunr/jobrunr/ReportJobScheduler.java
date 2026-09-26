package com.gucardev.reportgenerationlighttaskwithjobrunr.jobrunr;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;

/** The only class that calls JobRunr's JobScheduler. */
@Component
@RequiredArgsConstructor
public class ReportJobScheduler {

    private final JobScheduler jobScheduler;

    public UUID scheduleReportGeneration(Long requestId) {
        return jobScheduler.<ReportJobs>enqueue(jobId("generate-report:" + requestId),
                j -> j.generateReport(requestId)).asUUID();
    }

    public UUID scheduleReportReadyEmail(Long requestId) {
        return jobScheduler.<ReportJobs>enqueue(jobId("report-ready-email:" + requestId),
                j -> j.sendReportReadyEmail(requestId)).asUUID();
    }

    // Same key, same job id. JobRunr skips an enqueue whose id already exists, so re-enqueueing is a no-op.
    private static UUID jobId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
