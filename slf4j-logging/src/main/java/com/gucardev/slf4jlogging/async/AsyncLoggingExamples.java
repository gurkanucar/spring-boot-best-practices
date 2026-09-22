package com.gucardev.slf4jlogging.async;

import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncLoggingExamples {

    public record Result(long jobId, String requestId, String threadName) {
    }

    @Async("loggingTaskExecutor")
    public CompletableFuture<Result> run(long jobId) {
        log.atInfo().addKeyValue("event", "job.completed").addKeyValue("jobId", jobId)
                .log("Worker processed demo job");
        return CompletableFuture.completedFuture(new Result(jobId, MDC.get("requestId"),
                Thread.currentThread().getName()));
    }
}
