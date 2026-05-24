CREATE TABLE IF NOT EXISTS mileage_workspace_settings (
    workspace_id                 VARCHAR(64) PRIMARY KEY,
    enabled                      BOOLEAN NOT NULL DEFAULT TRUE,
    rate                         NUMERIC(18,6),
    unit                         VARCHAR(16) NOT NULL DEFAULT 'mi',
    input_category_id            VARCHAR(64),
    output_category_id           VARCHAR(64),
    rounding_mode                VARCHAR(32) NOT NULL DEFAULT 'HALF_UP',
    convert_on_create            BOOLEAN NOT NULL DEFAULT TRUE,
    convert_on_update            BOOLEAN NOT NULL DEFAULT TRUE,
    preserve_original_notes      BOOLEAN NOT NULL DEFAULT TRUE,
    dry_run_mode                 BOOLEAN NOT NULL DEFAULT FALSE,
    allow_user_rate_override     BOOLEAN NOT NULL DEFAULT FALSE,
    note_template                TEXT,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by_user_id           VARCHAR(64),
    CONSTRAINT chk_mileage_rate_positive CHECK (rate IS NULL OR rate > 0),
    CONSTRAINT chk_mileage_rounding_mode CHECK (rounding_mode IN ('UP','DOWN','CEILING','FLOOR','HALF_UP','HALF_DOWN','HALF_EVEN'))
);

CREATE TABLE IF NOT EXISTS mileage_conversion (
    id                           UUID PRIMARY KEY,
    workspace_id                 VARCHAR(64) NOT NULL,
    expense_id                   VARCHAR(64) NOT NULL,
    source                       VARCHAR(32) NOT NULL,
    source_event_type            VARCHAR(64),
    source_category_id           VARCHAR(64),
    target_category_id           VARCHAR(64),
    user_id                      VARCHAR(64),
    project_id                   VARCHAR(64),
    task_id                      VARCHAR(64),
    miles                        NUMERIC(18,6),
    rate                         NUMERIC(18,6),
    calculated_amount            NUMERIC(18,6),
    rounded_amount               NUMERIC(18,2),
    currency                     VARCHAR(16),
    rounding_mode                VARCHAR(32),
    status                       VARCHAR(32) NOT NULL,
    skip_reason                  VARCHAR(128),
    error_code                   VARCHAR(128),
    error_message                TEXT,
    note_marker                  VARCHAR(128),
    raw_event_hash               VARCHAR(128),
    clockify_request_id          VARCHAR(128),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    converted_at                 TIMESTAMPTZ,
    deleted_at                   TIMESTAMPTZ,
    CONSTRAINT chk_mileage_conversion_source CHECK (source IN ('ADDON_FORM','WEBHOOK_CREATED','WEBHOOK_UPDATED','WEBHOOK_RESTORED')),
    CONSTRAINT chk_mileage_conversion_status CHECK (status IN ('RECEIVED','FETCHED','DRY_RUN','SKIPPED','CONVERTING','CONVERTED','FAILED','DELETED','RESTORED_IGNORED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mileage_conversion_workspace_expense
    ON mileage_conversion(workspace_id, expense_id);

CREATE INDEX IF NOT EXISTS ix_mileage_conversion_workspace_status
    ON mileage_conversion(workspace_id, status);

CREATE INDEX IF NOT EXISTS ix_mileage_conversion_workspace_created
    ON mileage_conversion(workspace_id, created_at DESC);
