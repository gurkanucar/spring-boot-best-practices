# Software Engineering Knowledge Map

A practical checklist of concepts, patterns, tools, and production concerns worth knowing as a backend/full-stack engineer.

This is **not a list of things I claim to have mastered or implemented in every project**. It is a living map of topics I want to remember, understand, practice, and revisit over time.

> The goal is not to memorize frameworks. The goal is to understand the problems they solve, the trade-offs they introduce, and how they behave in production.

---

## 1. Core Application Development

- [ ] CRUD operations
- [ ] REST API design
- [ ] SOAP / legacy service integration
- [ ] API versioning
- [ ] Pagination, sorting, filtering
- [ ] Request validation
- [ ] Error handling and consistent error responses
- [ ] DTO / entity separation
- [ ] Mapping strategies
- [ ] Configuration management
- [ ] Environment-specific configuration
- [ ] Feature flags
- [ ] Background jobs
- [ ] Scheduled / cron jobs
- [ ] Batch processing
- [ ] Report generation
- [ ] Email sending
- [ ] File upload / download
- [ ] Large file processing / streaming
- [ ] S3 / object storage operations
- [ ] Admin panel development
- [ ] React-based internal tools

---

## 2. API & Integration Design

- [ ] REST principles
- [ ] HTTP methods and status codes
- [ ] Idempotency
- [ ] Idempotency keys
- [ ] Request / response contracts
- [ ] OpenAPI / Swagger
- [ ] API backward compatibility
- [ ] Consumer-driven contracts
- [ ] Webhooks
- [ ] Polling vs event-driven integration
- [ ] REST vs messaging trade-offs
- [ ] Sync vs async communication
- [ ] Timeouts
- [ ] Connection pooling
- [ ] API gateways
- [ ] BFF pattern
- [ ] Rate limiting
- [ ] Throttling
- [ ] Quotas

### Integration failure scenarios to think about

- What if the downstream service is slow?
- What if it returns 500?
- What if the request succeeds but the response is lost?
- What if the same request is sent twice?
- What if only half of a multi-step operation succeeds?

---

## 3. Resilience & Fault Tolerance

- [ ] Retry
- [ ] Retry with exponential backoff
- [ ] Jitter
- [ ] Timeout strategy
- [ ] Circuit breaker
- [ ] Bulkhead
- [ ] Rate limiter
- [ ] Fallback strategies
- [ ] Graceful degradation
- [ ] Load shedding
- [ ] Health checks
- [ ] Readiness / liveness probes
- [ ] Resilience4j
- [ ] Spring Retry

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

## 4. Databases & Persistence

- [ ] SQL fundamentals
- [ ] Transactions
- [ ] ACID
- [ ] Isolation levels
- [ ] Optimistic locking
- [ ] Pessimistic locking
- [ ] Deadlocks
- [ ] Indexes
- [ ] Query plans
- [ ] N+1 queries
- [ ] Connection pools
- [ ] Schema design
- [ ] Normalization / denormalization
- [ ] Database migrations
- [ ] Flyway / Liquibase
- [ ] Soft delete
- [ ] Auditing
- [ ] Database constraints
- [ ] Unique constraints
- [ ] JSON columns
- [ ] Read replicas
- [ ] Partitioning
- [ ] Archiving / retention

### Questions to ask

- Does this operation need a transaction?
- What happens under concurrent requests?
- Can the same row be updated twice at the same time?
- Is the database constraint enforcing an invariant that application code alone cannot guarantee?

---

## 5. Messaging & Event-Driven Systems

- [ ] Kafka
- [ ] RabbitMQ
- [ ] Producer / consumer model
- [ ] Topics / queues / exchanges
- [ ] Consumer groups
- [ ] Partitioning
- [ ] Ordering guarantees
- [ ] Delivery semantics
- [ ] At-most-once
- [ ] At-least-once
- [ ] Exactly-once concepts
- [ ] Duplicate message handling
- [ ] Idempotent consumers
- [ ] Dead-letter queue (DLQ)
- [ ] Retry queue
- [ ] Poison messages
- [ ] Message schema evolution
- [ ] Kafka consumer lag
- [ ] Offset management
- [ ] Backpressure
- [ ] Event-driven architecture

### Patterns

- [ ] Transactional Outbox
- [ ] Inbox pattern
- [ ] Outbox + CDC
- [ ] Saga pattern
- [ ] Choreography
- [ ] Orchestration
- [ ] Event sourcing — understand when it is actually justified

---

## 6. Caching

- [ ] Redis
- [ ] Local cache
- [ ] Distributed cache
- [ ] Cache-aside
- [ ] Write-through / write-behind concepts
- [ ] TTL
- [ ] Cache invalidation
- [ ] Cache stampede
- [ ] Cache penetration
- [ ] Distributed locking
- [ ] Hot keys

> Cache invalidation should always be treated as part of the data consistency design, not as an afterthought.

---

## 7. Authentication & Security

- [ ] Spring Security
- [ ] Authentication vs authorization
- [ ] Session-based authentication
- [ ] JWT
- [ ] OAuth 2.0
- [ ] OpenID Connect
- [ ] Access tokens / refresh tokens
- [ ] Roles and permissions
- [ ] RBAC
- [ ] Method-level authorization
- [ ] Password hashing
- [ ] Secrets management
- [ ] Key rotation
- [ ] CSRF
- [ ] CORS
- [ ] XSS
- [ ] SQL injection
- [ ] SSRF
- [ ] Secure headers
- [ ] Dependency vulnerabilities
- [ ] OWASP Top 10
- [ ] Audit logging
- [ ] PII handling
- [ ] Data masking

---

## 8. Logging, Monitoring & Observability

### Logging

- [ ] Structured logging
- [ ] Log levels
- [ ] Correlation ID / request ID
- [ ] Context propagation
- [ ] Avoid logging secrets / PII
- [ ] Centralized logging
- [ ] ELK / EFK
- [ ] Loki

### Metrics

- [ ] Micrometer
- [ ] Prometheus
- [ ] Grafana
- [ ] Counters
- [ ] Gauges
- [ ] Histograms
- [ ] Percentiles
- [ ] RED metrics
  - Rate
  - Errors
  - Duration
- [ ] USE method
  - Utilization
  - Saturation
  - Errors

### Tracing

- [ ] Distributed tracing
- [ ] OpenTelemetry
- [ ] Trace ID / span ID
- [ ] Context propagation between services
- [ ] Jaeger / Tempo

### Production thinking

- Can I understand why a request failed from logs?
- Can I identify which dependency is slow?
- Can I see error rate and latency trends?
- Can I correlate logs, metrics, and traces?

---

## 9. Error Handling

- [ ] Domain exceptions
- [ ] Technical exceptions
- [ ] Global exception handling
- [ ] Error codes
- [ ] User-safe error messages
- [ ] Retryable vs non-retryable failures
- [ ] Partial failure handling
- [ ] Compensating actions
- [ ] Failure isolation

Avoid swallowing exceptions without context.

Good errors should preserve enough information for debugging without leaking sensitive details.

---

## 10. Concurrency & Distributed Systems

- [ ] Threads / thread pools
- [ ] Futures / async execution
- [ ] Race conditions
- [ ] Atomic operations
- [ ] Locks
- [ ] Optimistic concurrency
- [ ] Distributed locks
- [ ] Leader election concepts
- [ ] Clock / time issues
- [ ] Eventual consistency
- [ ] Strong consistency
- [ ] CAP theorem
- [ ] Split brain concepts
- [ ] Network partitions
- [ ] Distributed transactions
- [ ] Two-phase commit concepts
- [ ] Consensus concepts

### Always remember

In distributed systems, a timeout does **not** necessarily mean the operation failed.

---

## 11. Scheduling & Background Processing

- [ ] Cron expressions
- [ ] Spring Scheduler
- [ ] Quartz
- [ ] Job locking
- [ ] ShedLock
- [ ] Preventing duplicate execution
- [ ] Retry failed jobs
- [ ] Job status tracking
- [ ] Long-running jobs
- [ ] Batch processing
- [ ] Spring Batch
- [ ] Job observability

Questions:

- What if two application instances execute the same scheduled job?
- What if the process crashes halfway through?
- Can the job safely run again?

---

## 12. File & Object Storage

- [ ] AWS S3
- [ ] Presigned URLs
- [ ] Multipart upload
- [ ] Streaming downloads
- [ ] MIME type validation
- [ ] File size limits
- [ ] Virus / malware scanning concepts
- [ ] Retention policies
- [ ] Lifecycle rules
- [ ] Temporary files
- [ ] Large CSV / Excel processing

Avoid loading large files fully into memory when streaming is possible.

---

## 13. Email & Notifications

- [ ] SMTP basics
- [ ] Transactional email
- [ ] HTML templates
- [ ] Async email sending
- [ ] Retry strategy
- [ ] Duplicate notification prevention
- [ ] Delivery status
- [ ] Email provider integration
- [ ] Notification preferences
- [ ] Push / SMS concepts

---

## 14. Testing

- [ ] Unit tests
- [ ] Integration tests
- [ ] End-to-end tests
- [ ] Contract tests
- [ ] Testcontainers
- [ ] Database integration tests
- [ ] Kafka / RabbitMQ tests
- [ ] Mocking external services
- [ ] WireMock
- [ ] Fixture / test data management
- [ ] Property-based testing concepts
- [ ] Load testing
- [ ] Stress testing
- [ ] Smoke tests
- [ ] Regression tests

### Testing mindset

Test business behavior and important failure modes, not just code coverage.

---

## 15. Performance

- [ ] Profiling
- [ ] CPU bottlenecks
- [ ] Memory bottlenecks
- [ ] Memory leaks
- [ ] JVM heap
- [ ] Garbage collection basics
- [ ] Thread pool sizing
- [ ] Database bottlenecks
- [ ] Slow queries
- [ ] Network latency
- [ ] Serialization cost
- [ ] Pagination
- [ ] Streaming
- [ ] Batching
- [ ] Compression
- [ ] Connection pooling
- [ ] Load testing

Useful latency concepts:

- p50
- p95
- p99
- throughput
- saturation

---

## 16. Spring / Java Ecosystem

- [ ] Spring Boot
- [ ] Spring MVC
- [ ] Spring WebFlux — know when it is useful and when it is unnecessary
- [ ] Spring Data JPA
- [ ] Hibernate
- [ ] Spring Security
- [ ] Spring Validation
- [ ] Spring Scheduler
- [ ] Spring Batch
- [ ] Spring Retry
- [ ] Spring Cache
- [ ] Resilience4j
- [ ] Micrometer
- [ ] Actuator
- [ ] Feign / HTTP clients
- [ ] Jackson
- [ ] MapStruct
- [ ] Lombok trade-offs
- [ ] Maven / Gradle

### JVM topics

- [ ] Heap vs stack
- [ ] Garbage collection
- [ ] Threads
- [ ] Virtual threads
- [ ] CompletableFuture
- [ ] Records
- [ ] Generics
- [ ] Streams
- [ ] Immutability

---

## 17. Frontend / Admin Panels

- [ ] React fundamentals
- [ ] TypeScript
- [ ] Routing
- [ ] Forms
- [ ] Validation
- [ ] State management
- [ ] API integration
- [ ] Authentication flow
- [ ] Role-based UI
- [ ] Tables
- [ ] Pagination
- [ ] Filtering
- [ ] File upload
- [ ] Error handling
- [ ] Loading states
- [ ] Internal admin tools

The goal is not necessarily to become a frontend specialist, but to be capable of shipping practical internal tools end-to-end.

---

## 18. Docker, Infrastructure & Deployment

- [ ] Docker
- [ ] Dockerfile best practices
- [ ] Docker Compose
- [ ] Environment variables
- [ ] Secrets
- [ ] Reverse proxy concepts
- [ ] Nginx
- [ ] Kubernetes basics
- [ ] Pods
- [ ] Deployments
- [ ] Services
- [ ] ConfigMaps
- [ ] Secrets
- [ ] Ingress
- [ ] Resource requests / limits
- [ ] Autoscaling
- [ ] Rolling deployments
- [ ] Blue-green deployment
- [ ] Canary deployment
- [ ] Graceful shutdown

---

## 19. CI/CD & Engineering Workflow

- [ ] Git
- [ ] Branching strategies
- [ ] Pull requests
- [ ] Code review
- [ ] CI pipelines
- [ ] Automated tests
- [ ] Static analysis
- [ ] Dependency scanning
- [ ] Build artifacts
- [ ] Deployment pipelines
- [ ] Rollback strategy
- [ ] Database migration strategy
- [ ] Semantic versioning
- [ ] Release notes

---

## 20. Architecture & Design

- [ ] Modular monolith
- [ ] Microservices
- [ ] Layered architecture
- [ ] Hexagonal architecture
- [ ] Clean architecture
- [ ] Domain-driven design
- [ ] Bounded contexts
- [ ] Aggregates
- [ ] Domain events
- [ ] CQRS
- [ ] Event-driven architecture
- [ ] API gateway
- [ ] Service discovery concepts

### Important architecture question

Do not ask only:

> "Can we use this pattern?"

Also ask:

> "What concrete problem does this pattern solve in this system?"

---

## 21. Production Readiness

Before considering a service production-ready, think about:

- [ ] Configuration
- [ ] Secrets
- [ ] Database migrations
- [ ] Health checks
- [ ] Graceful shutdown
- [ ] Logging
- [ ] Metrics
- [ ] Tracing
- [ ] Alerts
- [ ] Dashboards
- [ ] Timeouts
- [ ] Retries
- [ ] Circuit breakers
- [ ] Rate limits
- [ ] Security
- [ ] Backups
- [ ] Rollback
- [ ] Runbooks
- [ ] Capacity
- [ ] Failure scenarios
- [ ] Dependency outages

---

## 22. Incident Handling

- [ ] Reading logs efficiently
- [ ] Using metrics during incidents
- [ ] Distributed tracing
- [ ] Root cause analysis
- [ ] Mitigation vs permanent fix
- [ ] Rollback
- [ ] Feature disabling
- [ ] Traffic reduction
- [ ] Database investigation
- [ ] Thread dumps
- [ ] Heap dumps
- [ ] Postmortems

During an incident, optimize first for **restoring service safely**, then investigate the deeper root cause.

---

## 23. Business & Domain Knowledge

Technical ability alone is not enough.

- [ ] Understand the business workflow
- [ ] Understand why the system exists
- [ ] Know the critical business rules
- [ ] Know which operations are financially or operationally sensitive
- [ ] Understand data ownership
- [ ] Understand upstream / downstream systems
- [ ] Recognize domain invariants
- [ ] Translate vague requirements into concrete technical behavior
- [ ] Challenge requirements when they conflict with existing business rules

A strong engineer should be able to explain both:

> What does this code do?

and

> Why does the business need it to work this way?

---

## 24. AI-Assisted Engineering

- [ ] Use AI for boilerplate and repetitive implementation
- [ ] Generate tests
- [ ] Review unfamiliar code
- [ ] Explain stack traces
- [ ] Explore alternatives
- [ ] Generate migration / scripting drafts
- [ ] Draft API documentation
- [ ] Analyze logs with proper data-safety boundaries
- [ ] Verify generated code before production use
- [ ] Understand the code instead of blindly accepting it

AI can reduce implementation cost, but it does not remove responsibility for:

- architecture
- correctness
- security
- production behavior
- trade-offs
- domain knowledge
- debugging

---

## 25. Failure Scenarios Worth Practicing

A useful exercise for every project is to ask what happens when:

- [ ] Database is unavailable
- [ ] Redis is unavailable
- [ ] Kafka / RabbitMQ is unavailable
- [ ] External API is unavailable
- [ ] External API becomes very slow
- [ ] Duplicate requests arrive
- [ ] Duplicate messages arrive
- [ ] A scheduled job runs twice
- [ ] Application crashes mid-transaction
- [ ] Application crashes after DB commit but before publishing an event
- [ ] Message processing succeeds but acknowledgement fails
- [ ] Network timeout happens after the remote operation actually succeeded
- [ ] Disk / memory / CPU becomes saturated
- [ ] Traffic suddenly increases 10x
- [ ] A bad deployment reaches production

---

## 26. Concepts I Should Be Able to Explain Without a Framework

Framework knowledge changes. These concepts remain valuable.

- [ ] Transaction
- [ ] Isolation level
- [ ] Idempotency
- [ ] Retry
- [ ] Timeout
- [ ] Circuit breaker
- [ ] Rate limiting
- [ ] Backpressure
- [ ] Eventual consistency
- [ ] Optimistic locking
- [ ] Outbox pattern
- [ ] Inbox pattern
- [ ] Saga
- [ ] Cache invalidation
- [ ] Consumer group
- [ ] Dead-letter queue
- [ ] Distributed tracing
- [ ] Load balancing
- [ ] Horizontal scaling
- [ ] Graceful degradation
- [ ] Zero-downtime deployment

---

## Personal Progress

Use this section to keep track of hands-on experience without pretending every topic has already been implemented.

| Topic | Read / Understand | Built in Demo | Used in Production | Notes |
|---|---|---|---|---|
| REST APIs |  |  |  |  |
| Spring Security |  |  |  |  |
| Kafka |  |  |  |  |
| RabbitMQ |  |  |  |  |
| Redis |  |  |  |  |
| Outbox Pattern |  |  |  |  |
| Resilience4j |  |  |  |  |
| S3 |  |  |  |  |
| Scheduler / Batch |  |  |  |  |
| Observability |  |  |  |  |
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

Keep this file alive. Add concepts when they appear in real work, interviews, incidents, or side projects.
