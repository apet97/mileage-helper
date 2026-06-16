ALTER TABLE mileage_conversion
    ADD COLUMN IF NOT EXISTS rate_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS rate_policy_id UUID,
    ADD COLUMN IF NOT EXISTS rate_policy_name VARCHAR(128);

CREATE INDEX IF NOT EXISTS ix_mileage_conversion_workspace_rate_policy
    ON mileage_conversion(workspace_id, rate_policy_id)
    WHERE rate_policy_id IS NOT NULL;
