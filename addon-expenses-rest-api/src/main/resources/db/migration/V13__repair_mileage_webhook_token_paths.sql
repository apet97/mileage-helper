-- Repair Mileage webhook tokens stored from Clockify INSTALLED payloads whose
-- webhook paths arrived as "//webhook/..." instead of "/webhook/...".

WITH malformed AS (
    SELECT
        id,
        workspace_id,
        addon_key,
        addon_id,
        webhook_token_enc,
        token_key_id,
        CASE path
            WHEN '//webhook/expense-created' THEN '/expense-created'
            WHEN '//webhook/expense-updated' THEN '/expense-updated'
            WHEN '//webhook/expense-deleted' THEN '/expense-deleted'
            WHEN '//webhook/expense-restored' THEN '/expense-restored'
        END AS canonical_path,
        CASE path
            WHEN '//webhook/expense-created' THEN 'EXPENSE_CREATED'
            WHEN '//webhook/expense-updated' THEN 'EXPENSE_UPDATED'
            WHEN '//webhook/expense-deleted' THEN 'EXPENSE_DELETED'
            WHEN '//webhook/expense-restored' THEN 'EXPENSE_RESTORED'
        END AS canonical_event,
        ROW_NUMBER() OVER (
            PARTITION BY workspace_id, addon_key,
                CASE path
                    WHEN '//webhook/expense-created' THEN '/expense-created'
                    WHEN '//webhook/expense-updated' THEN '/expense-updated'
                    WHEN '//webhook/expense-deleted' THEN '/expense-deleted'
                    WHEN '//webhook/expense-restored' THEN '/expense-restored'
                END
            ORDER BY updated_at DESC, id DESC
        ) AS row_number
    FROM addon_webhook_tokens
    WHERE addon_key = 'mileage-for-clockify'
      AND path IN (
          '//webhook/expense-created',
          '//webhook/expense-updated',
          '//webhook/expense-deleted',
          '//webhook/expense-restored')
),
latest_malformed AS (
    SELECT *
    FROM malformed
    WHERE row_number = 1
)
UPDATE addon_webhook_tokens canonical
SET webhook_token_enc = latest_malformed.webhook_token_enc,
    token_key_id = latest_malformed.token_key_id,
    addon_id = COALESCE(latest_malformed.addon_id, canonical.addon_id),
    event_type = latest_malformed.canonical_event,
    updated_at = NOW()
FROM latest_malformed
WHERE canonical.workspace_id = latest_malformed.workspace_id
  AND canonical.addon_key = latest_malformed.addon_key
  AND canonical.path = latest_malformed.canonical_path;

DELETE FROM addon_webhook_tokens malformed
USING addon_webhook_tokens canonical
WHERE malformed.addon_key = 'mileage-for-clockify'
  AND malformed.path IN (
      '//webhook/expense-created',
      '//webhook/expense-updated',
      '//webhook/expense-deleted',
      '//webhook/expense-restored')
  AND canonical.workspace_id = malformed.workspace_id
  AND canonical.addon_key = malformed.addon_key
  AND canonical.path = CASE malformed.path
      WHEN '//webhook/expense-created' THEN '/expense-created'
      WHEN '//webhook/expense-updated' THEN '/expense-updated'
      WHEN '//webhook/expense-deleted' THEN '/expense-deleted'
      WHEN '//webhook/expense-restored' THEN '/expense-restored'
  END;

UPDATE addon_webhook_tokens
SET path = CASE path
        WHEN '//webhook/expense-created' THEN '/expense-created'
        WHEN '//webhook/expense-updated' THEN '/expense-updated'
        WHEN '//webhook/expense-deleted' THEN '/expense-deleted'
        WHEN '//webhook/expense-restored' THEN '/expense-restored'
    END,
    event_type = CASE path
        WHEN '//webhook/expense-created' THEN 'EXPENSE_CREATED'
        WHEN '//webhook/expense-updated' THEN 'EXPENSE_UPDATED'
        WHEN '//webhook/expense-deleted' THEN 'EXPENSE_DELETED'
        WHEN '//webhook/expense-restored' THEN 'EXPENSE_RESTORED'
    END,
    updated_at = NOW()
WHERE addon_key = 'mileage-for-clockify'
  AND path IN (
      '//webhook/expense-created',
      '//webhook/expense-updated',
      '//webhook/expense-deleted',
      '//webhook/expense-restored');

UPDATE addon_webhook_tokens
SET event_type = CASE path
        WHEN '/expense-created' THEN 'EXPENSE_CREATED'
        WHEN '/expense-updated' THEN 'EXPENSE_UPDATED'
        WHEN '/expense-deleted' THEN 'EXPENSE_DELETED'
        WHEN '/expense-restored' THEN 'EXPENSE_RESTORED'
    END,
    updated_at = NOW()
WHERE addon_key = 'mileage-for-clockify'
  AND path IN (
      '/expense-created',
      '/expense-updated',
      '/expense-deleted',
      '/expense-restored')
  AND (event_type IS NULL OR event_type = '');
