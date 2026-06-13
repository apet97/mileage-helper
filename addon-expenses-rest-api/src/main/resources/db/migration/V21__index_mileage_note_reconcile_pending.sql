-- Speeds the deferred add-on-create note-charge reconcile sweeper.
-- The worker scans unsettled add-on-created converted rows by converted_at.
CREATE INDEX IF NOT EXISTS idx_mileage_conversion_note_reconcile_pending
    ON mileage_conversion (converted_at)
    WHERE source = 'ADDON_FORM'
      AND status = 'CONVERTED'
      AND note_charge_reconciled_at IS NULL;
