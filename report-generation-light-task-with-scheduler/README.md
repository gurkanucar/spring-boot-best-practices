# Spring Boot: Background Report Export with @Scheduled + ShedLock

A user clicks **Export**. The API answers at once with `202 Accepted`; the report is generated in
the background and a "your report is ready" email follows. No JobRunr, Quartz or message broker:
a `background_task` table, `@Scheduled`, ShedLock and a small worker pool. Runs safely on several
instances.

Spring Boot 4.1.1, Java 25, PostgreSQL 17, Flyway, ShedLock, Testcontainers. Report content and emails are
demos (`ReportMailer` only logs).

## Run

```bash
docker compose up -d
./mvnw spring-boot:run
```

```bash
curl -i -X POST http://localhost:8099/api/reports -H "Content-Type: application/json" \
  -d '{"reportType": "CUSTOMER_LIST", "requestedBy": "alice@example.com"}'
# 202 {"reportRequestId": 1, "taskId": "...", "reportUrl": "/api/reports/1"}

curl http://localhost:8099/api/reports/1        # "ready": true once generated

# share the finished report (409 while it is not ready)
curl -X POST http://localhost:8099/api/reports/1/share -H "Content-Type: application/json" \
  -d '{"recipientEmail": "bob@example.com"}'
```

The schema changed (`locked_by` column removed, V1 was renamed). If you
ran an older version, reset the database once: `docker compose down -v`.

## Flow

```text
POST /api/reports ── report_request + REPORT_GENERATION task (one transaction)
                                   │
TaskWorker.poll (@Scheduled + ShedLock) ── claim due tasks ── worker pool
                                   │
ReportTasks.generate ── report + EMAIL_SEND task (one transaction) ── sendReadyEmail ── ReportMailer
POST /api/reports/{id}/share ── REPORT_SHARE task ── ReportTasks.share ── ReportMailer
```

## Files

```text
ReportGenerationLightTaskWithSchedulerApplication   @EnableScheduling, ShedLock lock provider,
                                                    worker thread pool
report/  ReportController, ReportService, ReportRequest, Report, repositories,
         ReportTasks (the task handlers), ReportMailer (demo mail adapter)
task/    BackgroundTask (entity, Status, Type), BackgroundTaskRepository (claim/finish/recover SQL),
         TaskService (enqueue), TaskWorker (poll, run, retry, recover stuck tasks)
```

## Guarantees

- **Survives restarts** — tasks are rows in PostgreSQL, enqueued in the caller's transaction.
- **Multi-instance safe** — ShedLock lets one instance at a time run the poll (and the stuck-task
  recovery). The lock covers only the short claim, not the 5-10 minute task: claimed tasks run on
  the instance that claimed them while other instances keep polling. The claim query also uses
  `FOR UPDATE SKIP LOCKED` as a second guard, so a row is never claimed twice even if two polls overlap.
- **Retry with backoff** — 30s, 2m, 8m, 32m (max 1h), 5 attempts, then `DEAD` for a human to look at.
  `last_error` keeps a short error summary.
- **Idempotent** — one report per request (unique constraint), one task per idempotency key
  (e.g. the same report shared with the same person twice), and the mailer receives a stable
  idempotency key for the provider.
- **Report and email are linked** — the report and its email task commit together.
- **Crash recovery** — tasks `RUNNING` longer than `stuck-after` go back to `PENDING` (or `DEAD`).
  A late result from the old execution is ignored (updates match status and attempt).

## Configuration

```yaml
tasks:
  poll-interval: 2s   # how often each instance looks for due tasks
  workers: 4          # tasks running at once per instance
  stuck-after: 20m    # must exceed the slowest legitimate task
  # first-retry-delay: 30s  (backoff base; the tests use 1s)
```

## Deliberately left out

Per-user rate limiting, request validation, per-type concurrency limits, non-retryable error types
and a task admin API. Add them when a real use case needs them.

## Tests

`./mvnw test` (needs Docker): `ReportFlowTest` covers the full flow, a retried email and sharing
(409 when not ready, one task and one email per recipient) against real PostgreSQL.
