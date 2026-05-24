-- Custom Flyway migration for temp-addon-expenses time entry logs.
CREATE TABLE IF NOT EXISTS temp_addon_expenses_time_entry_log (
    id                 VARCHAR(64) PRIMARY KEY,
    workspace_id       VARCHAR(64)  NOT NULL,
    entry_description  VARCHAR(255),
    user_id            VARCHAR(64)  NOT NULL,
    user_email         VARCHAR(255),
    processed_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
