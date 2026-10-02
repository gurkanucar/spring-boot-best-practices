# open-observe

OpenTelemetry telemetry (traces, metrics, logs) from a Spring Boot 4 app to
[OpenObserve](https://openobserve.ai) over OTLP/HTTP. No agent: everything is the
`spring-boot-starter-opentelemetry` starter plus configuration.

## Run

```bash
docker compose up -d        # OpenObserve :5080, Postgres :5433, Mailpit :8025
./mvnw spring-boot:run      # app on :8080, profile "dev"
```

UI: http://localhost:5080 (`root@example.com` / `Complexpass#123`) → stream `springboot_observe`.

## Generate some telemetry

```bash
curl -XPOST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{"customer":"ali","amount":12.5}'
curl localhost:8080/api/orders/1
curl 'localhost:8080/api/orders/slow?millis=1500'   # long span
curl localhost:8080/api/orders/fail                 # 500, ERROR log, failed span
```

## What goes where

| Signal  | Source                                        | Config                                          |
|---------|-----------------------------------------------|-------------------------------------------------|
| Traces  | Micrometer Tracing → OTel bridge, `@Observed` | `management.opentelemetry.tracing.export.otlp`  |
| Metrics | Micrometer `OtlpMeterRegistry`, every 15 s    | `management.otlp.metrics.export`                |
| Logs    | Logback `OpenTelemetryAppender`               | `management.opentelemetry.logging.export.otlp`  |

- Logs carry `trace_id` / `span_id`, so a trace in OpenObserve links to its log lines (including Logbook's
  request/response dumps).
- `@Observed` (needs `spring-boot-starter-aspectj`) adds a span and a timer per service method.
- The `OpenTelemetryAppender` is declared in `logback-spring.xml` and attached to the SDK in
  `OpenTelemetryLogAppenderConfig`; Boot does not do that part for you.
- `opentelemetry-logback-appender-1.0` is not managed by Boot. Keep its version aligned with the OTel API Boot
  ships (`2.28.0-alpha` ↔ API 1.62); a newer one fails with `NoClassDefFoundError: ExtendedAttributeKey`.
- **Stream name is not derived from the project name.** It is whatever the `stream-name` header says
  (`OPENOBSERVE_STREAM`, default `springboot_observe`) and applies to traces and logs. The service name
  (`spring.application.name`) is only a field inside the stream (`service_name`). Metrics ignore the header
  and get one stream per metric name (`order_service_count`, `http_server_requests_bucket`, ...). For several
  apps, give each its own `OPENOBSERVE_STREAM`, or share one stream and filter on `service_name`.
- JDBC spans come from `datasource-micrometer-spring-boot` (Boot does not instrument JDBC).

## Dashboard and alerts

Both live in the repo and are imported through the API; re-running replaces what is there.

```bash
./scripts/traffic.sh                                  # generate data first
./scripts/import-dashboard.sh                         # dashboards/observability.json
./scripts/import-alerts.sh [stream] [alert-email]     # alerts/alerts.json (mails land in Mailpit :8025)
```

- Dashboard: 15 panels (resources, traffic, latency, errors, logs, slowest queries, traces). The stream is
  substituted for `__STREAM__`, the `@Observed` timer prefix (default `order_service`, 2nd argument) for
  `__SERVICE_METRIC__`.
- Alerts: `error_log_spike`, `unhandled_exception_logged` (real-time, matches the `Unhandled exception` log
  from `GlobalExceptionHandler`), `slow_http_request`, `no_traces_received`.
- `./scripts/traffic.sh` hits every endpoint, including 400/404/500 and a slow call.

## Profiles

| Profile | Sampling | Logbook                    | Extras                 |
|---------|----------|----------------------------|------------------------|
| `dev`   | 100 %    | every request, TRACE       | Swagger UI on          |
| `prod`  | 10 %     | only responses with 5xx    | Swagger off, health hidden |

Override the target with `OPENOBSERVE_AUTH_TOKEN` (full `Basic ...` value) and `OPENOBSERVE_STREAM`.
Tests use H2 and disable exporting (`src/test/resources/application.yaml`).
