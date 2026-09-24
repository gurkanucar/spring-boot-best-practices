# Spring Boot + Kafka: Flight Updates Outbox

A flight operations service. REST commands change flights (delay, gate change, boarding,
departure...). Every change is published to Kafka through a transactional outbox.

```text
POST /api/flights/{id}/delay â”€â”€ FlightCommandService â”€â”¬â”€ flight          (one DB transaction)
                                                      â””â”€ outbox_event   â”€â”˜
                                                              â”‚
                         OutboxRelay â”€â”€ OutboxPublisher â”€â”€ OutboxKafkaSender â”€â”€ Kafka flight-updates
```

Spring Boot 4.1.1, Java 25, PostgreSQL 17, Spring Kafka, Flyway, ShedLock and Testcontainers.

## Why an outbox

A command must change the database **and** tell other systems. Doing both directly is unsafe:

- send to Kafka, then commit: the commit fails, and the display boards show a delay that never happened;
- commit, then send to Kafka: the app crashes in between, and the passengers are never told.

Here the command only writes to PostgreSQL: the flight change and an `outbox_event` row, in one
transaction. Both are committed or neither is. A relay publishes the committed rows afterwards.

## Run

```bash
docker compose up -d
./mvnw spring-boot:run
```

The app listens on port **8097**, PostgreSQL on **5434**, Kafka on **9092**, and Kafka UI on **8085**
(the same infrastructure setup as the inbox example; run one of the two at a time).
On Windows use `mvnw.cmd`. Tests require Java 25 and Docker:

```bash
./mvnw test
```

## A day of operations: TK1971 Istanbul â†’ London Heathrow

```bash
# schedule the flight â†’ FLIGHT_SCHEDULED
curl -X POST http://localhost:8097/api/flights -H "Content-Type: application/json" -d '{
  "carrierCode": "TK", "flightNumber": "1971", "departureDate": "2026-09-24",
  "origin": "IST", "destination": "LHR",
  "scheduledDeparture": "2026-09-24T18:40:00Z", "scheduledArrival": "2026-09-24T22:30:00Z",
  "terminal": "1", "gate": "A5", "aircraftRegistration": "TC-LGA", "aircraftType": "A21N"}'

F=http://localhost:8097/api/flights/TK1971-20260924-IST

# late inbound aircraft (IATA delay code 93): the estimated arrival moves by the same hour
curl -X POST $F/delay -H "Content-Type: application/json" \
  -d '{"estimatedDeparture": "2026-09-24T19:40:00Z", "delayCode": "93", "reason": "Late inbound aircraft"}'

curl -X PUT $F/gate     -H "Content-Type: application/json" -d '{"terminal": "1", "gate": "F12"}'
curl -X PUT $F/aircraft -H "Content-Type: application/json" -d '{"registration": "TC-LSB", "type": "A21N"}'
curl -X POST $F/boarding
curl -X POST $F/departure -H "Content-Type: application/json" -d '{"at": "2026-09-24T19:52:00Z"}'
curl -X POST $F/arrival   -H "Content-Type: application/json" -d '{"at": "2026-09-24T23:41:00Z"}'
```

Open Kafka UI at http://localhost:8085, topic `flight-updates`: seven messages with the key
`TK1971-20260924-IST`, versions 1 to 7, on one partition.

| Endpoint | Event |
|---|---|
| `POST /api/flights` | `FLIGHT_SCHEDULED` |
| `POST /api/flights/{id}/delay` | `FLIGHT_DELAYED` |
| `PUT /api/flights/{id}/gate` | `GATE_CHANGED` |
| `PUT /api/flights/{id}/aircraft` | `AIRCRAFT_CHANGED` (tail swap) |
| `POST /api/flights/{id}/boarding` | `BOARDING_STARTED` (needs a gate) |
| `POST /api/flights/{id}/departure` | `FLIGHT_DEPARTED` (off-block; `at` defaults to now) |
| `POST /api/flights/{id}/arrival` | `FLIGHT_ARRIVED` (on-block; at the diversion airport if diverted) |
| `POST /api/flights/{id}/cancellation` | `FLIGHT_CANCELLED` |
| `POST /api/flights/{id}/diversion` | `FLIGHT_DIVERTED` |

Lifecycle: `SCHEDULED â†’ BOARDING â†’ DEPARTED â†’ ARRIVED`. A flight can be cancelled before departure
and diverted after it. A command that does not fit the current status is `409 Conflict`; a
broken rule (e.g. arrival before departure) is `422`; invalid fields are `400`. None of them
writes an event. A gate or aircraft change to the current value changes nothing and publishes nothing.

| Endpoint | Result |
|---|---|
| `GET /api/flights?date=2026-09-24` | Flights of a day |
| `GET /api/flights/{id}` | Current flight state |
| `GET /api/outbox?aggregateId={id}` | The flight's events and their publishing status |
| `GET /api/outbox?status=PENDING` | Latest 100 unpublished events |
| `GET /api/outbox?aggregateId={id}&status=PENDING` | Unpublished events of that flight |

## The message

Key: the flight id. Headers: `eventId`, `eventType`, `aggregateType`. Value:

```json
{
  "eventId": "5f0c7c1e-...",
  "eventType": "GATE_CHANGED",
  "flightId": "TK1971-20260924-IST",
  "version": 3,
  "occurredAt": "2026-09-24T17:05:12.345Z",
  "flight": { "status": "SCHEDULED", "gate": "F12", "estimatedDeparture": "...", "...": "full snapshot" },
  "change": { "previousTerminal": "1", "previousGate": "A5", "terminal": "1", "gate": "F12" }
}
```

`flight` is the complete state after the change, so a consumer that only needs the current state
(a display board) just stores it. `change` says what happened, for consumers that react to it
(a passenger notification for a gate change).

**What consumers must do.** Delivery is at-least-once: deduplicate on `eventId`.
A consumer maintaining current flight state can also ignore snapshots whose `version` is not newer.
A consumer reacting to every change (such as notifications) must deduplicate each event and preserve
its processing order, rather than discard distinct changes just because a newer snapshot arrived.
The [inbox example](../kafka-inbox-pattern-airports-data-filling-example) demonstrates the receiving
pattern with a different airport event contract; it is not directly subscribed to this topic.

## The important parts

**Writing.** `FlightCommandService` locks the flight row (`SELECT ... FOR UPDATE`), applies the
change on the `Flight` entity (which checks the lifecycle and increments `version`), and creates
one typed `FlightEvent`. `OutboxWriter.append(event)` serializes and stores it in that transaction.
The writer uses `Propagation.MANDATORY`: it refuses to run outside the business transaction.
There is no intermediate recorder or generic event-writing API.

**Ordering.** All events of a flight use the flight id as the Kafka key, so they land on one
partition. Within the outbox, the flight lock serializes commands: the next command's outbox row
is inserted only after the previous transaction commits, so ids increase in commit order.
(Across different flights, a larger id can commit first. That is fine; order only matters per flight.)

**Publishing.** `OutboxRelay` waits 2 seconds between polls and calls `OutboxPublisher.publishNext()`
up to `batch-size` times. Each call runs in its **own transaction**, claims one row, waits for
Kafka's acknowledgement, marks the row `SENT`, and commits before the next call.

The claim query uses `FOR UPDATE SKIP LOCKED` and excludes an event when an earlier `PENDING`
event exists for the same flight. Another relay can claim a different flight, but cannot skip
past a locked or backing-off event to publish that flight's next version. This needs one query
and one row lock per event; there is no separate aggregate-claim pass or nested publishing loop.

**Failures.** When a send fails, its row stays `PENDING` with `attempts + 1`, `last_error`, and
exponential backoff (1s, 2s, 4s ... up to 1 minute). The current poll stops. Other flights are
eligible on the next poll while that event backs off. A successful retry clears `last_error`.

There is no `FAILED` state or producer-side DLT: the committed payload is already stored in the
outbox and is retried until it can be published. A permanently invalid event blocks later events
of that flight, so monitor pending age and attempts and correct the cause. Pending events are
never silently dropped or removed by cleanup.

**At-least-once.** If the database commit fails after Kafka acknowledged a send, that event stays
`PENDING` and is sent again with the same `eventId`. Earlier events already committed as `SENT`
are unaffected. Producer idempotence covers Kafka's internal retries, not a new send after an
application restart or DB rollback. Timeouts can also have an uncertain delivery outcome, so
consumers must tolerate duplicates, including late copies.

**Transaction length.** A row lock is held while waiting for one Kafka send. The producer's
`max.block.ms` and `delivery.timeout.ms`, plus the sender's acknowledgement timeout, bound the
normal wait. `batch-size` caps work per poll, not the size of a database transaction. There is
one commit per event: a deliberate throughput tradeoff for straightforward locking and recovery.
See [Kafka producer configuration](https://kafka.apache.org/40/configuration/producer-configs/).

## Multiple instances and cleanup

All instances share PostgreSQL. Each runs its own relay; row locks let them publish different
flights in parallel, so the relay intentionally has no ShedLock.

Cleanup runs daily at **03:00 UTC** and deletes `SENT` rows whose `sent_at` is older than
`retention` (default **7 days**). Pending rows are never deleted. `OutboxCleanupJob` uses
`@SchedulerLock(name = "flight-outbox-cleanup")` with the database clock, like the inbox example.

To try two instances locally:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8097"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8098"
```

## Scope

Commands are not idempotent: a client retry of a delay publishes a second `FLIGHT_DELAYED`
(with a newer version). A production API would add an `Idempotency-Key`.
The flight date is given by the client (local at the origin); there is no airport time zone data.
Polling is simple and database-only; change data capture (e.g. Debezium reading the outbox table)
is the alternative when polling latency or load matters.

## Configuration

```yaml
app:
  kafka:
    flight-updates-topic: flight-updates
  outbox:
    poll-interval: 2s
    batch-size: 100
    send-timeout: 15s
    retry-backoff: 1s
    max-backoff: 1m
    retention: 7d
    cleanup-cron: "0 0 3 * * *" # UTC
```

## Tests

Integration tests use real PostgreSQL and Kafka. They cover a full day of operations published
in order on one partition, diversion, rejected and no-op commands publishing nothing, and a
rolled-back business transaction discarding its event. They also cover four extra relays racing
the scheduled one without duplicates or reordering.
Relay tests drive the publisher manually with a spied Kafka sender: a failing flight keeps its
events pending and ordered while other flights continue, and uncommitted events are invisible to
the relay. A blocked sender test verifies another relay can publish a different flight without
overtaking the locked event. A post-send rollback test verifies stable event IDs on redelivery and
preservation of previously committed sends. Inspection tests combine flight and status filters.
A cleanup test checks the shared lock and retention rules; a unit test checks the backoff.
