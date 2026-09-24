# Spring Boot + Kafka: Inbox Pattern (Airports)

An `airport` table that is filled and updated from two sources: **Kafka events** and a
**REST API**. Every change carries a `transactionId`, a `version` and the airport's full state
(including its runways). Both sources go through one **inbox table**, and one processor applies
the changes to the airport table.

```
Kafka "airport-events" (key = airport code) ──┐
                                              ├──> inbox_event ──> InboxProcessor ──> airport + runway
PUT /api/airports/{code}  ────────────────────┘    (PENDING)       (version rule)
```

Spring Boot 4.1.1 · Java 25 · Spring Kafka 4.1 · PostgreSQL 17 · Flyway · Testcontainers

## Why an inbox?

What goes wrong when the Kafka listener writes the airport table directly:

| Problem | What happens | How the inbox solves it |
|---|---|---|
| **Duplicates** | Kafka delivers *at least once*: after a crash or rebalance the same message comes again. REST clients retry too. | `transaction_id` is `unique` in the inbox. A second copy is not stored (`insert ... on conflict do nothing`). |
| **Late / out-of-order events** | Version 4 arrives after version 5 (retries, two sources, replays). Applying it overwrites newer data. | Each event is a full snapshot with a version. The processor applies it only if `event.version > airport.version`, otherwise the row becomes `SKIPPED`. |
| **Slow or failing business logic in the listener** | The partition is blocked, retries hammer the database, the consumer group rebalances. | The listener only inserts one row and returns. The processing happens later, with its own retries and backoff. |
| **Two sources, two sets of rules** | REST and Kafka drift apart. | Both write the same inbox row. One processor, one version rule, one validation. |
| **"What happened to change X?"** | Nobody knows. | Every change is a row with status, attempts, last error, Kafka position. |

## Running

```bash
cd kafka-inbox-pattern-airports-data-filling-example
docker compose up -d        # Postgres (localhost:5434), Kafka (localhost:9092), Kafka UI (localhost:8085)
./mvnw spring-boot:run      # app on localhost:8095; Flyway creates the tables
```

- Postgres is mapped to host port **5434**, so it does not clash with a local Postgres on 5432.
- The Kafka UI at `http://localhost:8085` shows the topics `airport-events` and `airport-events-dlt`.
- `docker compose down -v` stops everything and deletes the data.

Tests need only Docker (Testcontainers starts its own Postgres and Kafka):

```bash
./mvnw test
```

## The event

The same content for Kafka and REST. **Always the full state**, never a diff.

```json
{
  "transactionId": "tx-ist-002",
  "airportCode": "IST",
  "version": 2,
  "occurredAt": "2026-09-24T10:00:00Z",
  "airport": {
    "icaoCode": "LTFM",
    "name": "Istanbul Airport",
    "city": "Istanbul",
    "countryCode": "TR",
    "timezone": "Europe/Istanbul",
    "runways": [
      { "designator": "16L/34R", "lengthMeters": 3750, "surface": "CONCRETE" },
      { "designator": "18/36",   "lengthMeters": 3060, "surface": "ASPHALT" }
    ]
  }
}
```

| Field | Meaning |
|---|---|
| `transactionId` | Unique id of this change, chosen by the sender. The idempotency key. |
| `airportCode` | IATA code. On Kafka it is also the **message key**, so all events of one airport land on the same partition and stay in order. |
| `version` | The sender's version of this airport, increasing with every change. |
| `airport` | The full state after the change, including the runways. |

Validation (Bean Validation on the records in `event/dto`) is the same for both sources:
`@Valid @RequestBody` on REST, `@Valid` on the `@KafkaListener` parameter.

## A complete example: request by request

Every response below is real output (`/dev/kafka/...` is a dev-only endpoint that plays the
external system and publishes to Kafka).

### 1. Kafka creates IST (version 1)

```bash
curl -X POST localhost:8095/dev/kafka/airport-events/IST -H 'Content-Type: application/json' -d '{
  "transactionId": "tx-ist-001", "airportCode": "IST", "version": 1, "occurredAt": "2026-09-24T09:00:00Z",
  "airport": { "icaoCode": "LTFM", "name": "Istanbul Airport", "city": "Istanbul", "countryCode": "TR",
               "timezone": "Europe/Istanbul",
               "runways": [ { "designator": "16L/34R", "lengthMeters": 3750, "surface": "ASPHALT" },
                            { "designator": "17L/35R", "lengthMeters": 4100, "surface": "ASPHALT" } ] } }'
```

`GET /api/airports/IST`:

```json
{
  "code": "IST", "icaoCode": "LTFM", "name": "Istanbul Airport", "city": "Istanbul",
  "countryCode": "TR", "timezone": "Europe/Istanbul",
  "version": 1, "lastTransactionId": "tx-ist-001",
  "createdAt": "2026-09-24T17:15:07.605874Z", "updatedAt": "2026-09-24T17:15:07.605874Z",
  "runways": [
    { "id": 1, "designator": "16L/34R", "lengthMeters": 3750, "surface": "ASPHALT" },
    { "id": 2, "designator": "17L/35R", "lengthMeters": 4100, "surface": "ASPHALT" }
  ]
}
```

### 2. Kafka sends version 2: one runway changed, one removed, one added

The event shown in [The event](#the-event). Result:

```json
{
  "code": "IST", "version": 2, "lastTransactionId": "tx-ist-002",
  "createdAt": "2026-09-24T17:15:07.605874Z", "updatedAt": "2026-09-24T17:15:09.686957Z",
  "runways": [
    { "id": 1, "designator": "16L/34R", "lengthMeters": 3750, "surface": "CONCRETE" },
    { "id": 3, "designator": "18/36",   "lengthMeters": 3060, "surface": "ASPHALT" }
  ]
}
```

`16L/34R` keeps **id 1**: it was updated in place, matched by its designator. `17L/35R` was
deleted, and `18/36` was inserted.

### 3. REST sends version 3: `202 Accepted`

```bash
curl -i -X PUT localhost:8095/api/airports/IST -H 'Content-Type: application/json' -d '{
  "transactionId": "tx-ist-003", "version": 3, "occurredAt": "2026-09-24T11:00:00Z",
  "airport": { "icaoCode": "LTFM", "name": "Istanbul Airport (IST)", "city": "Istanbul", "countryCode": "TR",
               "timezone": "Europe/Istanbul",
               "runways": [ { "designator": "16L/34R", "lengthMeters": 3750, "surface": "CONCRETE" },
                            { "designator": "18/36",   "lengthMeters": 3060, "surface": "ASPHALT" } ] } }'
```

```
HTTP/1.1 202
Location: /api/inbox/3

{"inboxEventId":3,"transactionId":"tx-ist-003","status":"PENDING","duplicate":false,"statusUrl":"/api/inbox/3"}
```

The change is **stored, not yet applied**. `GET /api/inbox/3` a moment later:

```json
{
  "id": 3, "transactionId": "tx-ist-003", "source": "REST", "airportCode": "IST", "version": 3,
  "status": "PROCESSED", "attempts": 0, "lastError": null, "kafkaPosition": null,
  "receivedAt": "2026-09-24T17:15:11.455638Z", "processedAt": "2026-09-24T17:15:11.763569Z"
}
```

### 4. The same REST request again (client retry)

```json
{"inboxEventId":3,"transactionId":"tx-ist-003","status":"PROCESSED","duplicate":true,"statusUrl":"/api/inbox/3"}
```

`duplicate: true` means nothing new was stored. The client learns that its first attempt went through.

### 5. A late version 2 and a redelivered version 1 arrive on Kafka

```bash
# a different change (new transactionId), but only version 2: older than what we have
curl -X POST localhost:8095/dev/kafka/airport-events/IST -H 'Content-Type: application/json' -d '{
  "transactionId": "tx-ist-002b", "airportCode": "IST", "version": 2, ...
  "airport": { ..., "name": "Old name from a late event", "runways": [] } }'

# exactly the first message again (same transactionId): a redelivery
curl -X POST localhost:8095/dev/kafka/airport-events/IST -H 'Content-Type: application/json' -d '{ "transactionId": "tx-ist-001", ... }'
```

The airport is unchanged: still version 3, name `Istanbul Airport (IST)`, 2 runways.

### 6. Everything that happened to IST: `GET /api/airports/IST/changes`

```json
[
  { "id": 1, "transactionId": "tx-ist-001",  "source": "KAFKA", "version": 1, "status": "PROCESSED",
    "attempts": 0, "lastError": null, "kafkaPosition": "airport-events-2@0", ... },
  { "id": 2, "transactionId": "tx-ist-002",  "source": "KAFKA", "version": 2, "status": "PROCESSED",
    "attempts": 0, "lastError": null, "kafkaPosition": "airport-events-2@1", ... },
  { "id": 3, "transactionId": "tx-ist-003",  "source": "REST",  "version": 3, "status": "PROCESSED",
    "attempts": 0, "lastError": null, "kafkaPosition": null, ... },
  { "id": 5, "transactionId": "tx-ist-002b", "source": "KAFKA", "version": 2, "status": "SKIPPED",
    "attempts": 0, "lastError": "Stale: event version 2, airport already at version 3",
    "kafkaPosition": "airport-events-2@2", ... }
]
```

- The redelivered `tx-ist-001` does not appear a second time.
- `id 4` is missing because the duplicate REST call consumed a sequence value in its
  `insert ... on conflict do nothing`. Gaps in the ids are normal.
- Kafka and REST changes are in one list, under one version rule.

### 7. Invalid REST request: `400`

```bash
curl -X PUT localhost:8095/api/airports/SAW -H 'Content-Type: application/json' -d '{
  "version": 0,
  "airport": { "icaoCode": "LTF", "name": "Sabiha Gokcen", "city": "Istanbul", "countryCode": "TR",
               "timezone": "Europe/Ankara",
               "runways": [ { "designator": "06/24", "lengthMeters": 3000, "surface": "ASPHALT" },
                            { "designator": "06/24", "lengthMeters": 3000, "surface": "ASPHALT" } ] } }'
```

```json
{
  "title": "Bad Request", "status": 400, "detail": "Validation failed", "instance": "/api/airports/SAW",
  "errors": [
    "airport.icaoCode: must be a 4-letter ICAO code",
    "airport.runwayDesignatorsUnique: runway designators must be unique",
    "airport.timezoneKnown: must be a known time zone, e.g. Europe/Istanbul",
    "transactionId: must not be blank",
    "version: must be greater than 0"
  ]
}
```

Nothing is stored. Invalid **Kafka** messages go to the dead letter topic instead (step 9).

### 8. A change that fails on every attempt: `FAILED`, then fixed

SAW is sent with IST's ICAO code `LTFM`, which violates the unique constraint on `icao_code`:

```bash
curl -X POST localhost:8095/dev/kafka/airport-events/SAW -H 'Content-Type: application/json' -d '{
  "transactionId": "tx-saw-001", "airportCode": "SAW", "version": 1,
  "airport": { "icaoCode": "LTFM", "name": "Sabiha Gokcen", ... } }'
```

The processor retries with a growing delay (1x, 2x, 3x ... `retry-backoff`) and gives up after
`max-attempts`. `GET /api/inbox?status=FAILED`:

```json
[
  {
    "id": 7, "transactionId": "tx-saw-001", "source": "KAFKA", "airportCode": "SAW", "version": 1,
    "status": "FAILED", "attempts": 5,
    "lastError": "PSQLException: ERROR: duplicate key value violates unique constraint \"airport_icao_code_key\"\n  Detail: Key (icao_code)=(LTFM) already exists.",
    "kafkaPosition": "airport-events-2@6",
    "receivedAt": "2026-09-24T17:15:16.157600Z", "processedAt": "2026-09-24T17:15:26.709662Z"
  }
]
```

The sender corrects its data and sends **version 2** with ICAO `LTFJ`: it is `PROCESSED` and SAW
exists. The failed version 1 can then be retried by hand (`POST /api/inbox/7/retry`), and it
safely ends up `SKIPPED`, because it is older than what is stored:

```json
[
  { "id": 7, "transactionId": "tx-saw-001", "version": 1, "status": "SKIPPED",
    "lastError": "Stale: event version 1, airport already at version 2", ... },
  { "id": 8, "transactionId": "tx-saw-002", "version": 2, "status": "PROCESSED", "lastError": null, ... }
]
```

Retrying a row that is not `FAILED` is refused:

```json
{ "title": "Conflict", "status": 409, "detail": "Only FAILED events can be retried, event 7 is SKIPPED",
  "instance": "/api/inbox/7/retry" }
```

If the cause is fixed on *our* side (a bug, a missing reference), the manual retry applies the
original event instead.

### 9. Invalid Kafka messages: the dead letter topic

A message that can never be processed does not block the partition. It is published **as
received** to `airport-events-dlt`, with the reason in headers, and never reaches the inbox:

| Message | Reason (header `kafka_dlt-exception-cause-fqcn` / message) |
|---|---|
| key `SAW`, value `{ "transactionId": "tx-broken", ` | `ConversionException`: Failed to convert from JSON (`Unexpected end-of-input`) |
| key `ESB`, value `{"transactionId":"tx-wrong-key","airportCode":"SAW",...}` | `InvalidEventException`: Message key 'ESB' does not match airportCode 'SAW' |
| a message violating a constraint (blank name, bad ICAO, ...) | `MethodArgumentNotValidException` from `@Valid` |

Other headers carry the original topic, partition and offset. Such a message is looked at by a
human, fixed at the source, and sent again.

## Package layout

Feature packages at the top, and inside each feature one sub-package per kind of class:

```
com.gucardev.kafkainboxpatternairportsdatafillingexample
├── airport/                 the airport table (current state)
│   ├── controller/          AirportController        GET /api/airports..., PUT /api/airports/{code}
│   ├── dto/                 AirportResponse, AirportUpdateRequest, AcceptedResponse
│   ├── entity/              Airport, Runway, RunwaySurface
│   ├── repository/          AirportRepository        (with the SELECT ... FOR UPDATE)
│   └── service/             AirportQueryService, AirportSnapshotApplier (the version rule)
├── event/                   the contract shared by Kafka and REST
│   ├── dto/                 AirportEvent, AirportPayload, RunwayPayload (+ validation)
│   └── exception/           InvalidEventException
├── inbox/                   the inbox pattern itself
│   ├── config/              InboxProperties          (app.inbox.*)
│   ├── controller/          InboxController          GET /api/inbox, POST /api/inbox/{id}/retry
│   ├── dto/                 InboxEventResponse
│   ├── entity/              InboxEvent, InboxStatus, InboxSource
│   ├── repository/          InboxEventRepository     (with FOR UPDATE SKIP LOCKED)
│   └── service/             InboxWriter (receive), InboxProcessor (apply), InboxAdminService, InboxCleanupJob
├── kafka/
│   ├── config/              KafkaConfig              topics, JSON converter, @Valid, error handler + DLT
│   ├── controller/          DevPublishController     dev only: publish to Kafka with curl
│   └── listener/            AirportEventListener
└── common/error/            GlobalExceptionHandler, ResourceNotFoundException, ConflictException
```

## Database schema (3 tables, Flyway `V1__airports_and_inbox.sql`)

**`airport`**: the current state, written only by the processor

| column | type | notes |
|---|---|---|
| `code` | varchar(3) | PK, IATA code |
| `icao_code` | varchar(4) | not null, **unique** |
| `name`, `city` | varchar | not null |
| `country_code` | varchar(2) | not null |
| `timezone` | varchar(50) | not null |
| `version` | bigint | the **sender's** version (not a JPA `@Version`) |
| `last_transaction_id` | varchar(100) | the event that produced this state |
| `created_at`, `updated_at` | timestamptz | |

**`runway`**: `id` (PK), `airport_code` (FK → `airport`, on delete cascade), `designator`,
`length_meters`, `surface`, with **unique (`airport_code`, `designator`)**.

**`inbox_event`**: every received change

| column | type | notes |
|---|---|---|
| `id` | bigserial | PK |
| `transaction_id` | varchar(100) | **unique**: the idempotency guarantee |
| `source` | varchar(10) | `KAFKA` / `REST` |
| `airport_code`, `version` | | copied out of the payload for querying |
| `payload` | jsonb | the event as received |
| `status` | varchar(20) | `PENDING` / `PROCESSED` / `SKIPPED` / `FAILED` |
| `attempts`, `last_error` | | retry bookkeeping |
| `kafka_position` | varchar | `topic-partition@offset`, for tracing |
| `received_at`, `next_attempt_at`, `processed_at` | timestamptz | |

A **partial index** `on (next_attempt_at, id) where status = 'PENDING'` serves the processor's
query and stays small, because finished rows are not in it.

## How it works

### Receiving: store and nothing else

`AirportEventListener` (Kafka) and `AirportController.put` (REST) both end in `InboxWriter.store`:

```sql
insert into inbox_event (transaction_id, source, airport_code, version, payload, status, ...)
values (...)
on conflict (transaction_id) do nothing
returning id
```

- No row returned means a duplicate, detected in one statement without check-then-insert races.
- **Offsets are committed after the insert** (`ack-mode: record`). A crash between insert and
  commit only causes a redelivery, which the unique key absorbs. *At-least-once delivery +
  idempotent receive = effectively once.*

### Processing: one event per transaction

`InboxProcessor` polls every `poll-interval` and drains everything that is due:

```
BEGIN
  SELECT * FROM inbox_event WHERE status='PENDING' AND next_attempt_at <= now()
    ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED          -- claim one event
  SELECT * FROM airport WHERE code = ? FOR UPDATE       -- lock this airport
  if event.version > airport.version: update airport + sync runways -> PROCESSED
  else                                                                -> SKIPPED
COMMIT                                                  -- airport and inbox status together
```

- **`SKIP LOCKED`**: several application instances can poll the same table. Each claims a
  different row instead of waiting for the others (tested with 4 concurrent processors on 40
  events: every event handled exactly once).
- **`FOR UPDATE` on the airport**: two events of the same airport, processed by two instances,
  are applied one after the other, so the version comparison always sees the latest committed version.
- **Airport change and status change commit together.** There is no state where the airport
  was updated but the event is still `PENDING` (or the reverse).
- If applying throws, the transaction rolls back and a second, small transaction records the
  attempt (`attempts + 1`, `last_error`, `next_attempt_at` with backoff, `FAILED` at `max-attempts`).
  A payload that cannot be read or is invalid is marked `FAILED` immediately, since retrying can't help.

### The version rule and full snapshots

Because every event is the **complete** state, the processor never needs the previous event:
the highest version wins, and anything older or equal is skipped. Missing versions (v3 after v1)
are no problem either. With partial events (deltas) this would not work: they must be applied
strictly in order, and a missing version blocks everything after it.

### Runways: synced by natural key, not "delete all, insert all"

`Airport.syncRunways` updates existing runways in place (matched by `designator`), removes the
missing ones and adds the new ones. `clear()` + add-all would fail, because Hibernate flushes
INSERTs before DELETEs and the new row would hit the unique `(airport_code, designator)` before
the old one is gone. In-place updates also keep row ids stable and write only what changed.

### Failure handling at a glance

| Where | What | Result |
|---|---|---|
| Kafka listener | not JSON / invalid / wrong key | straight to `airport-events-dlt` |
| Kafka listener | database unreachable while storing | retried with exponential backoff (~1 min), then DLT |
| REST | invalid | `400` with the list of errors |
| Processor | exception while applying (constraint, deadlock, ...) | retried `max-attempts` times with backoff, then `FAILED` |
| Processor | stored payload unreadable / invalid | `FAILED` immediately |
| Operations | `GET /api/inbox?status=FAILED`, `POST /api/inbox/{id}/retry` | inspect and re-run after a fix |

### Configuration

```yaml
spring.kafka:
  consumer.enable-auto-commit: false
  listener.ack-mode: record           # commit each offset after the record is stored
app:
  kafka.airport-events-topic: airport-events
  inbox:
    poll-interval: 500ms              # how often the processor looks for work
    max-attempts: 5                   # then FAILED
    retry-backoff: 5s                 # 5s, 10s, 15s, ...
    retention: 7d                     # PROCESSED/SKIPPED rows older than this are deleted
    cleanup-cron: "0 0 3 * * *"
```

## Design notes and pitfalls

- **Latency.** A change is applied up to one `poll-interval` after it was stored; REST callers
  get `202` and can follow `statusUrl`. If a client must see its change immediately, apply it
  synchronously on the REST path. You then lose the single write path.
- **Retention vs. duplicate detection.** Duplicates are detected only while the inbox row
  exists. Keep `retention` longer than any realistic redelivery or client-retry window. Even
  after cleanup, a very late copy is harmless: its version is not newer, so it gets `SKIPPED`.
- **Same `transactionId`, different content** is treated as a duplicate and ignored. That is the
  contract; a producer bug of this kind has to be found at the source.
- **Deletes are not modelled.** A delete would be an event too (a tombstone with a version, or a
  `status: CLOSED` field in the snapshot), never a hard delete that a late older event could undo.
- **The message key must be the airport code.** The listener checks it, because a wrong key
  puts one airport's events on different partitions and ordering is lost.
- **`@Transactional` and self-invocation.** `InboxCleanupJob.scheduledCleanup()` calls
  `deleteFinishedBefore()` in the same class. That call bypasses the Spring proxy, so a
  `@Transactional` on it would not start a transaction. The transaction therefore sits on the
  repository's delete method.
- **Spring Kafka 4 names the DLT `<topic>-dlt`** (3.x used `<topic>.DLT`). The topic is created
  explicitly with the same partition count as the main topic.
- **Inbox vs. outbox.** The *inbox* makes *receiving* reliable (dedupe, order, retries). The
  *outbox* makes *sending* reliable (publish an event in the same transaction as a data change).
  A service that consumes and publishes usually needs both.

## Tests

`AirportInboxFlowTest` runs against real Postgres and Kafka (Testcontainers, `@ServiceConnection`):

- Kafka event creates the airport with its runways; the inbox row records the Kafka position.
- A redelivered message (same `transactionId`) is stored and applied once.
- A newer version replaces the state; runways are synced by designator (id kept, removed, added).
- Older and equal versions are `SKIPPED` with the reason.
- Malformed JSON, a wrong key and a constraint violation all end in the DLT and never in the inbox.
- REST `PUT` answers `202` with `Location`; the same request again is reported as a duplicate.
- REST and Kafka follow the same version rule.
- Invalid REST requests get `400` with every error; nothing is stored.
- A failing event is retried, becomes `FAILED`, and after fixing the cause a manual retry processes it.
- A broken stored payload fails immediately without retries.
- Four concurrent processors on 40 shuffled versions: every event handled once, the highest version wins.
- Cleanup deletes finished rows, keeps `FAILED` ones, and runs in a transaction from its scheduled entry point.
