package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.cake.clockify.addon.mileage.policy.MileageRateResolution;

import java.util.List;
import java.util.UUID;

public record MileageCreateContextResponse(
        String rate,
        String unit,
        String roundingMode,
        boolean allowUserRateOverride,
        boolean complete,
        List<String> diagnostics,
        String rateSource,
        UUID ratePolicyId,
        String ratePolicyName,
        List<String> warnings
) {
    public static MileageCreateContextResponse from(MileageSettingsValidation settings) {
        return from(settings, null);
    }

    public static MileageCreateContextResponse from(
            MileageSettingsValidation settings,
            MileageRateResolution rateResolution) {
        return new MileageCreateContextResponse(
                settings.rate() == null ? null : settings.rate().stripTrailingZeros().toPlainString(),
                settings.unit(),
                settings.roundingMode().name(),
                settings.allowUserRateOverride(),
                settings.complete(),
                settings.diagnostics(),
                rateResolution == null ? null : rateResolution.source(),
                rateResolution == null ? null : rateResolution.policyId(),
                rateResolution == null ? null : rateResolution.policyName(),
                rateResolution == null ? List.of() : rateResolution.warnings());
    }
}
