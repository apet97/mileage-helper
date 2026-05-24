-- Refactor to expense log.
DROP TABLE IF EXISTS temp_addon_expenses_time_entry_log;

CREATE TABLE IF NOT EXISTS temp_addon_expenses_log (
    id                 VARCHAR(64) PRIMARY KEY,
    workspace_id       VARCHAR(64)  NOT NULL,
    user_id            VARCHAR(64)  NOT NULL,
    category_name      VARCHAR(255),
    amount             DOUBLE PRECISION,
    total              DOUBLE PRECISION,
    processed_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
