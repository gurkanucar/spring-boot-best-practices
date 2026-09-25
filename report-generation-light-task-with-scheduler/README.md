# Spring Boot: Background Report Export with @Scheduled + ShedLock

A user clicks **Export** in the UI. The API answers at once with `202 Accepted`; the report is
generated in the background, and a "your report is ready" email follows. No JobRunr, Quartz or
Spring Batch: just a `background_task` table, `@Scheduled`, ShedLock and a Spring worker pool.
The database stores pending work; no separate message broker is required.

```text
POST /api/reports ── report_request + background_task (one transaction)
                                         │
   TaskPoller (@Scheduled + ShedLock) ── claims due tasks ── worker pool ── TaskHandler
                                                                   │
                                        ReportGenerationHandler ── report + EMAIL_SEND task
```

Spring Boot 4.1.1, Java 25, PostgreSQL 17, Flyway, ShedLock and Testcontainers.

The report content and email sends are demonstrations: actual XLSX generation, object storage and
mail-provider integration are TODOs in the handlers.

## Run

```bash
docker compose up -d
./mvnw spring-boot:run
```

```bash
curl -i -X POST http://localhost:8099/api/reports -H "Content-Type: application/json" \
  -d '{"reportType": "CUSTOMER_LIST", "requestedBy": "alice@example.com"}'
# 202 {"reportRequestId": "...", "taskId": "...", "reportUrl": "/api/reports/..."}

curl http://localhost:8099/api/reports/{reportRequestId}   # "ready": true once generated

# share the finished report with a colleague (409 while it is not ready yet)
curl -X POST http://localhost:8099/api/reports/{reportRequestId}/share -H "Content-Type: application/json" \
  -d '{"recipientEmail": "bob@example.com"}'
```

**Rate limit.** A user can request at most **3 reports in 10 minutes** (sliding window). The 4th
request gets `429 Too Many Requests` with a `Retry-After` header in seconds. A per-user
PostgreSQL advisory lock makes the check safe against double clicks. Configure it under
`reports.rate-limit`.

## How it works

- **Enqueue** — `TaskService.enqueue(type, payload, idempotencyKey)` inserts a PENDING row in the
  caller's transaction: the task exists exactly when the business data was saved. A repeated
  idempotency key returns the existing task (`ON CONFLICT DO NOTHING`).
- **Poll** — every 2s, ShedLock lets one instance dispatch at a time. The poller asks for the
  executor's available capacity, claims due tasks with JPA queries, and submits them. Each claim
  conditionally changes PENDING to RUNNING; a task already claimed by another dispatcher is skipped.
  No `SKIP LOCKED`, local semaphore or explicit Java synchronization is needed.
- **Per-type limits** — e.g. at most 2 report generations at once **per instance**. Before claiming
  a type, `TaskService` counts that instance's RUNNING rows. At capacity, that type stays PENDING;
  other types can still be selected. The fixed executor limits total running workers to `tasks.workers`.
  If submission is rejected (capacity changed or shutdown began), the task is returned to PENDING
  without consuming an attempt. Tasks of each type are selected oldest first; cross-type fairness
  is not guaranteed.
- **Result** — the handler owns its transaction boundaries. Rendering and external calls run outside
  a transaction; the report result and its follow-up email task commit together in a short one.
  The status update is another short transaction.
  Success → SUCCEEDED. Failure → PENDING again after 30s, 2m, 8m, 32m, 1h... until `max_attempts`
  (5), then DEAD. `NonRetryableTaskException` → DEAD at once.
- **Recovery** — every minute, tasks RUNNING for more than 20 minutes (a crashed instance) go back
  to PENDING, or DEAD if all attempts were used. `stuck-after` must be longer than your slowest task.
  Recovery is not a timeout and does not cancel the old worker; handlers must tolerate overlap.
- **Shutdown** — Spring stops scheduling and the executor waits up to `shutdown-wait` (60s) for
  submitted work. This is a shutdown grace period, not a task timeout or forced cancellation.
  Work interrupted by process termination is later picked up by recovery.

Handlers must be **idempotent**: a task can run twice (crash before SUCCEEDED, recovery).
The report handler skips requests that already have a report; the email handler should pass an
idempotency key to the mail provider.

ShedLock protects the **short claim/submission phase**, not the 5–10 minute work. Its 30s
`lockAtMostFor` must exceed the worst-case dispatch time; exceeding it can allow overlapping
dispatches and invalidate capacity counts. The conditional claim still prevents two dispatchers
from claiming the same pending attempt. See [ShedLock's guarantees](https://github.com/lukas-krecan/ShedLock).

`stuck-after` must exceed the real task duration. Recovery does not stop the old execution; if this
assumption is violated, work can overlap and the RUNNING-row count is no longer an accurate
per-type execution limit. Configure real network/query timeouts and idempotent handlers.

## Database portability

The scheduler lock uses ShedLock's JDBC provider. Task selection and claim use Spring Data JPA;
there is no PostgreSQL-specific row-lock syntax in that path.

The **demo as a whole still targets PostgreSQL**: migrations, `jsonb`, enqueue's `ON CONFLICT ...
RETURNING`, retry/recovery interval SQL, and the report rate limit's advisory lock are database
specific. For another database, adapt these queries, mappings, migrations and driver, and rerun
the integration suite against that database. Restoring ShedLock alone does not make them portable.
Keep instance clocks synchronized: JPA claim timestamps use the application clock, whereas the
native state updates and ShedLock use the database clock.

## Adding a new task type

1. Add a constant to `TaskType`, e.g. `INVOICE_PDF`.
2. Write a handler bean. Its generic type is the payload: the runner converts the stored JSON to it
   before calling `handle`. For a single id, use the id type directly:

   ```java
   @Component
   public class InvoicePdfHandler implements TaskHandler<Long> {

       public TaskType type() { return TaskType.INVOICE_PDF; }

       public void handle(Long invoiceId) {
           // idempotent: return early if the PDF for this invoice already exists
       }
   }
   ```

   Need more than one value? Use a small record instead, e.g.
   `TaskHandler<InvoicePdfHandler.Payload>` with `record Payload(Long invoiceId, String locale)`.

3. Enqueue it where the work arises, inside that transaction:

   ```java
   taskService.enqueue(TaskType.INVOICE_PDF, invoice.getId(), "invoice-pdf:" + invoice.getId());
   ```

4. Optionally limit it: `tasks.concurrency.INVOICE_PDF: 4` (default: all workers).

Use a short `@Transactional` service method or `TransactionTemplate` for database writes and
follow-up enqueues that must commit together. Do not wrap a 5–10 minute export or an email-provider
call in that transaction. A runner upgrade does not supply an implicit handler transaction anymore;
custom handlers that relied on it need an explicit transaction boundary.

Keep payloads small (ids only); the handler loads what it needs. Every value is required: a payload
that does not fit the handler's type (missing, null, wrong type) makes the task DEAD at once, since
retrying would not fix it.

The handlers here show the three styles:

| Handler | Payload type | Stored in `background_task.payload` |
|---|---|---|
| `ReportGenerationHandler` | `TaskHandler<Long>` (report request id) | `42` |
| `EmailSendHandler` | `TaskHandler<UUID>` (report id) | `"3f1c…"` |
| `ReportShareHandler` | `TaskHandler<Payload>`, `record Payload(UUID reportId, String recipientEmail)` | `{"reportId": "3f1c…", "recipientEmail": "…"}` |

## What gets stored

`enqueue` turns the payload into JSON with Jackson and stores that text in the `payload` column
(`jsonb`), in the caller's transaction. Nothing stays in memory. When the task runs (first time, on a
retry, after a restart, on another instance), the runner reads the row from the database and
converts the JSON back into the handler's type before calling `handle`.

Any type that Jackson can write and read back works: `Long`, `UUID`, `String`, enums, `LocalDate`,
records of these, `List<Long>`... Only data is stored, not Java objects, so:

- pass ids, not entities: the payload is a snapshot from enqueue time, and the handler should load
  current data itself;
- renaming a payload record's field breaks tasks that are still waiting with the old name.

## Package layout

```text
tasks/
  config/      TaskConfig (ShedLock, Spring worker pool and shutdown settings), TaskProperties
  entity/      BackgroundTask, TaskStatus, TaskType
  repository/  BackgroundTaskRepository (claim and status updates)
  dto/         ClaimedTask
  exception/   NonRetryableTaskException
  handler/     TaskHandler<P> and its implementations
  service/     TaskService (enqueue, claim), TaskRunner (execution, retry, backoff)
  scheduler/   TaskPoller, StuckTaskRecovery (the @Scheduled jobs)
report/        the demo business code: export request, rate limit, report
```

## Configuration

```yaml
tasks:
  poll-interval: 2s
  workers: 16
  concurrency:
    REPORT_GENERATION: 2
  stuck-after: 20m
  shutdown-wait: 60s
reports:
  rate-limit:
    max-requests: 3
    window: 10m
```

All capacity limits are **per application instance**:

| Setting | Meaning |
|---|---|
| `poll-interval: 2s` | Delay between dispatch passes. Running handlers continue independently; this is not their timeout. |
| `workers: 16` | Shared ceiling of 16 running tasks across all types. Waiting work remains in the database. |
| `concurrency.REPORT_GENERATION: 2` | At most 2 report tasks on this instance. It reserves no threads and cannot exceed `workers`. |
| An omitted concurrency entry | That type may use all remaining worker capacity. |
| `stuck-after: 20m` | Common recovery threshold measured from claim time. A once-per-minute sweep retries old RUNNING tasks or marks them DEAD when attempts are exhausted. It does not stop the old thread. |
| `shutdown-wait: 60s` | Executor grace period during application shutdown, not a handler timeout. |

For example, separate heavy reports from lighter email work with:

```yaml
tasks:
  workers: 16
  concurrency:
    REPORT_GENERATION: 2
    EMAIL_SEND: 10
    REPORT_SHARE: 4
```

This allows up to 2 reports, 10 ready-notification emails and 4 sharing emails at once, subject to
the shared 16-worker ceiling. These are ceilings, not reserved slots or guaranteed throughput:
if only `EMAIL_SEND` tasks are pending, its limit of 10 leaves 6 workers idle. With the default
configuration (only `REPORT_GENERATION: 2`), emails may instead use all 16 workers when no other
tasks are running, or the remaining 14 while 2 reports run. `EMAIL_SEND` and `REPORT_SHARE` have
independent limits, not a combined mail-provider quota. Concurrency limits simultaneous work;
it does not limit emails per second or minute.

Three instances multiply the capacity: up to 6 reports and 48 total tasks. ShedLock serializes
the short dispatch passes; handlers can continue concurrently on different instances.

## Tests

`./mvnw test` (needs Docker) runs against real PostgreSQL. It covers:
enqueue → SUCCEEDED (including the follow-up email task), retry with backoff, non-retryable and
last-attempt failures (DEAD), the per-type limit (never more than 2 reports at once), idempotency
keys, rollback together with the caller, atomic report/notification persistence, ShedLock exclusion,
conditional claim races, rejected submissions, stale completion rejection, bounded stuck-task
recovery, and the rate limit. A full report quota is also tested alongside email processing.
