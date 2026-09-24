# Spring Boot: Background Report Export with @Scheduled + ShedLock

A user clicks **Export** in the UI. The API answers at once with `202 Accepted`; the report is
generated in the background, and a "your report is ready" email follows. No JobRunr, Quartz or
Spring Batch: just a `background_task` table, `@Scheduled`, and ShedLock.

```text
POST /api/reports ── report_request + background_task (one transaction)
                                         │
   TaskPoller (@Scheduled + ShedLock) ── claims due tasks ── worker pool ── TaskHandler
                                                                   │
                                        ReportGenerationHandler ── report + EMAIL_SEND task
```

Spring Boot 4.1.1, Java 25, PostgreSQL 17, Flyway, ShedLock and Testcontainers.

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
```

**Rate limit.** A user can request at most **3 reports in 10 minutes** (sliding window). The 4th
request gets `429 Too Many Requests` with a `Retry-After` header in seconds. A per-user
PostgreSQL advisory lock makes the check safe against double clicks. Configure it under
`reports.rate-limit`.

## How it works

- **Enqueue** — `TaskService.enqueue(type, payload, idempotencyKey)` inserts a PENDING row in the
  caller's transaction: the task exists exactly when the business data was saved. A repeated
  idempotency key returns the existing task (`ON CONFLICT DO NOTHING`).
- **Poll** — every 2s, `TaskPoller` claims as many due tasks as there are free workers
  (`FOR UPDATE SKIP LOCKED`), marks them RUNNING, and submits them to the worker pool. It never
  runs a task itself.
- **Per-type limits** — e.g. at most 2 report generations at once. If a type is at its limit, the
  task goes back to PENDING for the next poll, without using an attempt; no worker waits.
- **Result** — the handler runs in its own transaction; the status update is a separate, short one.
  Success → SUCCEEDED. Failure → PENDING again after 30s, 2m, 8m, 32m, 1h... until `max_attempts`
  (5), then DEAD. `NonRetryableTaskException` → DEAD at once.
- **Recovery** — every minute, tasks RUNNING for more than 20 minutes (a crashed instance) go back
  to PENDING. `stuck-after` must be longer than your slowest task.
- **Shutdown** — the poller stops claiming and waits up to 60s for running tasks; anything left
  is picked up by recovery.

Handlers must be **idempotent**: a task can run twice (crash before SUCCEEDED, recovery).
The report handler skips requests that already have a report; the email handler should pass an
idempotency key to the mail provider.

## Adding a new task type

1. Add a constant to `TaskType`, e.g. `INVOICE_PDF`.
2. Write a handler bean:

   ```java
   @Component
   public class InvoicePdfHandler implements TaskHandler {
       public TaskType type() { return TaskType.INVOICE_PDF; }

       public void handle(JsonNode payload) {
           UUID invoiceId = Payloads.requireUuid(payload, "invoiceId");
           // idempotent: return early if the PDF already exists
       }
   }
   ```

3. Enqueue it where the work arises, inside that transaction:

   ```java
   taskService.enqueue(TaskType.INVOICE_PDF, Map.of("invoiceId", id.toString()), "invoice-pdf:" + id);
   ```

4. Optionally limit it: `tasks.concurrency.INVOICE_PDF: 4` (default: all workers).

Keep payloads small (ids only); the handler loads what it needs.

## Configuration

```yaml
tasks:
  poll-interval: 2s
  workers: 16
  concurrency:
    REPORT_GENERATION: 2
    EMAIL_SEND: 30
  stuck-after: 20m
  shutdown-wait: 60s
reports:
  rate-limit:
    max-requests: 3
    window: 10m
```

## Tests

`./mvnw test` (needs Docker) runs against real PostgreSQL. It covers:
enqueue → SUCCEEDED (including the follow-up email task), retry with backoff, non-retryable and
last-attempt failures (DEAD), the per-type limit (never more than 2 reports at once), idempotency
keys, rollback together with the caller, stuck task recovery, and the rate limit.
