package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.config.TaskProperties;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.dto.ClaimedTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.exception.NonRetryableTaskException;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler.TaskHandler;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.repository.BackgroundTaskRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

/** Runs one claimed task on a worker thread and records the outcome. */
@Component
@Slf4j
public class TaskRunner {

    static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 4000;

    private final Map<TaskType, TaskHandler<Object>> handlers = new EnumMap<>(TaskType.class);
    private final Map<TaskType, ObjectReader> payloadReaders = new EnumMap<>(TaskType.class);
    private final Map<TaskType, Semaphore> typeSlots = new EnumMap<>(TaskType.class);
    private final BackgroundTaskRepository repository;
    private final TransactionTemplate newTransaction;
    private final TaskProperties properties;

    @SuppressWarnings("unchecked")
    public TaskRunner(List<TaskHandler<?>> handlerBeans, BackgroundTaskRepository repository, JsonMapper jsonMapper,
                      PlatformTransactionManager transactionManager, TaskProperties properties) {
        for (TaskHandler<?> handler : handlerBeans) {
            if (handlers.put(handler.type(), (TaskHandler<Object>) handler) != null) {
                throw new IllegalStateException("More than one TaskHandler for " + handler.type());
            }
            payloadReaders.put(handler.type(), payloadReader(handler, jsonMapper));
        }
        for (TaskType type : TaskType.values()) {
            typeSlots.put(type, new Semaphore(properties.concurrencyOf(type)));
        }
        this.repository = repository;
        this.properties = properties;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void run(ClaimedTask task) {
        Semaphore slot = typeSlots.get(task.type());
        // Never wait for a slot here: a blocked worker would be a wasted worker.
        if (!slot.tryAcquire()) {
            putBack(task);
            return;
        }
        try {
            execute(task);
        } finally {
            slot.release();
        }
    }

    /** Returns a claimed task to PENDING without counting an attempt; it is tried again next poll. */
    public void putBack(ClaimedTask task) {
        repository.putBack(task.id(), properties.instanceId(), task.attempts(), seconds(properties.pollInterval()));
    }

    private void execute(ClaimedTask task) {
        Exception failure = null;
        try {
            TaskHandler<Object> handler = handlers.get(task.type());
            if (handler == null) {
                throw new NonRetryableTaskException("No TaskHandler for " + task.type());
            }
            Object payload = readPayload(task);
            // The handler's work commits in its own transaction; the status update below is another,
            // short one. A long report does not hold the task row locked.
            newTransaction.executeWithoutResult(status -> handler.handle(payload));
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
     * Reads the stored JSON as {@code P} of {@code TaskHandler<P>} (resolved like Spring does for
     * {@code ApplicationListener<E>}), with generics: {@code TaskHandler<List<Long>>} gets Longs.
     * Every record field is required: a missing or null value fails here, once, for all handlers.
     */
    static ObjectReader payloadReader(TaskHandler<?> handler, JsonMapper jsonMapper) {
        ResolvableType type = ResolvableType.forInstance(handler).as(TaskHandler.class).getGeneric(0);
        if (type.resolve() == null) {
            throw new IllegalStateException(handler.getClass().getName()
                    + " must declare its payload type, e.g. implements TaskHandler<Long>");
        }
        return jsonMapper.readerFor(javaType(type, jsonMapper.getTypeFactory()))
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
    }

    // Built from the resolved type, so P also works when it comes through a generic base class.
    private static JavaType javaType(ResolvableType type, TypeFactory typeFactory) {
        Class<?> raw = type.resolve(Object.class);
        if (!type.hasGenerics()) {
            return typeFactory.constructType(raw);
        }
        JavaType[] parameters = Arrays.stream(type.getGenerics())
                .map(generic -> javaType(generic, typeFactory))
                .toArray(JavaType[]::new);
        return typeFactory.constructParametricType(raw, parameters);
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
