create table background_task (
    id              uuid         primary key,
    type            varchar(50)  not null,                 -- REPORT_GENERATION, EMAIL_SEND, ...
    payload         jsonb        not null,                 -- ids only, never large objects
    status          varchar(20)  not null,                 -- PENDING | RUNNING | SUCCEEDED | FAILED | DEAD
    attempts        integer      not null default 0,       -- executions started so far
    max_attempts    integer      not null default 5,
    run_at          timestamptz  not null,                 -- eligible from this moment
    locked_at       timestamptz,                           -- set while RUNNING
    locked_by       varchar(255),                          -- instance id of the worker
    last_error      text,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    idempotency_key varchar(200)                           -- optional; one task per key
);

-- The poller's query: "PENDING and due, oldest first".
create index ix_background_task_status_run_at on background_task (status, run_at);
-- Unique: the database, not a check-then-insert, prevents duplicates. NULL keys never conflict.
create unique index ux_background_task_idempotency_key on background_task (idempotency_key);

create table shedlock (
    name       varchar(64)  not null primary key,
    lock_until timestamp(3) not null,
    locked_at  timestamp(3) not null,
    locked_by  varchar(255) not null
);
