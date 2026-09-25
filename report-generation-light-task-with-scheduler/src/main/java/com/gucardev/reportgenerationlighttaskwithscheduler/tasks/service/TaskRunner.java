package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.config.TaskProperties;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.dto.ClaimedTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.exception.NonRetryableTaskException;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler.TaskHandler;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.repository.BackgroundTaskRepository;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/** Runs one claimed task on a worker thread and records the outcome. */
@Component
@Slf4j
public class TaskRunner {

    static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 4000;

    private final Map<TaskType, TaskHandler<Object>> handlers = new EnumMap<>(TaskType.class);
    private final Map<TaskType, ObjectReader> payloadReaders = new EnumMap<>(TaskType.class);
    private final BackgroundTaskRepository repository;
    private final TaskProperties properties;

    @SuppressWarnings("unchecked")
    public TaskRunner(List<TaskHandler<?>> handlerBeans, BackgroundTaskRepository repository, JsonMapper jsonMapper,
                      TaskProperties properties) {
        for (TaskHandler<?> handler : handlerBeans) {
            if (handlers.put(handler.type(), (TaskHandler<Object>) handler) != null) {
                throw new IllegalStateException("More than one TaskHandler for " + handler.type());
            }
            payloadReaders.put(handler.type(), payloadReader(handler, jsonMapper));
        }
        this.repository = repository;
        this.properties = properties;
    }

    /** Returns a claimed task to PENDING without counting an attempt; it is tried again next poll. */
    public void putBack(ClaimedTask task) {
        repository.putBack(task.id(), properties.instanceId(), task.attempts(), seconds(properties.pollInterval()));
    }

    public void run(ClaimedTask task) {
        Exception failure = null;
        try {
            TaskHandler<Object> handler = handlers.get(task.type());
            if (handler == null) {
                throw new NonRetryableTaskException("No TaskHandler for " + task.type());
            }
            Object payload = readPayload(task);
            // Handlers own their short database transactions. Rendering and network calls should
            // not keep a database connection/transaction open for the lifetime of the task.
            handler.handle(payload);
        } catch (Exception e) {
            failure = e;
        }

        String instanceId = properties.instanceId();
        if (failure == null) {
            repository.markSucceeded(task.id(), instanceId, task.attempts());
            log.info("Task {} {} succeeded (attempt {})", task.type(), task.id(), task.attempts());
        } else if (failure instanceof NonRetryableTaskException || task.attempts() >= task.maxAttempts()) {
            repository.markDead(task.id(), instanceId, task.attempts(), summarize(failure));
            log.error("Task {} {} is DEAD after attempt {}", task.type(), task.id(), task.attempts(), failure);
        } else {
            Duration backoff = backoff(task.attempts());
            repository.scheduleRetry(task.id(), instanceId, task.attempts(), seconds(backoff), summarize(failure));
            log.warn("Task {} {} failed (attempt {}), retry in {}", task.type(), task.id(), task.attempts(), backoff,
                    failure);
        }
    }

    // A wrong or missing payload will not fix itself: non-retryable.
    private Object readPayload(ClaimedTask task) {
        try {
            Object payload = payloadReaders.get(task.type()).readValue(task.payload());
            if (payload == null) { // a JSON null for a single-id payload
                throw new IllegalArgumentException("payload is null");
            }
            return payload;
        } catch (RuntimeException e) {
            throw new NonRetryableTaskException("Unreadable payload for " + task.type() + ": " + task.payload(), e);
        }
    }

    /**
     * Jackson resolves {@code P} of {@code TaskHandler<P>}, including inherited generic types:
     * {@code TaskHandler<List<Long>>} gets Longs without a custom type-resolution algorithm.
     * Every record field is required: a missing or null value fails here, once, for all handlers.
     */
    static ObjectReader payloadReader(TaskHandler<?> handler, JsonMapper jsonMapper) {
        JavaType type = jsonMapper.getTypeFactory().constructType(AopUtils.getTargetClass(handler))
                .findSuperType(TaskHandler.class).containedType(0);
        if (type == null) {
            throw new IllegalStateException(handler.getClass().getName()
                    + " must declare its payload type, e.g. implements TaskHandler<Long>");
        }
        return jsonMapper.readerFor(type)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
    }

    /** 30s, 2m, 8m, 32m, then 1h. */
    static Duration backoff(int attempts) {
        Duration delay = Duration.ofSeconds(30).multipliedBy(1L << (2 * Math.min(attempts - 1, 10)));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    /** The exception chain with a few frames each: enough to diagnose, small enough to store. */
    static String summarize(Throwable error) {
        StringBuilder summary = new StringBuilder();
        int depth = 0;
        for (Throwable t = error; t != null && depth < 5; t = t.getCause(), depth++) {
            summary.append(depth == 0 ? "" : "Caused by: ").append(t).append('\n');
            StackTraceElement[] frames = t.getStackTrace();
            for (int i = 0; i < Math.min(3, frames.length); i++) {
                summary.append("\tat ").append(frames[i]).append('\n');
            }
        }
        return summary.length() <= MAX_ERROR_LENGTH ? summary.toString() : summary.substring(0, MAX_ERROR_LENGTH);
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }
}
