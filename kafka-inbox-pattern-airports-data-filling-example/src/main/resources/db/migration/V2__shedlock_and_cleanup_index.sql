create table shedlock (
    name       varchar(64)  not null primary key,
    lock_until timestamp(3) not null,
    locked_at  timestamp(3) not null,
    locked_by  varchar(255) not null
);

create index ix_inbox_cleanup on inbox_event (processed_at)
    where status in ('PROCESSED', 'SKIPPED');
