UPDATE mileage_conversion
SET status = 'RECEIVED'
WHERE status = 'FETCHED';

ALTER TABLE mileage_conversion
    DROP CONSTRAINT IF EXISTS chk_mileage_conversion_status;

ALTER TABLE mileage_conversion
    ADD CONSTRAINT chk_mileage_conversion_status
    CHECK (status IN (
        'RECEIVED',
        'DRY_RUN',
        'SKIPPED',
        'CONVERTING',
        'CONVERTED',
        'FAILED',
        'DELETED',
        'RESTORED_IGNORED'
    ));

ALTER TABLE mileage_conversion
    DROP COLUMN IF EXISTS currency,
    DROP COLUMN IF EXISTS raw_event_hash,
    DROP COLUMN IF EXISTS clockify_request_id;
