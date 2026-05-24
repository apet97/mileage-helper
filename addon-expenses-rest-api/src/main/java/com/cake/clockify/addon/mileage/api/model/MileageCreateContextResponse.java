package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;

import java.util.List;

public record MileageCreateContextResponse(
        String rate,
        String unit,
        String roundingMode,
        boolean allowUserRateOverride,
        boolean complete,
        List<String> diagnostics
) {
    public static MileageCreateContextResponse from(MileageSettingsValidation settings) {
        return new MileageCreateContextResponse(
                settings.rate() == null ? null : settings.rate().stripTrailingZeros().toPlainString(),
                settings.unit(),
                settings.roundingMode().name(),
                settings.allowUserRateOverride(),
                settings.complete(),
                settings.diagnostics());
    }
}
