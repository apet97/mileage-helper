-- Clockify Add-on Platform — per-webhook auth tokens.
-- One row per (workspace, addon_key, path). Used by the webhook auth filter
-- to verify inbound webhooks carry the correct auth token for this route.

CREATE TABLE IF NOT EXISTS addon_webhook_tokens (
    id                  BIGSERIAL    PRIMARY KEY,
    workspace_id        VARCHAR(64)  NOT NULL REFERENCES addon_installations(workspace_id) ON DELETE CASCADE,
    addon_key           VARCHAR(128) NOT NULL,
    addon_id            VARCHAR(64),
    path                VARCHAR(256) NOT NULL,
    event_type          VARCHAR(64),
    webhook_token_enc   TEXT         NOT NULL,
    token_key_id        VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_addon_webhook_tokens_workspace_addon_path
        UNIQUE (workspace_id, addon_key, path)
);

CREATE INDEX IF NOT EXISTS idx_addon_webhook_tokens_workspace
    ON addon_webhook_tokens (workspace_id);
