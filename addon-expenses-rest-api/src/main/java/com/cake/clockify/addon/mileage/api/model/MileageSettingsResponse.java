package com.cake.clockify.addon.mileage.api.model;

import java.util.List;

public record MileageSettingsResponse(
        boolean enabled,
        String rate,
        String unit,
        String inputCategoryId,
        String outputCategoryId,
        String roundingMode,
        boolean convertOnCreate,
        boolean convertOnUpdate,
        boolean preserveOriginalNotes,
        boolean dryRunMode,
        boolean allowUserRateOverride,
        String noteTemplate,
        boolean completeForAddonCreate,
        boolean completeForNativeConversion,
        List<String> diagnostics
) {
}
