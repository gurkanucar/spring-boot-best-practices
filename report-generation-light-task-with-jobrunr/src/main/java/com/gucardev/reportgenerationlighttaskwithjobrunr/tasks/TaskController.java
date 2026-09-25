package com.gucardev.reportgenerationlighttaskwithjobrunr.tasks;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jobrunr.storage.JobNotFoundException;
import org.jobrunr.storage.StorageProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class TaskController {

    public record JobView(UUID jobId, String state) {}

    private final TaskSubmissionRepository submissions;
    private final StorageProvider storage;

    @GetMapping("/{id}")
    public JobView get(@PathVariable UUID id) {
        var submission = submissions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        try {
            return new JobView(id, storage.getJobById(id).getState().name());
        } catch (JobNotFoundException e) {
            return new JobView(id, submission.getSubmittedAt() == null ? "WAITING_DISPATCH" : "UNAVAILABLE");
        }
    }
}
