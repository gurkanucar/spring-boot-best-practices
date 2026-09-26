# Background report and email jobs with JobRunr OSS

Spring Boot 4.1.1, Java 25, JobRunr **8.7.0 OSS**, PostgreSQL 17.

A user requests a report and gets `202 Accepted`. A JobRunr job generates the report, then a
separate job emails the owner. A finished report can be shared with another recipient. JobRunr
owns job storage, workers, retries and crash recovery; there is no custom worker or task table.

Report content is a demo string and `ReportMailer` only logs a `DEMO` line, so no mail account
is needed.

## Run

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # Windows: .\mvnw.cmd
```

API `http://localhost:8100`, dashboard `http://localhost:8001` (only with the `local` profile),
PostgreSQL `localhost:5435`, database `reports_jobrunr`, user/password `reports`.

The schema was simplified by editing `V1__reports.sql` in place. If you ran an older version,
reset the database once: `docker compose down -v`.

```bash
curl -i -X POST http://localhost:8100/api/reports \
  -H "Content-Type: application/json" \
  -d '{"reportType":"MONTHLY_SALES","requestedBy":"alice@example.com"}'
# 202 {"reportRequestId":1,"jobId":"...","reportUrl":"/api/reports/1"}

curl http://localhost:8100/api/reports/1
# {"ready":true,"content":"MONTHLY_SALES report for alice@example.com",...}

curl -i -X POST http://localhost:8100/api/reports/1/share \
  -H "Content-Type: application/json" \
  -d '{"recipientEmail":"bob@example.com"}'
# 202 {"jobId":"..."}  same report + same recipient returns the same jobId
# 404 unknown request, 409 report not ready yet
```

## Flow

```text
POST /api/reports
  └─ save report_request → enqueue generate(requestId)          job id = jobId("generate:<id>")
       └─ ReportJobs.generate: save report (if missing)
            └─ enqueue sendReadyEmail(reportId)                  job id = jobId("ready:<id>")
                 └─ ReportMailer.send(owner)

POST /api/reports/{id}/share
  └─ enqueue share(reportId, recipient)                          job id = jobId("share:<report>:<recipient>")
       └─ ReportMailer.send(recipient)
```

## Files

```text
ReportGenerationLightTaskWithJobrunrApplication
report/
  ReportController                    endpoints, request bodies
  ReportService                       request(), get(), share(), response records
  ReportRequest, Report               entities
  ReportRequestRepository, ReportRepository
  ReportJobs                          @Job generate / sendReadyEmail / share, jobId(key)
  ReportMailer                        demo mail adapter
```

## Guarantees

- **Survives restarts** - jobs are stored in PostgreSQL by JobRunr.
- **Retries** - a failing job is retried with exponential backoff (`default-number-of-retries: 4`,
  so up to 5 attempts), then stays `FAILED` in the dashboard for a human.
- **No duplicate jobs** - job ids are `UUID.nameUUIDFromBytes(key)`. JobRunr does not create a
  second job with an existing id, so enqueueing the same key again is harmless. (After JobRunr
  permanently deletes old job history, the same key can create a new job.)
- **One report per request** - a unique constraint on `report.report_request_id`; a concurrent
  duplicate insert is caught and the existing report is used.
- **A saved report always gets its email** - `generate` enqueues the email job even when the report
  already exists. If that enqueue fails, JobRunr retries `generate`, which skips generation and
  enqueues again.
- The mailer gets the job id as an idempotency key. A job can still run twice (crash mid-send), so
  a real mail provider should deduplicate on that key.

## Deliberately left out

- **Crash window between request and job.** JobRunr OSS `enqueue` does not join the Spring
  transaction. `request()` commits the `report_request` row, then enqueues. A crash between the two
  leaves a request with no job. In production, close this with a transactional outbox (store the
  job intent in the same transaction and hand it off later) or with
  [JobRunr Pro transactions](https://www.jobrunr.io/en/documentation/pro/transactions/).
- **Rate limiting and input validation** - no per-user limit, no `@Valid`; add them for real use.
- **Per-type concurrency** - the 4 workers are shared by all job types. OSS has no
  "reports=2, emails=10" setting; rate limiters and server tags are JobRunr Pro features.
- **Authentication** - `requestedBy` is demo input. Protect the dashboard before enabling it
  outside your machine.

## Tests

```bash
./mvnw test
```

Docker is required (Testcontainers PostgreSQL, real JobRunr workers). `ReportFlowTest` covers the
flow, an email retry after one mail failure, and share (409 before ready, one job per recipient).
