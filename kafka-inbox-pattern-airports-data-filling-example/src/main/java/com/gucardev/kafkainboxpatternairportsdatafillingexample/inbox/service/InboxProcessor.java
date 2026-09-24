package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportSnapshotApplier;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportSnapshotApplier.Outcome;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportSnapshotApplier.Result;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.config.InboxProperties;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The processing side of the inbox. Polls for due PENDING events and applies them one by one,
 * each in its own transaction:
 *
 * <pre>
 *   BEGIN
 *     claim next event        (SELECT ... FOR UPDATE SKIP LOCKED)
 *     lock the airport row    (SELECT ... FOR UPDATE)
 *     apply if newer version: update the airport and its runways
 *     mark event PROCESSED / SKIPPED
 *   COMMIT                    (airport change and inbox status change succeed or fail together)
 * </pre>
 *
 * If applying throws, that transaction is rolled back and a second, small transaction records
 * the failed attempt.
 */
@Component
public class InboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboxProcessor.class);

    private final InboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final AirportSnapshotApplier applier;
    private final TransactionTemplate tx;
    private final InboxProperties properties;

    public InboxProcessor(InboxEventRepository repository, JsonMapper jsonMapper, Validator validator,
                          AirportSnapshotApplier applier, TransactionTemplate tx, InboxProperties properties) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.applier = applier;
        this.tx = tx;
        this.properties = properties;
    }

    /** Drains everything that is due, then waits for the next poll. */
    @Scheduled(fixedDelayString = "${app.inbox.poll-interval}")
    public void poll() {
        while (processNext()) {
            // keep going until nothing is due
        }
    }

    /** @return false when there was nothing to process */
    public boolean processNext() {
        AtomicReference<Long> claimedId = new AtomicReference<>();
        try {
            Boolean processed = tx.execute(status -> {
                InboxEvent event = repository.claimNext(Instant.now()).orElse(null);
                if (event == null) {
                    return false;
                }
                claimedId.set(event.getId());
                handle(event);
                return true;
            });
            return Boolean.TRUE.equals(processed);
        } catch (RuntimeException e) {
            if (claimedId.get() == null) {
                throw e; // could not even claim: database unavailable; the next poll tries again
            }
            recordFailure(claimedId.get(), e);
            return true;
        }
    }

    private void handle(InboxEvent inboxEvent) {
        // Events stored by InboxWriter were validated on arrival, but a stored payload can still
        // be broken (manual insert, contract change). Retrying cannot fix that: fail at once.
        AirportEvent event;
        try {
            event = jsonMapper.readValue(inboxEvent.getPayload(), AirportEvent.class);
        } catch (JacksonException e) {
            fail(inboxEvent, "Unreadable payload: " + e.getOriginalMessage());
            return;
        }
        Set<ConstraintViolation<AirportEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            fail(inboxEvent, "Invalid payload: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
            return;
        }

        Outcome outcome = applier.apply(event);
        if (outcome.result() == Result.STALE) {
            inboxEvent.markSkipped("Stale: event version %d, airport already at version %d"
                    .formatted(event.version(), outcome.currentVersion()));
        } else {
            inboxEvent.markProcessed();
        }
        log.info("Inbox event {} ({} {} v{}): {}", inboxEvent.getId(), inboxEvent.getSource(),
                event.airportCode(), event.version(), outcome.result());
    }

    private static void fail(InboxEvent inboxEvent, String error) {
        log.error("Inbox event {} cannot be processed: {}", inboxEvent.getId(), error);
        inboxEvent.markFailed(error);
    }

    private void recordFailure(long inboxEventId, RuntimeException error) {
        log.warn("Inbox event {} failed: {}", inboxEventId, error.toString());
        tx.executeWithoutResult(status -> repository.findById(inboxEventId).ifPresent(event ->
                event.recordFailedAttempt(rootMessage(error), properties.maxAttempts(), properties.retryBackoff())));
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
