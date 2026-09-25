package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReportRequestId(Long reportRequestId);

    Optional<Report> findByReportRequestId(Long reportRequestId);
}
