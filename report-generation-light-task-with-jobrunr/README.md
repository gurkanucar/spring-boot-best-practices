# Background report and email jobs with JobRunr OSS

Spring Boot 4.1.1, Java 25, JobRunr **8.7.0 OSS**, PostgreSQL 17.

1. `POST /api/reports` - a `PENDING` report row is saved and the user gets `202 Accepted`.
2. A background job generates the content and marks the report `READY`.
3. A second background job sends the "report ready" email.

The client polls `GET /api/reports/{reportId}` until `status` is `READY`. JobRunr owns job storage, workers,
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

The schema was changed by editing `V1__reports.sql` in place (one `report` table, no
`report_request`). If you ran an older version, reset the database once: `docker compose down -v`.

```bash
curl -i -X POST http://localhost:8100/api/reports \
  -H "Content-Type: application/json" \
  -d '{"reportType":"MONTHLY_SALES","requestedBy":"alice@example.com"}'
# 202 {"reportId":1,"jobId":"...","reportUrl":"/api/reports/1"}

curl http://localhost:8100/api/reports/1
# {"reportId":1,"status":"PENDING","content":null,...}   while the job runs
# {"reportId":1,"status":"READY","content":"MONTHLY_SALES report for alice@example.com",...}
```

## Flow

```text
1. POST /api/reports → ReportService.requestReport()
     save report (PENDING), scheduleReportGeneration(reportId)   job id from "generate-report:<reportId>"
2. job ReportJobs.generateReport(reportId) → ReportGenerator.generateReport(reportId)
     mark READY (if still PENDING), scheduleReportReadyEmail(reportId)
                                                              job id from "report-ready-email:<reportId>"
3. job ReportJobs.sendReportReadyEmail(reportId) → ReportEmailSender.sendReportReadyEmail(reportId)

GET /api/reports/{reportId} → ReportService.getReport()      poll until status is READY
```

## Files

```text
ReportGenerationLightTaskWithJobrunrApplication
report/                               plain Spring, no JobRunr imports
  ReportController                    POST /api/reports, GET /api/reports/{reportId}
  ReportService                       requestReport(), getReport(), response records
  ReportGenerator                     generateReport(): idempotent, skips a READY report
  ReportEmailSender                   sendReportReadyEmail(): demo, logs with an idempotency key
  Report                              entity: table report, status PENDING → READY
  ReportRepository
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
- **One report per request** - the request and the report are the same `report` row. A retried
  generate job sees `READY` and skips; two concurrent runs would write the same content, so the
  plain save is harmless.
- **A ready report always gets its email** - `generateReport` schedules the email job even when the
  report is already `READY`. If that fails, JobRunr retries `generateReport`, which skips generation
  and schedules again.
- `ReportEmailSender` uses `report-ready-email:<reportId>` as an idempotency key. A job can still run
  twice (crash mid-send), so a real mail provider should deduplicate on that key.

## Deliberately left out

- **Crash window between request and job.** JobRunr OSS `enqueue` does not join the Spring
  transaction. `requestReport()` commits the `PENDING` report row, then schedules the job. A crash
  between the two leaves a `PENDING` report with no job. In production, close this with a transactional
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
