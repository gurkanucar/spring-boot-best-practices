package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    private UUID id;

    @Column(name = "report_request_id", nullable = false, unique = true)
    private Long reportRequestId;

    @Column(nullable = false)
    private String content;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public static Report generated(Long reportRequestId, String content) {
        Report report = new Report();
        report.id = UUID.randomUUID();
        report.reportRequestId = reportRequestId;
        report.content = content;
        report.generatedAt = Instant.now();
        return report;
    }
}
