# Spring Boot + Kafka: Airport Inbox

Kafka events and REST requests update the same airport data through a durable inbox.

```text
Kafka airport-events ──┐
                      ├── InboxWriter ── inbox_event ── InboxProcessor ── InboxEventHandler
PUT /api/airports/... ─┘                                                   │
                                                                 airport + runway
```

Spring Boot 4.1.1, Java 25, PostgreSQL 17, Spring Kafka, Flyway, ShedLock and Testcontainers.

## Run

```bash
docker compose up -d
./mvnw spring-boot:run
```

The app listens on port **8095**, PostgreSQL on **5434**, Kafka on **9092**, and Kafka UI on **8085**.
On Windows use `mvnw.cmd`. Tests require Java 25 and Docker:

```bash
./mvnw test
```

## Send an airport snapshot

Every event contains the complete airport state, including its runways:

```json
{
  "transactionId": "tx-ist-002",
  "airportCode": "IST",
  "version": 2,
  "occurredAt": "2026-09-24T10:00:00Z",
  "airport": {
    "icaoCode": "LTFM",
    "name": "Istanbul Airport",
    "city": "Istanbul",
    "countryCode": "TR",
    "timezone": "Europe/Istanbul",
    "runways": [
      { "designator": "16L/34R", "lengthMeters": 3750, "surface": "CONCRETE" }
    ]
  }
}
```

- `transactionId` identifies a change. Resending it does not create another inbox row.
- `version` is the source's increasing version for that airport. Kafka and REST must share the same version sequence.
- `occurredAt` is optional metadata; ordering uses `version`.
- The Kafka message key must equal `airportCode`.

Save the example as `event.json`. The demo publisher forwards it to Kafka:

```bash
curl -X POST http://localhost:8095/dev/kafka/airport-events/IST \
  -H "Content-Type: application/json" --data-binary @event.json
```

For REST, send the same JSON without `airportCode`, which comes from the URL:

```bash
curl -X PUT http://localhost:8095/api/airports/IST \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-ist-003","version":3,"airport":{"icaoCode":"LTFM","name":"Istanbul Airport","city":"Istanbul","countryCode":"TR","timezone":"Europe/Istanbul","runways":[]}}'
```

REST returns `202 Accepted` and a `Location` header pointing to `/api/inbox/{id}`.
It means the event is stored; processing happens asynchronously.

## Inspect the result

| Endpoint | Result |
|---|---|
| `GET /api/airports` | Current airports |
| `GET /api/airports/IST` | Current airport and runways |
| `GET /api/inbox/{id}` | Status of an accepted change |
| `GET /api/inbox?status=FAILED` | Latest 100 failed events |
| `GET /api/inbox` | Latest 100 events |

Inbox states are `PENDING`, `PROCESSED`, `SKIPPED`, and `FAILED`.
An older or equal version is `SKIPPED`; this is not an error.

## The important parts

**Receiving.** `InboxWriter` inserts with `ON CONFLICT (transaction_id) DO NOTHING`.
The database's unique constraint handles concurrent duplicates.
Kafka uses `ack-mode: record`: the listener returns after the inbox transaction commits.
A crash before the offset commit causes a safe redelivery.

**Processing.** `InboxProcessor` selects up to 100 due events per poll.
For each candidate, it calls `InboxEventHandler.process` through Spring's transaction proxy.
The handler locks the inbox row with `FOR UPDATE SKIP LOCKED` and rechecks its status,
attempt count and due time. Selection alone does not claim a row.

`AirportSnapshotApplier` locks an existing airport, applies only newer snapshots, and updates
runways by designator. Airport changes and the inbox's final status commit in the same transaction.
Concurrent first inserts may hit the airport's primary key constraint; the losing event retries.

**Failures.** An apply or commit failure rolls back before the scheduler calls
`recordFailure` in another transaction. That method locks and rechecks the same row.
A delayed failure cannot overwrite a completed event or a newer attempt.

Retries use a linear delay (5s, 10s, ...), stopping at `max-attempts`.
Unreadable or invalid stored payloads fail immediately.
Failed rows remain available for inspection. After correcting the cause, submit an event with
a new `transactionId` and a version newer than the current airport state.

**Validation.** Both inputs use Bean Validation. `AirportValidator` checks time zones and
duplicate runway designators and reports errors on the actual payload fields.
Malformed or invalid Kafka messages go to `airport-events-dlt`.
Infrastructure failures while receiving are retried by Spring Kafka for about a minute before DLT recovery.

## Multiple instances and cleanup

All instances share PostgreSQL and use the same Kafka consumer group. Each runs its own inbox
poller. Row locks protect event processing, while the airport lock protects version comparisons.
The processor intentionally has no ShedLock: instances can process different inbox rows in parallel.

Cleanup runs daily at **03:00 UTC**. It deletes only `PROCESSED` and `SKIPPED` rows whose
`processed_at` is older than `retention` (default **7 days**). Pending and failed rows remain.
The delete runs in a repository transaction; no self-invocation is needed.

`InboxCleanupJob` uses `@SchedulerLock(name = "airport-inbox-cleanup")`. Competing instances
skip the run while the lock is held. The JDBC provider uses the shared database clock via
`usingDbTime()`. The lock stays held for at least one minute to cover nearby scheduler triggers,
and expires after at most ten minutes if the owner crashes. Cleanup's database transaction has a
two-minute timeout. Keep the maximum lock duration above the expected worst-case job duration;
a job exceeding the lease can overlap another execution. See the [ShedLock documentation](https://github.com/lukas-krecan/ShedLock).

Flyway migration `V2__shedlock_and_cleanup_index.sql` adds the lock table and a partial index
for cleanup. Existing databases upgrade without editing the original migration.

**Retention is also the transaction-ID deduplication window.** After cleanup, a redelivered
transaction ID can be inserted again. Its old snapshot is still skipped by the airport version
check, provided the airport and its version have been retained. Set retention longer than your
expected Kafka replay and REST retry window. Finished status URLs return `404` after cleanup.
Failed rows need an operational retention policy if they accumulate.

To try two instances locally, start the shared containers once and run these in separate terminals:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8095"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8096"
```

Both instances use the same datasource and consumer group from `application.yaml`.
For a faster cleanup demo, override `app.inbox.cleanup-cron` and `app.inbox.retention` on both
instances. The one-minute minimum lock still applies. Set `cleanup-cron: "-"` to disable cleanup.

## Scope

This example keeps the receiving, deduplication, version check and asynchronous processing flow.
There is no manual retry API or change-history endpoint.
Finished inbox rows are cleaned up automatically; failed events remain available for investigation.

Snapshots must be complete: skipping older versions is not suitable for incremental deltas.
The same transaction ID with different content is still treated as a duplicate.
The demo publisher and inbox inspection endpoints are for local demonstration.

## Configuration

```yaml
app:
  inbox:
    poll-interval: 500ms
    max-attempts: 5
    retry-backoff: 5s
    retention: 7d
    cleanup-cron: "0 0 3 * * *" # UTC
```

## Tests

Integration tests use real PostgreSQL and Kafka and cover both inputs, duplicate delivery,
version ordering, runway updates, invalid messages, bounded retries and concurrent workers.
Transaction regressions check rollback of airport and inbox together, delayed failure recording,
and protection against stale retry attempts. Null runway entries are checked on both REST and Kafka.
Cleanup tests use a second JDBC lock provider against the same PostgreSQL database to check lock
contention, retention rules, preservation of airport data and safe redelivery after cleanup.
