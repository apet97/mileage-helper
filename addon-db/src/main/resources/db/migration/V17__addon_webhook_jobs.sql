-- Clockify Add-on Platform — async webhook job queue (G1 scale hardening).
-- One row per enqueued webhook delivery. The web controller writes PENDING rows and
-- returns 2xx immediately; a worker claims rows via SELECT … FOR UPDATE SKIP LOCKED
-- and dispatches to the registered AddonWebhookHandler. Survives multi-worker scale-out
-- because PostgreSQL row-level locks combined with SKIP LOCKED guarantee no two
-- workers ever process the same row.

CREATE TABLE IF NOT EXISTS addon_webhook_jobs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    addon_key     VARCHAR(50)  NOT NULL,
    workspace_id  VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    event_id      UUID,
    dedupe_key    VARCHAR(255) NOT NULL,
    payload       BYTEA,
    claims_json   TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    claimed_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_awj_status_created_at
    ON addon_webhook_jobs (status, created_at);
CREATE INDEX IF NOT EXISTS idx_awj_workspace
    ON addon_webhook_jobs (addon_key, workspace_id);
CREATE INDEX IF NOT EXISTS idx_awj_event_id
    ON addon_webhook_jobs (event_id);
CREATE INDEX IF NOT EXISTS idx_awj_claimed_at
    ON addon_webhook_jobs (claimed_at)
    WHERE status = 'CLAIMED';
