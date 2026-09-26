# Software Engineering Knowledge Map

A practical map of concepts, patterns, tools and production concerns worth knowing as a
backend / full-stack engineer, with runnable Spring Boot demos in this repository for some of them.

This is **not a list of things I claim to have mastered or implemented**. It is a living map of
topics I want to remember, understand, practice and revisit. Many topics do not have a demo yet;
they stay here on purpose so they are not forgotten.

> The goal is not to memorize frameworks. The goal is to understand the problems they solve, the
> trade-offs they introduce, and how they behave in production.

**How to read this file**

- `- [ ]` a topic to know. Tick it only when I can explain it *and* have used it hands-on.
- `→ demo` links to a runnable example project in this repository.
- Each section ends with the questions worth asking in a real system.

---

## Demo projects in this repository

| Project | Topic |
|---|---|
| [`rest-api-design`](rest-api-design/) | REST API design: resources, status codes, pagination, versioning, ETag, ProblemDetail |
| [`rest-api-integration`](rest-api-integration/) | Calling external APIs with `RestClient`: timeouts, retry, circuit breaker, idempotency |
| [`validation`](validation/) | Request validation with Jakarta Validation |
| [`swagger-openapi-docs`](swagger-openapi-docs/) | OpenAPI / Swagger documentation |
| [`jackson`](jackson/) | JSON serialization with Jackson |
| [`er-one-to-one`](er-one-to-one/), [`er-one-to-many`](er-one-to-many/), [`er-many-to-many`](er-many-to-many/) | JPA entity relationships |
| [`entity-auditing`](entity-auditing/) | Entity auditing and history with Hibernate Envers |
| [`entity-encryption`](entity-encryption/) | Field-level encryption of JPA entity columns |
| [`dto-field-masker`](dto-field-masker/) | Masking sensitive fields in DTOs |
| [`file-operations-io`](file-operations-io/) | Secure file upload / download: Tika magic-byte detection, allow/block lists, size limits, UUID storage, security headers |
| [`caching`](caching/) | Spring Cache with Caffeine (local) and Redis (distributed) |
| [`resillience4j`](resillience4j/) | Retry, circuit breaker, rate limiter, bulkhead, time limiter |
| [`slf4j-logging`](slf4j-logging/) | Logging with SLF4J: levels, MDC, structured logs, masking |
| [`logbook-logging-requests`](logbook-logging-requests/) | HTTP request / response logging with Logbook |
| [`kafka-outbox-pattern-flight-data-updates-example`](kafka-outbox-pattern-flight-data-updates-example/) | Transactional outbox with Kafka |
| [`kafka-inbox-pattern-airports-data-filling-example`](kafka-inbox-pattern-airports-data-filling-example/) | Inbox pattern / idempotent Kafka consumer |
| [`quartz-scheduler-shedlock`](quartz-scheduler-shedlock/) | Scheduled jobs with Quartz and ShedLock on several instances |
| [`report-generation-light-task-with-scheduler`](report-generation-light-task-with-scheduler/) | Background report + email via a database task table, `@Scheduled` and ShedLock |
| [`report-generation-light-task-with-jobrunr`](report-generation-light-task-with-jobrunr/) | The same report + email flow with JobRunr OSS |

---

# Part 1 — Building Blocks

## 1. Core Application Development

- [ ] CRUD operations
- [ ] Layered structure: controller / service / repository
- [ ] Request validation → [demo](validation/)
- [ ] DTO / entity separation
- [ ] Mapping strategies (manual, MapStruct)
- [ ] JSON serialization: naming, dates, null handling, unknown fields → [demo](jackson/)
- [ ] Error handling and consistent error responses (see [Error Handling](#12-error-handling))
- [ ] Configuration management (`application.yaml`, `@ConfigurationProperties`)
- [ ] Environment-specific configuration (profiles, environment variables)
- [ ] Feature flags
- [ ] Time zones, `Instant` vs `LocalDateTime`, storing time in UTC
- [ ] Money and decimal handling (`BigDecimal`, minor units)
- [ ] Admin panels / internal tools (see [Frontend](#20-frontend--admin-panels))

---

## 2. API & Integration Design

### Designing APIs

- [ ] REST principles and resource naming → [demo](rest-api-design/)
- [ ] HTTP methods and status codes
- [ ] Pagination, sorting, filtering (offset vs cursor / keyset)
- [ ] API versioning
- [ ] Error format: `ProblemDetail` (RFC 9457)
- [ ] Conditional requests: ETag, `If-None-Match`, `If-Match`
- [ ] Long-running operations: `202 Accepted` + status URL / polling → [demo](report-generation-light-task-with-scheduler/)
- [ ] Idempotency and idempotency keys
- [ ] Request / response contracts
- [ ] OpenAPI / Swagger → [demo](swagger-openapi-docs/)
- [ ] Backward compatibility
- [ ] Consumer-driven contracts
- [ ] Webhooks
- [ ] API gateways
- [ ] BFF pattern
- [ ] Rate limiting, throttling, quotas (see [Resilience](#9-resilience--fault-tolerance))

### Calling other systems

- [ ] HTTP clients: `RestClient`, `WebClient`, Feign → [demo](rest-api-integration/)
- [ ] Timeouts: connect, read, overall
- [ ] Connection pooling
- [ ] Sync vs async communication
- [ ] REST vs messaging trade-offs
- [ ] Polling vs event-driven integration
- [ ] SOAP / legacy service integration

### Integration failure scenarios

- What if the downstream service is slow?
- What if it returns 500?
- What if the request succeeds but the response is lost?
- What if the same request is sent twice?
- What if only half of a multi-step operation succeeds?

---

## 3. Databases & Persistence

### Fundamentals

- [ ] SQL fundamentals
- [ ] Schema design, normalization / denormalization
- [ ] Transactions, ACID
- [ ] Isolation levels and the anomalies they allow
- [ ] Indexes and query plans (`EXPLAIN ANALYZE`)
- [ ] Database constraints, unique constraints (invariants the database enforces)
- [ ] JSON columns (`jsonb`)
- [ ] Database migrations with Flyway / Liquibase
- [ ] Connection pools (HikariCP sizing)

### JPA / Hibernate

- [ ] Entity relationships: one-to-one, one-to-many, many-to-many → [1:1](er-one-to-one/), [1:N](er-one-to-many/), [N:M](er-many-to-many/)
- [ ] Lazy vs eager loading, fetch joins
- [ ] N+1 queries
- [ ] Persistence context, dirty checking, flush
- [ ] `open-in-view` and why to turn it off
- [ ] Projections for read queries
- [ ] Auditing: created/updated by, history tables → [demo](entity-auditing/)
- [ ] Field-level encryption → [demo](entity-encryption/)
- [ ] Soft delete

### Concurrency in the database

- [ ] Optimistic locking (`@Version`)
- [ ] Pessimistic locking (`SELECT ... FOR UPDATE`)
- [ ] `FOR UPDATE SKIP LOCKED` for work queues → [demo](report-generation-light-task-with-scheduler/)
- [ ] Advisory locks
- [ ] Deadlocks
- [ ] Insert-or-ignore / upsert (`ON CONFLICT`)

### Scale and lifecycle

- [ ] Read replicas
- [ ] Partitioning
- [ ] Archiving / retention
- [ ] Backups and restore tests

### Questions to ask

- Does this operation need a transaction? How long does it keep the connection?
- What happens under concurrent requests?
- Can the same row be updated twice at the same time?
- Is the database constraint enforcing an invariant that application code alone cannot guarantee?

---

## 4. Caching

- [ ] Spring Cache abstraction → [demo](caching/)
- [ ] Local cache (Caffeine)
- [ ] Distributed cache (Redis)
- [ ] Cache-aside
- [ ] Write-through / write-behind concepts
- [ ] TTL and eviction
- [ ] Cache invalidation
- [ ] Cache stampede
- [ ] Cache penetration
- [ ] Hot keys
- [ ] Serialization of cached values and versioning them

> Cache invalidation is part of the data consistency design, not an afterthought.

---

## 5. Messaging & Event-Driven Systems

### Basics

- [ ] Kafka
- [ ] RabbitMQ
- [ ] Producer / consumer model
- [ ] Topics / queues / exchanges
- [ ] Consumer groups
- [ ] Partitioning and ordering guarantees
- [ ] Offset management and consumer lag
- [ ] Backpressure
- [ ] Message schema evolution (Avro / Protobuf, schema registry)

### Delivery guarantees

- [ ] At-most-once, at-least-once, exactly-once (and what "exactly-once" really covers)
- [ ] Duplicate message handling, idempotent consumers
- [ ] Dead-letter queue (DLQ)
- [ ] Retry topics / queues
- [ ] Poison messages

### Patterns

- [ ] Transactional outbox → [demo](kafka-outbox-pattern-flight-data-updates-example/)
- [ ] Inbox pattern → [demo](kafka-inbox-pattern-airports-data-filling-example/)
- [ ] Outbox + CDC (Debezium)
- [ ] Saga: choreography vs orchestration
- [ ] Event sourcing — understand when it is actually justified

---

## 6. Scheduling & Background Processing

### Scheduling

- [ ] Cron expressions
- [ ] Spring `@Scheduled`
- [ ] Quartz (persistent jobs, misfires, clustering) → [demo](quartz-scheduler-shedlock/)
- [ ] ShedLock: one instance runs a scheduled job at a time → [demo](quartz-scheduler-shedlock/)

### Background jobs

- [ ] Moving slow work out of the HTTP request (`202 Accepted` + status)
- [ ] Database-backed task queue: enqueue in the business transaction, claim, retry, recover → [demo](report-generation-light-task-with-scheduler/)
- [ ] Job libraries: JobRunr, Quartz, db-scheduler → [demo](report-generation-light-task-with-jobrunr/)
- [ ] Worker pools and concurrency limits
- [ ] Retry with backoff and a maximum number of attempts, then a "dead" state
- [ ] Recovering stuck / crashed jobs
- [ ] Idempotent job handlers
- [ ] Job status tracking and dashboards
- [ ] Long-running jobs and graceful shutdown
- [ ] Batch processing with Spring Batch (chunks, restartability)

### Questions to ask

- What if two application instances execute the same job?
- What if the process crashes halfway through?
- What if it crashes after the database commit but before the job is enqueued?
- Can the job safely run again?

---

## 7. Files, Reports & Object Storage

- [ ] File upload / download → [demo](file-operations-io/)
- [ ] Streaming uploads / downloads
- [ ] Large CSV / Excel processing (streaming, not loading all into memory)
- [ ] Report generation (PDF / XLSX / CSV) in the background → [scheduler demo](report-generation-light-task-with-scheduler/), [JobRunr demo](report-generation-light-task-with-jobrunr/)
- [ ] AWS S3 / object storage
- [ ] Presigned URLs
- [ ] Multipart upload
- [ ] MIME type validation (magic bytes, Apache Tika) and file size limits → [demo](file-operations-io/)
- [ ] Virus / malware scanning concepts
- [ ] Temporary files
- [ ] Retention policies and lifecycle rules

Avoid loading large files fully into memory when streaming is possible.

---

## 8. Email & Notifications

- [ ] SMTP basics
- [ ] Transactional email
- [ ] HTML templates
- [ ] Async email sending → [demo](report-generation-light-task-with-scheduler/)
- [ ] Retry strategy
- [ ] Duplicate notification prevention (provider idempotency keys)
- [ ] Delivery status, bounces
- [ ] Email provider integration
- [ ] Notification preferences
- [ ] Push / SMS concepts

---

# Part 2 — Cross-Cutting Concerns

## 9. Resilience & Fault Tolerance

- [ ] Timeout strategy
- [ ] Retry, with exponential backoff and jitter
- [ ] Circuit breaker
- [ ] Bulkhead
- [ ] Rate limiter
- [ ] Time limiter
- [ ] Fallback strategies
- [ ] Graceful degradation
- [ ] Load shedding
- [ ] Resilience4j → [demo](resillience4j/)
- [ ] Spring Retry
- [ ] Health checks, readiness / liveness probes

### Important rule

Retries are not automatically safe. Before retrying, think about:

- idempotency
- duplicate writes
- retry storms
- downstream overload
- maximum attempts
- backoff strategy
- which errors are retryable

---

## 10. Authentication & Security

### Identity and access

- [ ] Spring Security
- [ ] Authentication vs authorization
- [ ] Session-based authentication
- [ ] JWT
- [ ] OAuth 2.0
- [ ] OpenID Connect
- [ ] Access tokens / refresh tokens
- [ ] Roles and permissions, RBAC
- [ ] Method-level authorization
- [ ] Multi-tenancy and tenant isolation
- [ ] Password hashing

### Protecting data

- [ ] Secrets management
- [ ] Key rotation
- [ ] Encryption in transit (TLS, mTLS)
- [ ] Encryption at rest, field-level encryption → [demo](entity-encryption/)
- [ ] PII handling
- [ ] Data masking in responses and logs → [demo](dto-field-masker/)
- [ ] Audit logging

### Web vulnerabilities

- [ ] OWASP Top 10
- [ ] CSRF
- [ ] CORS
- [ ] XSS
- [ ] SQL injection
- [ ] SSRF
- [ ] Secure headers → [demo](file-operations-io/)
- [ ] Dependency vulnerabilities

---

## 11. Logging, Monitoring & Observability

### Logging

- [ ] Log levels → [demo](slf4j-logging/)
- [ ] Structured (JSON) logging
- [ ] Correlation ID / request ID with MDC
- [ ] HTTP request / response logging → [demo](logbook-logging-requests/)
- [ ] Avoid logging secrets / PII
- [ ] Centralized logging: ELK / EFK, Loki

### Metrics

- [ ] Micrometer, Actuator
- [ ] Prometheus, Grafana
- [ ] Counters, gauges, histograms
- [ ] Percentiles (p50 / p95 / p99)
- [ ] RED metrics: rate, errors, duration
- [ ] USE method: utilization, saturation, errors
- [ ] SLIs, SLOs and error budgets
- [ ] Alerts and dashboards

### Tracing

- [ ] Distributed tracing
- [ ] OpenTelemetry
- [ ] Trace ID / span ID
- [ ] Context propagation between services, threads and messages
- [ ] Jaeger / Tempo

### Production thinking

- Can I understand why a request failed from logs?
- Can I identify which dependency is slow?
- Can I see error rate and latency trends?
- Can I correlate logs, metrics and traces?

---

## 12. Error Handling

- [ ] Domain exceptions vs technical exceptions
- [ ] Global exception handling (`@RestControllerAdvice`)
- [ ] Error codes
- [ ] User-safe error messages
- [ ] Retryable vs non-retryable failures
- [ ] Partial failure handling
- [ ] Compensating actions
- [ ] Failure isolation

Avoid swallowing exceptions without context. Good errors keep enough information for debugging
without leaking sensitive details.

---

## 13. Concurrency & Distributed Systems

### In one JVM

- [ ] Threads and thread pools
- [ ] Futures, `CompletableFuture`, async execution
- [ ] Virtual threads
- [ ] Race conditions
- [ ] Atomic operations
- [ ] Locks

### Across instances

- [ ] Optimistic concurrency
- [ ] Distributed locks (database, Redis) and their limits
- [ ] Leader election concepts
- [ ] Clock / time issues
- [ ] Eventual vs strong consistency
- [ ] CAP theorem
- [ ] Network partitions, split brain
- [ ] Distributed transactions, two-phase commit concepts
- [ ] Consensus concepts

### Always remember

In distributed systems, a timeout does **not** necessarily mean the operation failed.

---

## 14. Testing

- [ ] Unit tests
- [ ] Integration tests
- [ ] End-to-end tests
- [ ] Contract tests
- [ ] Testcontainers (real PostgreSQL, Kafka, Redis in tests)
- [ ] Testing async / background code (Awaitility, no fixed sleeps)
- [ ] Mocking external services, WireMock
- [ ] Fixture / test data management
- [ ] Property-based testing concepts
- [ ] Smoke tests and regression tests
- [ ] Load and stress testing (Gatling, k6, JMeter)

Test business behavior and important failure modes, not just code coverage.

---

## 15. Performance

- [ ] Profiling
- [ ] CPU and memory bottlenecks
- [ ] Memory leaks
- [ ] JVM heap and garbage collection basics
- [ ] Thread pool sizing
- [ ] Database bottlenecks and slow queries
- [ ] Network latency
- [ ] Serialization cost
- [ ] Streaming instead of loading everything
- [ ] Batching
- [ ] Compression

Useful latency concepts: p50, p95, p99, throughput, saturation.

---

# Part 3 — Platform & Delivery

## 16. Spring / Java Ecosystem

### Spring

- [ ] Spring Boot
- [ ] Spring MVC
- [ ] Spring WebFlux — know when it is useful and when it is unnecessary
- [ ] Spring Data JPA, Hibernate
- [ ] Spring Security
- [ ] Spring Validation
- [ ] Spring Scheduler
- [ ] Spring Batch
- [ ] Spring Retry
- [ ] Spring Cache
- [ ] Micrometer, Actuator
- [ ] Jackson
- [ ] MapStruct
- [ ] Lombok trade-offs
- [ ] Maven / Gradle
- [ ] Major version upgrades (e.g. Spring Boot 4, Jackson 3)

### Java / JVM

- [ ] Heap vs stack
- [ ] Garbage collection
- [ ] Threads and virtual threads
- [ ] `CompletableFuture`
- [ ] Records
- [ ] Generics
- [ ] Streams
- [ ] Immutability

---

## 17. Docker, Infrastructure & Deployment

### Docker

- [ ] Dockerfile best practices (multi-stage builds, non-root user, small images)
- [ ] Docker Compose for local dependencies
- [ ] Environment variables and secrets

### Kubernetes

- [ ] Pods, Deployments, Services
- [ ] ConfigMaps and Secrets
- [ ] Ingress
- [ ] Resource requests / limits
- [ ] Autoscaling
- [ ] Probes

### Deployment

- [ ] Reverse proxy concepts, Nginx
- [ ] Rolling deployments
- [ ] Blue-green deployment
- [ ] Canary deployment
- [ ] Graceful shutdown
- [ ] Running several instances safely (scheduled jobs, caches, sessions)

---

## 18. CI/CD & Engineering Workflow

- [ ] Git and branching strategies
- [ ] Pull requests and code review
- [ ] CI pipelines with automated tests
- [ ] Static analysis
- [ ] Dependency scanning
- [ ] Build artifacts
- [ ] Deployment pipelines
- [ ] Rollback strategy
- [ ] Database migration strategy (expand / contract, backward-compatible changes)
- [ ] Semantic versioning
- [ ] Release notes

---

## 19. Architecture & Design

- [ ] Layered architecture
- [ ] Modular monolith
- [ ] Microservices
- [ ] Hexagonal / clean architecture
- [ ] Domain-driven design: bounded contexts, aggregates, domain events
- [ ] CQRS
- [ ] Event-driven architecture
- [ ] Service discovery concepts
- [ ] Architecture decision records (ADRs)

### Important architecture question

Do not ask only:

> "Can we use this pattern?"

Also ask:

> "What concrete problem does this pattern solve in this system?"

---

## 20. Frontend / Admin Panels

- [ ] React fundamentals
- [ ] TypeScript
- [ ] Routing
- [ ] Forms and validation
- [ ] State management
- [ ] API integration
- [ ] Authentication flow
- [ ] Role-based UI
- [ ] Tables, pagination, filtering
- [ ] File upload
- [ ] Error handling and loading states

The goal is not to become a frontend specialist, but to ship practical internal tools end-to-end.

---

# Part 4 — Working in Production

## 21. Production Readiness

Before calling a service production-ready, check:

- [ ] Configuration and secrets
- [ ] Database migrations
- [ ] Health checks
- [ ] Graceful shutdown
- [ ] Logging, metrics, tracing
- [ ] Alerts and dashboards
- [ ] Timeouts, retries, circuit breakers
- [ ] Rate limits
- [ ] Security review
- [ ] Backups (and a tested restore)
- [ ] Rollback plan
- [ ] Runbooks
- [ ] Capacity
- [ ] Behavior when dependencies are down

---

## 22. Incident Handling

- [ ] Reading logs efficiently
- [ ] Using metrics and traces during incidents
- [ ] Mitigation vs permanent fix
- [ ] Rollback, feature disabling, traffic reduction
- [ ] Database investigation
- [ ] Thread dumps and heap dumps
- [ ] Root cause analysis
- [ ] Blameless postmortems

During an incident, optimize first for **restoring service safely**, then investigate the deeper
root cause.

---

## 23. Failure Scenarios Worth Practicing

For every project, ask what happens when:

- [ ] The database is unavailable
- [ ] Redis is unavailable
- [ ] Kafka / RabbitMQ is unavailable
- [ ] An external API is unavailable
- [ ] An external API becomes very slow
- [ ] Duplicate requests arrive
- [ ] Duplicate messages arrive
- [ ] A scheduled job runs twice
- [ ] The application crashes mid-transaction
- [ ] The application crashes after the DB commit but before publishing an event or enqueuing a job
- [ ] Message processing succeeds but acknowledgement fails
- [ ] A network timeout happens after the remote operation actually succeeded
- [ ] Disk / memory / CPU becomes saturated
- [ ] Traffic suddenly increases 10x
- [ ] A bad deployment reaches production

---

# Part 5 — Beyond Code

## 24. Business & Domain Knowledge

Technical ability alone is not enough.

- [ ] Understand the business workflow and why the system exists
- [ ] Know the critical business rules
- [ ] Know which operations are financially or operationally sensitive
- [ ] Understand data ownership
- [ ] Understand upstream / downstream systems
- [ ] Recognize domain invariants
- [ ] Translate vague requirements into concrete technical behavior
- [ ] Challenge requirements when they conflict with existing business rules

A strong engineer can explain both:

> What does this code do?

and

> Why does the business need it to work this way?

---

## 25. AI-Assisted Engineering

- [ ] Use AI for boilerplate and repetitive implementation
- [ ] Generate tests
- [ ] Review unfamiliar code
- [ ] Explain stack traces
- [ ] Explore alternatives
- [ ] Draft migrations, scripts and API documentation
- [ ] Analyze logs with proper data-safety boundaries
- [ ] Verify generated code before production use
- [ ] Understand the code instead of blindly accepting it

AI can reduce implementation cost, but it does not remove responsibility for architecture,
correctness, security, production behavior, trade-offs, domain knowledge and debugging.

---

## 26. Concepts I Should Be Able to Explain Without a Framework

Framework knowledge changes. These concepts remain valuable.

- [ ] Transaction and isolation level
- [ ] Idempotency
- [ ] Retry and timeout
- [ ] Circuit breaker
- [ ] Rate limiting
- [ ] Backpressure
- [ ] Eventual consistency
- [ ] Optimistic locking
- [ ] Outbox and inbox patterns
- [ ] Saga
- [ ] Cache invalidation
- [ ] Consumer group
- [ ] Dead-letter queue
- [ ] Distributed tracing
- [ ] Load balancing and horizontal scaling
- [ ] Graceful degradation
- [ ] Zero-downtime deployment

---

## Personal Progress

Track hands-on experience honestly. Empty cells are fine: they show what is still ahead.

| Topic | Read / Understand | Built in Demo | Used in Production | Notes |
|---|---|---|---|---|
| REST APIs |  | [rest-api-design](rest-api-design/) |  |  |
| Validation |  | [validation](validation/) |  |  |
| JPA relationships |  | [er-*](er-one-to-many/) |  |  |
| Auditing / encryption / masking |  | [auditing](entity-auditing/), [encryption](entity-encryption/), [masking](dto-field-masker/) |  |  |
| Caching / Redis |  | [caching](caching/) |  |  |
| Resilience4j |  | [resillience4j](resillience4j/) |  |  |
| Logging |  | [slf4j](slf4j-logging/), [logbook](logbook-logging-requests/) |  |  |
| Kafka |  | [outbox](kafka-outbox-pattern-flight-data-updates-example/), [inbox](kafka-inbox-pattern-airports-data-filling-example/) |  |  |
| Outbox / Inbox pattern |  | [outbox](kafka-outbox-pattern-flight-data-updates-example/), [inbox](kafka-inbox-pattern-airports-data-filling-example/) |  |  |
| Scheduler / background jobs |  | [quartz](quartz-scheduler-shedlock/), [task table](report-generation-light-task-with-scheduler/), [JobRunr](report-generation-light-task-with-jobrunr/) |  |  |
| Spring Security |  |  |  |  |
| RabbitMQ |  |  |  |  |
| Spring Batch |  |  |  |  |
| S3 / file handling |  | [file-operations-io](file-operations-io/) |  |  |
| Observability (metrics, tracing) |  |  |  |  |
| Docker |  |  |  |  |
| Kubernetes |  |  |  |  |
| React Admin Panel |  |  |  |  |

---

## Final Reminder

The most valuable skill is not knowing the largest number of libraries.

It is being able to look at a system and reason about:

1. what the business needs,
2. how the data should behave,
3. what can fail,
4. what happens under concurrency,
5. how the system behaves under load,
6. how to observe it in production,
7. and how to recover when something goes wrong.

Keep this file alive. Add concepts when they appear in real work, interviews, incidents or side
projects, and link a demo here when one is built.
