create table report_request (
    id bigint generated always as identity primary key,
    report_type varchar(50) not null,
    requested_by varchar(254) not null,
    created_at timestamptz not null
);
create index ix_report_request_user_time on report_request (requested_by, created_at);

create table report (
    id uuid primary key,
    report_request_id bigint not null references report_request(id),
    content text not null,
    generated_at timestamptz not null,
    constraint uk_report_request unique (report_request_id)
);

-- Only the durable handoff to JobRunr. Execution state, attempts, locks and errors live in JobRunr.
create table task_submission (
    id uuid primary key,
    type varchar(30) not null,
    payload text not null,
    created_at timestamptz not null,
    submitted_at timestamptz
);
create index ix_task_submission_pending on task_submission (submitted_at, created_at);
