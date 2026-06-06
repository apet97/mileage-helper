-- Tracks when the deferred add-on-create note-charge reconcile sweeper has processed a row.
-- NULL = not yet processed; non-null = the sweeper has stamped it (annotated or decided no annotation needed).
ALTER TABLE mileage_conversion
    ADD COLUMN IF NOT EXISTS note_charge_reconciled_at TIMESTAMP NULL;
