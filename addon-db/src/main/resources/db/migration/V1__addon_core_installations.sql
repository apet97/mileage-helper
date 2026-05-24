-- Clockify Add-on Platform — core installations table.
-- One row per workspace. workspace_id is the primary isolation boundary.

CREATE TABLE IF NOT EXISTS addon_installations (
    workspace_id            VARCHAR(64)  PRIMARY KEY,
    addon_key               VARCHAR(128) NOT NULL,
    addon_id                VARCHAR(64),
    installation_token_enc  TEXT         NOT NULL,
    token_key_id            VARCHAR(64)  NOT NULL,
    backend_url             VARCHAR(512) NOT NULL,
    reports_url             VARCHAR(512),
    installer_user_id       VARCHAR(64),
    status                  VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    installed_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_addon_installations_addon_key
    ON addon_installations (addon_key);
CREATE INDEX IF NOT EXISTS idx_addon_installations_status
    ON addon_installations (status);
