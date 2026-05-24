package com.cake.clockify.addon.mileage.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record MileageSettingsValidation(
        String workspaceId,
        boolean enabled,
        boolean complete,
        BigDecimal rate,
        String unit,
        String inputCategoryId,
        String outputCategoryId,
        RoundingMode roundingMode,
        boolean convertOnCreate,
        boolean convertOnUpdate,
        boolean preserveOriginalNotes,
        boolean dryRunMode,
        boolean allowUserRateOverride,
        String noteTemplate,
        List<String> diagnostics
) {
}
