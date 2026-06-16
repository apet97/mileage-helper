ALTER TABLE mileage_conversion
    ADD COLUMN IF NOT EXISTS trip_origin VARCHAR(256),
    ADD COLUMN IF NOT EXISTS trip_destination VARCHAR(256),
    ADD COLUMN IF NOT EXISTS trip_purpose VARCHAR(256),
    ADD COLUMN IF NOT EXISTS odometer_start NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS odometer_end NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS policy_exception_reason VARCHAR(256);
