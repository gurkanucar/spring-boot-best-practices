-- Demo business data for the report tasks: one row per report, PENDING until generated.
create table report (
    id           bigint       generated always as identity primary key,
    report_type  varchar(50)  not null,                    -- e.g. MONTHLY_SALES
    requested_by varchar(200) not null,                    -- email address to notify
    status       varchar(20)  not null,                    -- PENDING, READY
    content      text,                                     -- null until READY
    requested_at timestamptz  not null,
    generated_at timestamptz                               -- null until READY
);
