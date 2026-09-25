# Background report and email tasks with JobRunr OSS

Spring Boot 4.1.1, Java 25, JobRunr **8.7.0 OSS**, and PostgreSQL 17.

A user requests a report and the API returns `202 Accepted`. JobRunr generates the report in the
background, then a separate job sends the ready notification. A finished report can also be shared
with another recipient. JobRunr owns job storage, workers, retries, recovery after a worker crash,
and the dashboard. There is no custom `TaskRunner`, `TaskPoller`, semaphore, `synchronized` block,
ShedLock, or task state machine.

**This is a working example:** report content is text, and the mail adapter only writes a `DEMO`
log entry. Add real XLSX generation and object storage integration in
`ReportGenerationHandler.handle`, and the mail provider integration in `ReportMailer.send`.
The example runs without a mail account.

## Run

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows, use `.\mvnw.cmd` instead of `./mvnw`.

- API: `http://localhost:8100`
- Dashboard: `http://localhost:8001` (enabled by the `local` profile)
- PostgreSQL: `localhost:5435`, database `reports_jobrunr`, username/password `reports`

This example uses a separate port and database from the scheduler example. The Docker volume
persists data. The dashboard is disabled in the default profile; protect access before enabling it
in production.

```bash
curl -i -X POST http://localhost:8100/api/reports \
  -H "Content-Type: application/json" \
  -d '{"reportType":"MONTHLY_SALES","requestedBy":"alice@example.com"}'
# 202: {"reportRequestId":1,"jobId":"...","reportUrl":"/api/reports/1"}

curl http://localhost:8100/api/reports/1
# Once ready=true, content is available; notification runs as a separate job.

curl http://localhost:8100/api/jobs/JOB_ID
# WAITING_DISPATCH, ENQUEUED, PROCESSING, SCHEDULED, SUCCEEDED, FAILED, ...

curl -i -X POST http://localhost:8100/api/reports/1/share \
  -H "Content-Type: application/json" \
  -d '{"recipientEmail":"bob@example.com"}'
# 202: {"jobId":"..."}; repeating the same report/recipient returns the same jobId.
```

A user may request up to 3 reports in 10 minutes. Excess requests return `429` with a
`Retry-After` header. A missing report returns `404`, sharing one that is not ready returns `409`,
and invalid input returns `400`. The `taskId` in the scheduler example corresponds to `jobId` here.

## Flow and transaction boundaries

```text
POST /reports
  └─ one DB transaction: report_request + task_submission
       └─ after commit: TaskDispatcher → JobRunr.enqueue(jobId, handler.handle)
            └─ ReportGenerationHandler.handle: generate the report outside a long transaction
                 └─ one short transaction: report + ready-email submission
                      └─ TaskDispatcher → EmailSendHandler.handle → ReportMailer

POST /reports/{id}/share
  └─ task_submission → TaskDispatcher → ReportShareHandler.handle → ReportMailer
```

## Three task examples and adding another

| Handler | Payload | Work |
|---|---|---|
| `ReportGenerationHandler` | `Long reportRequestId` | Generates the report and commits its result with the ready-email intent. |
| `EmailSendHandler` | `UUID reportId` | Notifies the report owner. |
| `ReportShareHandler` | `UUID reportId, String recipientEmail` | Shares a finished report with a different recipient. |

Handlers are ordinary Spring beans. The job method uses JobRunr's `@Job` annotation.
A single value uses `Long` or `UUID`; multiple values use a small `Payload` record.
One switch in `TaskDispatcher` maps each stored type to its JobRunr method.
JobRunr manages workers and retries.

To add a task, define a Spring handler:

```java
@Component
public class CleanupHandler {
    @Job(name = "Clean archive %0")
    public void handle(Long archiveId) {
        // Idempotent work. Let JobRunr handle failures.
    }
}
```

Add its type to `TaskType` and its JobRunr call to the switch in `TaskDispatcher`.
Within the business transaction, call `tasks.submit(TaskType.CLEANUP, id, "cleanup:" + id)`.
After the service transaction completes, `dispatcher.tryDispatch(jobId)` hands it off promptly.
The recurring job will hand it off if this immediate call is skipped or fails. The same
idempotency key must always represent the same task. The business layer must serialize
concurrent creation with the same key; the share example locks the report-request row.

Pass small values such as IDs and recipient addresses to JobRunr, not entities or files.
Throw transient errors so JobRunr can retry. Missing permanent business data can throw
`new JobRunrException(message, true)` to fail without retries. An email failure retries only
the email job; it does not regenerate the report.

### Why keep a small handoff table?

JobRunr OSS `enqueue` does not join the current Spring business transaction.
[Transaction integration is a Pro feature](https://www.jobrunr.io/en/documentation/pro/transactions/).
Calling `enqueue` inside `@Transactional` therefore does not make the business row and job commit
or roll back together. An `AFTER_COMMIT` callback alone would leave a crash window after commit.

`task_submission` is a small transactional outbox. It stores only **what to hand off, the stable
job ID, and whether handoff was acknowledged**. Attempts, locks, RUNNING/FAILED states, and
error history belong to JobRunr.

1. Business data and its submission are saved in one transaction. Rollback removes both.
2. After the transactional service method returns and releases its connection, the controller
   or job attempts to enqueue. A JobRunr outage leaves the submission pending.
3. A JobRunr `@Recurring` job hands off up to the oldest 100 pending submissions every 30 seconds.
4. After enqueue succeeds, `submitted_at` is written in a separate transaction. If the process
   crashes before that update, handoff repeats with the same UUID; JobRunr does not duplicate
   an existing job with that ID.

Pending submissions survive an application restart and are handed off by the next recurring run.
This assumes the submission rows remain stored and JobRunr storage/workers become available again.
The handoff job shares the worker pool, so 30 seconds is not a strict latency guarantee.

## Concurrency and retry settings

```yaml
jobrunr:
  background-job-server:
    enabled: true
    worker-count: 4
    poll-interval-in-seconds: 5
    interrupt-jobs-await-duration-on-stop: 60s
  jobs:
    default-number-of-retries: 4
tasks:
  dispatch-interval: PT30S
```

| Setting | Meaning |
|---|---|
| `worker-count: 4` | Four concurrent jobs per instance, shared by report, email, and handoff jobs. |
| `poll-interval-in-seconds: 5` | JobRunr's polling interval, not a job timeout. |
| `default-number-of-retries: 4` | Initial attempt plus four retries. JobRunr calculates the backoff. |
| `interrupt-jobs-await-duration-on-stop: 60s` | How long JobRunr waits for jobs during shutdown. |
| `dispatch-interval: PT30S` | How often pending submissions are checked for handoff. |

**JobRunr OSS does not provide a built-in `reports=2, emails=10` per-type concurrency setting.**
All four workers may be occupied by report jobs; separate email jobs do not have reserved capacity.
Three instances can run up to 12 jobs in total. The
[concurrent rate limiter](https://www.jobrunr.io/en/documentation/pro/rate-limiters/) and
server tags for routing jobs to particular servers are Pro features.

The scheduler example's `stuck-after` setting is absent because JobRunr handles recovery after
a worker crash. Configure actual connection, read, and query timeouts for live calls that hang.
JobRunr does not make the business operation idempotent automatically.

## Idempotency, retention, and production integration

- A database unique constraint and a short lock on the report-request row deduplicate report
  results.
- The report and ready-email submission commit together, preventing a saved result from losing
  its notification intent.
- Sharing the same report with the same recipient returns the same submission/job ID. While the
  submission row is retained, clearing JobRunr history does not create another email job.
- The stable JobRunr UUID prevents duplicate enqueues while its job row exists. A job can still
  run again; pass the submission ID to the external mail provider as a stable idempotency key.
  If the provider does not enforce it, a crash or retry can send a duplicate email.
- `submitted_at` means only that JobRunr accepted handoff; it does not mean the email was sent
  or the job completed. `/api/jobs/{id}` reports JobRunr's state. After history is removed, it
  returns `UNAVAILABLE` rather than assuming success. Report `ready` comes from business data.
- This example marks succeeded job history for deletion after 36 hours and permanently removes
  deleted history 72 hours later. Set a separate retention policy for submissions and reports.
  Removing a submission also removes the record that prevents a repeated share request.
- `requestedBy` is demo input. In production, derive identity from authentication and enforce
  user/tenant access on report and job endpoints. Protect the dashboard, which offers job controls.
- Store real report files in shared object storage and put their keys in the database; do not
  pass large files as job arguments. Renaming handler methods or changing the payload schema
  affects compatibility with pending jobs and submissions. A rolling deployment must be able to
  read older records.
- Task payloads are created only by application code. There is no endpoint accepting arbitrary
  task types or JSON from callers.

## Database and classes

JobRunr creates its own tables for the supported database. Flyway manages the business tables.
This example is tested with PostgreSQL. The per-user rate limit uses a PostgreSQL advisory lock;
to use another database, adapt that query, the Flyway DDL, and the JDBC/Flyway drivers.
Application code does not access JobRunr's internal tables or JSON format.

```text
report/  API, business service, entities/repositories, per-user rate limit
tasks/   TaskType: the three task types
         TaskService, TaskSubmission + repository: durable intent in the business transaction
         TaskDispatcher: immediate + recurring handoff and JobRunr method mapping
         TaskController: job state endpoint
         handler/: ReportGenerationHandler, EmailSendHandler, ReportShareHandler
mail/    ReportMailer: demo mail adapter
```

## Tests

```bash
./mvnw test
```

Docker is required. Testcontainers uses real PostgreSQL and real JobRunr workers/storage.
Tests cover API → report → separate email job, transient mail retry, permanent failure,
transaction rollback, recurring handoff after a JobRunr outage, lost acknowledgement after
enqueue, concurrent sharing and rate-limit requests, and idempotency after job-history cleanup.

References: [Spring starter](https://www.jobrunr.io/en/documentation/configuration/spring/),
[retry](https://www.jobrunr.io/en/documentation/background-methods/dealing-with-exceptions/),
and [recurring jobs](https://www.jobrunr.io/en/documentation/background-methods/recurring-jobs/).
The YAML properties target the **8.7.0** version used by this project.
