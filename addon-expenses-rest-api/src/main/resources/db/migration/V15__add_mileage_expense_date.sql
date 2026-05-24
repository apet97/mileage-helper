ALTER TABLE mileage_conversion
    ADD COLUMN IF NOT EXISTS expense_date DATE;

UPDATE mileage_conversion
SET expense_date = (COALESCE(converted_at, updated_at, created_at) AT TIME ZONE 'UTC')::DATE
WHERE expense_date IS NULL;

CREATE INDEX IF NOT EXISTS ix_mileage_conversion_workspace_expense_date
    ON mileage_conversion(workspace_id, expense_date DESC, updated_at DESC);

CREATE INDEX IF NOT EXISTS ix_mileage_conversion_workspace_user_expense_date
    ON mileage_conversion(workspace_id, user_id, expense_date DESC, updated_at DESC);

CREATE INDEX IF NOT EXISTS ix_mileage_conversion_workspace_status_expense_date
    ON mileage_conversion(workspace_id, status, expense_date DESC, updated_at DESC);
