-- Clockify Add-on Platform — workspace settings table.
-- One row per (addon_key, workspace_id, setting_key).

CREATE TABLE IF NOT EXISTS addon_workspace_settings (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    addon_key           VARCHAR(50)  NOT NULL,
    workspace_id        VARCHAR(64)  NOT NULL,
    setting_key         VARCHAR(255) NOT NULL,
    value_json          TEXT,
    value_type          VARCHAR(50),
    updated_by_user_id  VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_addon_workspace_setting
        UNIQUE (addon_key, workspace_id, setting_key)
);

CREATE INDEX IF NOT EXISTS idx_aws_addon_workspace
    ON addon_workspace_settings (addon_key, workspace_id);
