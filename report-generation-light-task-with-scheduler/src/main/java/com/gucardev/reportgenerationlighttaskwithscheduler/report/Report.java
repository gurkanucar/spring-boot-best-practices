package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A requested report: PENDING until generated, then READY with its content. */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    public enum Status { PENDING, READY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String content;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "generated_at")
    private Instant generatedAt;

    public static Report requested(String reportType, String requestedBy) {
        Report report = new Report();
        report.reportType = reportType;
        report.requestedBy = requestedBy;
        report.status = Status.PENDING;
        report.requestedAt = Instant.now();
        return report;
    }

    public void markReady(String content) {
        this.content = content;
        this.generatedAt = Instant.now();
        this.status = Status.READY;
    }
}
