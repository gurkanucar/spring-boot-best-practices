package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReportRequestId(UUID reportRequestId);

    Optional<Report> findByReportRequestId(UUID reportRequestId);
}
