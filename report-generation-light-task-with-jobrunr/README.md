# Background report and email jobs with JobRunr OSS

Spring Boot 4.1.1, Java 25, JobRunr **8.7.0 OSS**, PostgreSQL 17.

1. `POST /api/reports` - the request is saved and the user gets `202 Accepted`.
2. A background job generates the report.
3. A second background job sends the "report ready" email.

The client polls `GET /api/reports/{id}` until `ready` is true. JobRunr owns job storage, workers,
retries and crash recovery; there is no custom worker or task table.

Report content is a demo string and `ReportEmailSender` only logs a `DEMO` line, so no mail
account is needed.

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
```

## Flow

```text
1. POST /api/reports → ReportService.requestReport()
     save report_request, scheduleReportGeneration(id)       job id from "generate-report:<id>"
2. job ReportJobs.generateReport(id) → ReportGenerator.generateReport(id)
     save report (if missing), scheduleReportReadyEmail(id)  job id from "report-ready-email:<id>"
3. job ReportJobs.sendReportReadyEmail(id) → ReportEmailSender.sendReportReadyEmail(id)

GET /api/reports/{id} → ReportService.getReport()            poll until ready
```

## Files

```text
ReportGenerationLightTaskWithJobrunrApplication
report/                               plain Spring, no JobRunr imports
  ReportController                    POST /api/reports, GET /api/reports/{id}
  ReportService                       requestReport(), getReport(), response records
  ReportGenerator                     generateReport(): idempotent, one report per request
  ReportEmailSender                   sendReportReadyEmail(): demo, logs with an idempotency key
  ReportRequest, Report               entities
  ReportRequestRepository, ReportRepository
jobrunr/                              everything JobRunr
  ReportJobScheduler                  the only JobScheduler caller, fixed job ids
  ReportJobs                          @Job methods that only delegate to report/
```

The business code in `report/` is plain Spring and can be read and tested without JobRunr.
Replacing JobRunr only touches `jobrunr/`. `ReportService` just asks `ReportJobScheduler` to
schedule the first job.

## Guarantees

- **Survives restarts** - jobs are stored in PostgreSQL by JobRunr.
- **Retries** - a failing job is retried with exponential backoff (`default-number-of-retries: 4`,
  so up to 5 attempts), then stays `FAILED` in the dashboard for a human.
- **No duplicate jobs** - job ids are `UUID.nameUUIDFromBytes(key)`. JobRunr does not create a
  second job with an existing id, so scheduling the same key again is a no-op. (After JobRunr
  permanently deletes old job history, the same key can create a new job.)
- **One report per request** - a unique constraint on `report.report_request_id`; a concurrent
  duplicate insert is caught and the existing report is used.
- **A saved report always gets its email** - `generateReport` schedules the email job even when the
  report already exists. If that fails, JobRunr retries `generateReport`, which skips generation
  and schedules again.
- `ReportEmailSender` uses `report-ready-email:<id>` as an idempotency key. A job can still run
  twice (crash mid-send), so a real mail provider should deduplicate on that key.

## Deliberately left out

- **Crash window between request and job.** JobRunr OSS `enqueue` does not join the Spring
  transaction. `requestReport()` commits the `report_request` row, then schedules the job. A crash
  between the two leaves a request with no job. In production, close this with a transactional
  outbox (store the job intent in the same transaction and hand it off later) or with
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
flow (report generated, owner emailed once) and an email retry after one mail failure that does
not regenerate the report.
