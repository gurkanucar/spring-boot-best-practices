-- Flights: the operational state, owned by this service. Every change also writes an outbox row.
create table flight (
    id                    varchar(20)  primary key,          -- e.g. TK1971-20260924-IST
    carrier_code          varchar(2)   not null,             -- IATA airline code, e.g. TK
    flight_number         varchar(4)   not null,
    departure_date        date         not null,             -- the flight date, local at the origin
    origin                varchar(3)   not null,             -- IATA airport codes
    destination           varchar(3)   not null,
    status                varchar(20)  not null,
    scheduled_departure   timestamptz  not null,
    scheduled_arrival     timestamptz  not null,
    estimated_departure   timestamptz  not null,
    estimated_arrival     timestamptz  not null,
    actual_departure      timestamptz,
    actual_arrival        timestamptz,
    delay_code            varchar(2),                        -- IATA delay code of the latest delay
    terminal              varchar(6),
    gate                  varchar(6),
    aircraft_registration varchar(10)  not null,             -- tail number, e.g. TC-LGA
    aircraft_type         varchar(4)   not null,             -- ICAO type designator, e.g. A21N
    diverted_to           varchar(3),
    cancellation_reason   varchar(200),
    version               bigint       not null,             -- +1 per change, sent with every event
    created_at            timestamptz  not null,
    updated_at            timestamptz  not null
);

create index ix_flight_departure on flight (departure_date, scheduled_departure);

-- The outbox: events waiting to be published, written in the same transaction as the flight change.
create table outbox_event (
    id              bigserial     primary key,
    event_id        uuid          not null,                  -- sent as a header; consumers deduplicate on it
    aggregate_type  varchar(50)   not null,                  -- Flight
    aggregate_id    varchar(100)  not null,                  -- the flight id, also the Kafka message key
    event_type      varchar(50)   not null,
    topic           varchar(200)  not null,
    payload         jsonb         not null,
    status          varchar(10)   not null,                  -- PENDING | SENT
    attempts        integer       not null default 0,
    last_error      varchar(2000),
    kafka_position  varchar(200),                            -- topic-partition@offset once sent
    created_at      timestamptz   not null,
    next_attempt_at timestamptz   not null,
    sent_at         timestamptz,
    constraint uk_outbox_event_id unique (event_id)
);

-- The relay's query: "the oldest pending event of each aggregate, if it is due". Partial indexes stay
-- small because sent rows (the vast majority) are not in them.
create index ix_outbox_pending_aggregate on outbox_event (aggregate_type, aggregate_id, id) where status = 'PENDING';
create index ix_outbox_pending_due on outbox_event (next_attempt_at, id) where status = 'PENDING';
create index ix_outbox_aggregate on outbox_event (aggregate_id, id);
create index ix_outbox_cleanup on outbox_event (sent_at) where status = 'SENT';

create table shedlock (
    name       varchar(64)  not null primary key,
    lock_until timestamp(3) not null,
    locked_at  timestamp(3) not null,
    locked_by  varchar(255) not null
);
