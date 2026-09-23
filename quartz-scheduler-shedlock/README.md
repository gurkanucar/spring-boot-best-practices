# Scheduler + ShedLock Example

A teaching catalog for running scheduled jobs safely across multiple application
instances, using Spring's `@Scheduled` and [ShedLock](https://github.com/lukas-krecan/ShedLock) —
plus a global scheduled-task error handler and an `@Async` error-handling example.

> **Note on the module name:** this module started out as a Quartz Scheduler + ShedLock
> example (hence `quartz-scheduler-shedlock`). Mid-build it became clear the simpler,
> far more common integration is Spring's own `@Scheduled` + ShedLock — no separate job
> classes, no `JobDetail`/`Trigger` registration, no reflection-instantiation gotchas to
> work around. That's what's actually implemented here; the folder/package name is a
> leftover of the original working title.

Spring Boot 4.1.1 · Java 25 · ShedLock 6.6.0 · H2

## The problem this solves

Deploy this application on three instances and, without ShedLock, all three would fire
`EveryMinuteTask` every minute — each instance running the business logic independently.
For most scheduled jobs (billing runs, report generation, cleanup tasks) that's wrong:
the work should happen exactly once per fire, not once per instance.

[ShedLock](https://github.com/lukas-krecan/ShedLock) fixes this: every instance's
scheduler still fires on schedule, but `@SchedulerLock` ensures only one instance's call
to the annotated method actually runs its body. The others' calls silently return
without executing anything — no exception, no retry, no duplicate side effect.

## Running

```bash
cd quartz-scheduler-shedlock
./mvnw spring-boot:run
```

The app starts on the default port (`8080`). H2 console is at `/h2-console` (JDBC URL
`jdbc:h2:mem:quartzshedlockdb`, user `sa`, empty password). `net.javacrumbs.shedlock` is
logged at `DEBUG`, so you'll see lines like `Locked 'everyMinuteTask', lock will be held
at most until ...` or `Not executing 'everyMinuteTask'. It's locked.` on every fire.

## Task catalog

| Task | Schedule | What it demonstrates |
|---|---|---|
| `EveryMinuteTask` | cron `0 * * * * ?` | Basic `@SchedulerLock`; fails ~30% of the time on purpose, so you can watch the global error handler catch it |
| `EveryFiveMinutesTask` | cron `0 */5 * * * ?` | Plain, always-successful locked task |
| `HourlyTask` | cron `0 0 * * * ?` | Synchronous locked work, followed by a fire-and-forget `@Async` side task — see "Async work and lock scope" below |
| `Daily3AmTask` | cron `0 0 3 * * ?` | Typical overnight batch job pattern, generous `lockAtMostFor` |
| `Every100SecondsTask` | `@Scheduled(fixedRate = 100_000)` | A cadence that doesn't map cleanly onto a cron field; also the one task whose `lockAtLeastFor` has to be tuned close to its full period — see "Deliberate simplifications" below |

Every task lives in `src/main/java/.../task/` as a single `@Component` method carrying
both `@Scheduled` (Spring's own trigger) and `@SchedulerLock` (ShedLock's cross-instance
guard) — no adapter classes, no separate registration step.

## The `@SchedulerLock` + `@Scheduled` pattern used here

```java
@Scheduled(cron = "0 * * * * ?")
@SchedulerLock(name = "everyMinuteTask", lockAtMostFor = "PT55S", lockAtLeastFor = "PT10S")
public void run() { ... }
```

Both annotations sit on the same method because Spring always invokes `@Scheduled`
methods through the bean's proxy — never via self-invocation — so `@SchedulerLock`'s
AOP advice is guaranteed to apply. (The general Spring AOP caveat still holds elsewhere
in this codebase: if you ever call a `@SchedulerLock` method from another method *on the
same bean*, the proxy is bypassed and the lock silently does nothing. That's not
specific to scheduling — the same rule applies to `@Transactional`, `@Cacheable`, etc.)

`lockAtMostFor` is a dead-man's-switch, tuned per task to comfortably exceed how long
the task could realistically run but stay under the next fire — it only matters if a
node dies mid-execution and never releases the lock itself. `lockAtLeastFor` guards
against clock drift between nodes (or a `fixedRate` task finishing very quickly)
causing a second, near-simultaneous execution right after the first one returns.

## Error handling

Two independent mechanisms, both logging (not storing) failures — nothing is queryable
over HTTP. Note that Spring already logs every scheduled/async failure by default even
without either of these (`TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER` for `@Scheduled`,
`SimpleAsyncUncaughtExceptionHandler` for `@Async void`) — the point of building these
explicitly is to have one intentional place to control the format and add your own
policy (a metric, an alert, a specific message), not to prevent a failure that would
otherwise vanish silently:

- **`SchedulingConfig`** installs a custom `TaskScheduler` whose `ErrorHandler` logs
  every uncaught exception from any `@Scheduled` method at `ERROR`, with the full stack
  trace. This is registered once, globally — no individual task handles its own error
  logging.
- **`AsyncConfig`** installs a custom `AsyncUncaughtExceptionHandler` for the
  `@Async void` path (see below), naming the failing task/method explicitly in the log
  line — this exists because a `void`-returning `@Async` method literally cannot
  propagate an exception to any caller, so the *default* handler is the only thing that
  would otherwise see it.

Watch `EveryMinuteTask` for a few minutes — with its ~30% failure rate you'll see
`A @Scheduled task failed` ERROR log lines appear within a couple of fires.

## Async work and lock scope

`HourlyTask` does its main work synchronously (covered by the lock), then calls
`HourlyNotificationTask.send(...)`, an `@Async` fire-and-forget method, **after** the
locked method's own work is done. This is deliberate, and worth understanding:

- `@SchedulerLock` only holds the lock for as long as the annotated method's call stack
  is executing. Calling an `@Async void` method returns immediately — so the lock is
  released right after, while the async work is potentially still running on a separate
  thread.
- That's fine here: sending a notification is a best-effort, idempotent side effect. If
  two instances' clocks drifted enough that both slipped past `lockAtLeastFor` and each
  fired the async step once, the worst case is a duplicate notification, not corrupted
  state.
- It would **not** be fine for work that must never run twice (e.g. charging a payment,
  sending a unique invoice number). For that, either block on the async call's
  `CompletableFuture` from inside the locked method (so the lock covers the whole
  operation), or set `lockAtLeastFor` to comfortably exceed the async work's expected
  duration.

## Proving multi-instance safety for real

The default `application.yaml` uses in-memory H2, which only one JVM can see — fine for
normal runs and the test suite, but it can't demonstrate two instances contending for
the same lock. The `cluster` profile (`application-cluster.yaml`) points at H2 running
in **TCP server mode** instead, so two separate application processes can share one
database.

1. Start an H2 TCP server (uses the `h2` jar already on this module's classpath).
   `-ifNotExists` is required — since H2 1.4.198, a TCP server refuses to create a
   database for a remote client unless started with this flag, so without it every
   client connection below fails immediately:

   ```bash
   ./mvnw dependency:build-classpath -Dmdep.outputFile=cp.txt
   java -cp "$(cat cp.txt)" org.h2.tools.Server -tcp -tcpPort 9092 -baseDir ./data -ifNotExists
   ```

2. In two separate terminals, start two instances on different ports:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=cluster -Dspring-boot.run.jvmArguments=-Dserver.port=8081
   ```

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=cluster -Dspring-boot.run.jvmArguments=-Dserver.port=8082
   ```

3. Watch both consoles. Every minute, exactly **one** instance logs
   `EveryMinuteTask running on thread ...` (plus the `DEBUG` line showing it obtained the
   `everyMinuteTask` lock); the other logs a ShedLock `DEBUG` line showing the lock was
   already held, and nothing from `EveryMinuteTask` itself — proof that the business
   logic really did run exactly once across both instances, even though both instances'
   `@Scheduled` trigger fired.

## Using a different database

`shedlock-provider-jdbc-template` (used in this project) works against any JDBC
datasource — only the driver dependency and the `shedlock` table's DDL change.
`shedlock-provider-mongo` is a different provider entirely.

| Database | Provider artifact | Extra dependency | Notes |
|---|---|---|---|
| H2 (this project) | `shedlock-provider-jdbc-template` | `com.h2database:h2` | Demo default |
| PostgreSQL | `shedlock-provider-jdbc-template` | `org.postgresql:postgresql` | Same DDL as `schema.sql` works unchanged |
| MySQL | `shedlock-provider-jdbc-template` | `com.mysql:mysql-connector-j` | Add `ENGINE=InnoDB` to the `CREATE TABLE` |
| SQL Server | `shedlock-provider-jdbc-template` | `com.microsoft.sqlserver:mssql-jdbc` | Same generic JDBC provider |
| Oracle | `shedlock-provider-jdbc-template` | `com.oracle.database.jdbc:ojdbc11` | Use `VARCHAR2`/`TIMESTAMP(3)` in the DDL instead of `VARCHAR`/`TIMESTAMP(3)` |
| MongoDB | `shedlock-provider-mongo` | `org.mongodb:mongodb-driver-sync` | No DDL — a `MongoLockProvider` bean and a `shedLock` collection instead |

ShedLock also ships providers for Redis (`shedlock-provider-redis-spring`), ZooKeeper,
Consul, DynamoDB, and etcd — out of scope for this demo, but worth knowing about if
you're not on a relational database.

## Tests

```bash
./mvnw test
```

No mocking framework is used anywhere in this module — every test runs against a real
Spring context, a real H2 database, and real `@Scheduled`/`@SchedulerLock` wiring.

- `EveryMinuteTaskTest`, `HourlyNotificationTaskTest` — deterministic success/failure
  branches via an injectable random seam, no Spring context needed
- `SchedulerLockConcurrencyTest` — two concurrent calls to the same locked method; only
  one actually runs (the core guarantee this whole project is about)
- `*TaskScheduleTest` (one per task) — reflection checks that each method's `@Scheduled`
  cron/`fixedRate` value is exactly what the catalog table above claims
- `AsyncUncaughtExceptionHandlerTest` — the real handler bean logs a failed async task,
  captured via a real Logback appender (`LogCapture`), not a mock
- `ScheduledTaskCatalogTest` — all five tasks assemble into one Spring context without
  conflict
- `ShedLockConfigTest` — lock acquire/release against the real `shedlock` table

## Deliberate simplifications

- **Spring's `@Scheduled`, not Quartz.** This is ShedLock's most common, best-documented
  integration point. Quartz + ShedLock is also possible but needs a `JobDetail`/`Trigger`
  adapter layer and a custom `JobFactory` to work around Quartz instantiating `Job`
  objects via reflection (bypassing Spring's own bean creation) — real extra complexity
  that buys nothing here, since this project never needed Quartz's own clustering or
  persistent job store.
- **Logging only, no history/metrics storage.** Both error handlers log and stop there —
  nothing is queryable over HTTP. A production system would likely ship these logs to a
  metrics/alerting backend instead of building a bespoke in-memory store.
- **`fixedRate`, not `fixedDelay`, for `Every100SecondsTask`.** `fixedRate` schedules the
  next run from the *start* of the previous one; `fixedDelay` would wait for completion
  first. Neither choice affects cross-instance dedupe on its own — see the next point.
- **Interval schedules need a longer `lockAtLeastFor` than cron ones.** A cron trigger
  (`0 * * * * ?` etc.) fires at the same wall-clock instant on every instance, so a short
  `lockAtLeastFor` is enough to cover clock drift. `fixedRate`/`fixedDelay` count from
  each JVM's own startup time instead, so two instances started minutes apart fire at
  unrelated offsets — `Every100SecondsTask` sets `lockAtLeastFor = "PT90S"` (90s of its
  100s period) specifically so the lock stays held long enough to also catch a drifted
  instance's independent fire, not just a near-simultaneous one.
