ALTER TABLE mileage_workspace_settings
    ALTER COLUMN unit SET DEFAULT 'mile',
    ALTER COLUMN preserve_original_notes SET DEFAULT FALSE,
    ALTER COLUMN rounding_mode SET DEFAULT 'HALF_UP';
