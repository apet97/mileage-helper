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
        List<String> diagnostics,
        String mileageCategoryId,
        String mileageCategoryName,
        String fixedUnit,
        String fixedRoundingMode
) {
    public MileageSettingsResponse withMileageCategoryName(String name) {
        return new MileageSettingsResponse(
                enabled,
                rate,
                unit,
                inputCategoryId,
                outputCategoryId,
                roundingMode,
                convertOnCreate,
                convertOnUpdate,
                preserveOriginalNotes,
                dryRunMode,
                allowUserRateOverride,
                noteTemplate,
                completeForAddonCreate,
                completeForNativeConversion,
                diagnostics,
                mileageCategoryId,
                name,
                fixedUnit,
                fixedRoundingMode);
    }
}
