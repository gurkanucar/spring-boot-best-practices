-- Airports: the current state, one row per airport. Written ONLY by the inbox processor.
create table airport (
    code                varchar(3)   primary key,           -- IATA code, e.g. IST
    icao_code           varchar(4)   not null unique,       -- e.g. LTFM
    name                varchar(200) not null,
    city                varchar(100) not null,
    country_code        varchar(2)   not null,
    timezone            varchar(50)  not null,
    version             bigint       not null,              -- the SOURCE's version of this airport
    last_transaction_id varchar(100) not null,              -- the event that produced this state
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null
);

create table runway (
    id            bigserial   primary key,
    airport_code  varchar(3)  not null references airport (code) on delete cascade,
    designator    varchar(10) not null,                     -- e.g. 16L/34R
    length_meters integer     not null,
    surface       varchar(20) not null,
    constraint uk_runway_airport_designator unique (airport_code, designator)
);

-- The inbox: every incoming change (Kafka or REST) lands here first.
create table inbox_event (
    id              bigserial     primary key,
    transaction_id  varchar(100)  not null,
    source          varchar(10)   not null,                 -- KAFKA | REST
    airport_code    varchar(3)    not null,
    version         bigint        not null,
    payload         jsonb         not null,                 -- the received event, as received
    status          varchar(20)   not null,                 -- PENDING | PROCESSED | SKIPPED | FAILED
    attempts        integer       not null default 0,
    last_error      varchar(2000),
    kafka_position  varchar(200),                           -- topic-partition@offset, for tracing
    received_at     timestamptz   not null,
    next_attempt_at timestamptz   not null,
    processed_at    timestamptz,
    -- The idempotency guarantee: the same transaction id can be stored only once.
    constraint uk_inbox_transaction_id unique (transaction_id)
);

-- The processor's query: "next PENDING rows that are due". A partial index stays small because
-- finished rows (the vast majority) are not in it.
create index ix_inbox_pending on inbox_event (next_attempt_at, id) where status = 'PENDING';
create index ix_inbox_status on inbox_event (status, id);
