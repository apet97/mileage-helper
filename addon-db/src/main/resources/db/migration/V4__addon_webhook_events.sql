-- Clockify Add-on Platform — webhook event deduplication and tracking.
-- One row per (addon_key, workspace_id, dedupe_key).

CREATE TABLE IF NOT EXISTS addon_webhook_events (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    addon_key               VARCHAR(50)  NOT NULL,
    workspace_id            VARCHAR(64)  NOT NULL,
    event_type              VARCHAR(100) NOT NULL,
    dedupe_key              VARCHAR(255) NOT NULL,
    payload_hash            VARCHAR(64),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    received_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at            TIMESTAMPTZ,
    retry_count             INTEGER      NOT NULL DEFAULT 0,
    error_message_redacted  TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_addon_webhook_event_dedupe
        UNIQUE (addon_key, workspace_id, dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_awe_addon_workspace
    ON addon_webhook_events (addon_key, workspace_id);
CREATE INDEX IF NOT EXISTS idx_awe_status
    ON addon_webhook_events (status);
