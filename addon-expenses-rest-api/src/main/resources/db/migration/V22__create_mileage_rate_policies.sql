CREATE TABLE IF NOT EXISTS mileage_rate_policy (
    id                  UUID PRIMARY KEY,
    workspace_id        VARCHAR(64) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    rate                NUMERIC(18,6) NOT NULL,
    unit                VARCHAR(16) NOT NULL DEFAULT 'mile',
    effective_from      DATE NOT NULL,
    effective_to        DATE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by_user_id  VARCHAR(64),
    CONSTRAINT chk_mileage_rate_policy_name_not_blank CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_mileage_rate_policy_rate_positive CHECK (rate > 0),
    CONSTRAINT chk_mileage_rate_policy_unit CHECK (unit = 'mile'),
    CONSTRAINT chk_mileage_rate_policy_date_order CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS ix_mileage_rate_policy_workspace_effective
    ON mileage_rate_policy(workspace_id, active, effective_from DESC);

CREATE INDEX IF NOT EXISTS ix_mileage_rate_policy_workspace_updated
    ON mileage_rate_policy(workspace_id, updated_at DESC);
