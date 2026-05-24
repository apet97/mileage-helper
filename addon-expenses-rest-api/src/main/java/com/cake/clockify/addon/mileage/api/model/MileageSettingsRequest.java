package com.cake.clockify.addon.mileage.api.model;

public record MileageSettingsRequest(
        Boolean enabled,
        String rate,
        String unit,
        String inputCategoryId,
        String outputCategoryId,
        String mileageCategoryId,
        String roundingMode,
        Boolean convertOnCreate,
        Boolean convertOnUpdate,
        Boolean preserveOriginalNotes,
        Boolean dryRunMode,
        Boolean allowUserRateOverride,
        String noteTemplate
) {
}
