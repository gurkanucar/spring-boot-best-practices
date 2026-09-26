-- One row per report: created PENDING by the API, set to READY by the generate job.
create table report (
    id bigint generated always as identity primary key,
    report_type varchar(50) not null,
    requested_by varchar(254) not null,
    status varchar(20) not null,
    content text,
    requested_at timestamptz not null,
    generated_at timestamptz
);
