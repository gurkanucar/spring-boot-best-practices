-- Demo business data for the task handlers.
create table report_request (
    id           uuid         primary key,
    report_type  varchar(50)  not null,                    -- e.g. MONTHLY_SALES
    requested_by varchar(200) not null,                    -- email address to notify
    created_at   timestamptz  not null
);

create table report (
    id                uuid        primary key,
    report_request_id uuid        not null references report_request (id),
    content           text        not null,
    generated_at      timestamptz not null,
    -- One report per request, even if a generation task runs twice.
    constraint uk_report_request unique (report_request_id)
);

-- The rate limit check: "requests of this user in the last 10 minutes".
create index ix_report_request_user_time on report_request (requested_by, created_at);
