-- Mileage for Clockify schema draft

create table if not exists mileage_workspace_settings (
    workspace_id varchar(64) primary key,
    enabled boolean not null default true,
    rate numeric(18,6) not null,
    unit varchar(16) not null default 'mi',
    input_category_id varchar(64),
    output_category_id varchar(64) not null,
    rounding_mode varchar(32) not null default 'HALF_UP',
    convert_on_create boolean not null default true,
    convert_on_update boolean not null default true,
    preserve_original_notes boolean not null default true,
    dry_run_mode boolean not null default false,
    allow_user_rate_override boolean not null default false,
    note_template text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    updated_by_user_id varchar(64)
);

create table if not exists mileage_conversion (
    id uuid primary key,
    workspace_id varchar(64) not null,
    expense_id varchar(64) not null,
    source varchar(32) not null,
    source_event_type varchar(64),
    source_category_id varchar(64),
    target_category_id varchar(64),
    user_id varchar(64),
    project_id varchar(64),
    task_id varchar(64),
    miles numeric(18,6),
    rate numeric(18,6),
    calculated_amount numeric(18,6),
    rounded_amount numeric(18,2),
    currency varchar(16),
    rounding_mode varchar(32),
    status varchar(32) not null,
    skip_reason varchar(128),
    error_code varchar(128),
    error_message text,
    note_marker varchar(128),
    raw_event_hash varchar(128),
    clockify_request_id varchar(128),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    converted_at timestamp,
    deleted_at timestamp
);

create unique index if not exists ux_mileage_conversion_workspace_expense
    on mileage_conversion(workspace_id, expense_id);

create index if not exists ix_mileage_conversion_workspace_status
    on mileage_conversion(workspace_id, status);

create index if not exists ix_mileage_conversion_workspace_created
    on mileage_conversion(workspace_id, created_at desc);

-- Optional, if detailed webhook delivery diagnostics are needed.
create table if not exists mileage_webhook_event_log (
    id uuid primary key,
    workspace_id varchar(64) not null,
    event_type varchar(64) not null,
    expense_id varchar(64),
    event_hash varchar(128),
    status varchar(32) not null,
    received_at timestamp not null default now(),
    processed_at timestamp,
    error_code varchar(128),
    error_message text
);

create unique index if not exists ux_mileage_webhook_event_hash
    on mileage_webhook_event_log(workspace_id, event_hash)
    where event_hash is not null;
