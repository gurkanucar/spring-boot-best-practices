create table report_request (
    id bigint generated always as identity primary key,
    report_type varchar(50) not null,
    requested_by varchar(254) not null,
    created_at timestamptz not null
);

-- One report per request: the unique constraint backs up concurrent generate executions.
create table report (
    id uuid primary key,
    report_request_id bigint not null references report_request(id),
    content text not null,
    generated_at timestamptz not null,
    constraint uk_report_request unique (report_request_id)
);
