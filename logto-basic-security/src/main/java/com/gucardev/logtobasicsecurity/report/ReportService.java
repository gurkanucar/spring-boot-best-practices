package com.gucardev.logtobasicsecurity.report;

import com.gucardev.logtobasicsecurity.security.CurrentUserService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final CurrentUserService currentUser;

    /**
     * The role check is on the service, so it holds whoever calls it, not only the controller. The
     * user comes from {@link CurrentUserService}, not from a parameter.
     */
    @PreAuthorize("hasAnyRole('admin', 'user')")
    public Map<String, Object> list() {
        return Map.of(
                "requestedBy", currentUser.logtoId(),
                "reports", List.of(Map.of("id", 1, "name", "Monthly sales"), Map.of("id", 2, "name", "Active users")));
    }

    /**
     * Runs on an executor thread, yet {@link CurrentUserService} still knows the user: {@code AsyncConfig}
     * carries the security context over. The role is checked by the controller, before the task starts.
     */
    @Async
    public CompletableFuture<Map<String, Object>> generate() {
        return CompletableFuture.completedFuture(Map.of(
                "requestedBy", currentUser.logtoId(),
                "thread", Thread.currentThread().getName(),
                "status", "generated"));
    }
}
