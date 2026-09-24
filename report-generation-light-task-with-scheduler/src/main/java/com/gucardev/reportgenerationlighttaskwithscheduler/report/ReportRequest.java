package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportRequest {

    @Id
    private UUID id;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ReportRequest create(String reportType, String requestedBy) {
        ReportRequest request = new ReportRequest();
        request.id = UUID.randomUUID();
        request.reportType = reportType;
        request.requestedBy = requestedBy;
        request.createdAt = Instant.now();
        return request;
    }
}
