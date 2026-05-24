UPDATE mileage_workspace_settings
SET unit = 'mile',
    rounding_mode = 'HALF_UP',
    preserve_original_notes = FALSE,
    input_category_id = COALESCE(NULLIF(input_category_id, ''), NULLIF(output_category_id, '')),
    output_category_id = COALESCE(NULLIF(input_category_id, ''), NULLIF(output_category_id, '')),
    updated_at = NOW()
WHERE unit <> 'mile'
   OR rounding_mode <> 'HALF_UP'
   OR preserve_original_notes = TRUE
   OR (NULLIF(input_category_id, '') IS DISTINCT FROM NULLIF(output_category_id, '')
       AND COALESCE(NULLIF(input_category_id, ''), NULLIF(output_category_id, '')) IS NOT NULL);
