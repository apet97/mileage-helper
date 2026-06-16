package com.cake.clockify.addon.mileage.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MileageRateResolution(
        BigDecimal rate,
        String rateText,
        String source,
        UUID policyId,
        String policyName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<String> warnings
) {
    public static final String SOURCE_POLICY = "POLICY";
    public static final String SOURCE_SETTINGS_FALLBACK = "SETTINGS_FALLBACK";
    public static final String SOURCE_USER_OVERRIDE = "USER_OVERRIDE";

    static MileageRateResolution policy(MileageRatePolicy policy) {
        return new MileageRateResolution(
                policy.getRate(),
                decimalText(policy.getRate()),
                SOURCE_POLICY,
                policy.getId(),
                policy.getName(),
                policy.getEffectiveFrom(),
                policy.getEffectiveTo(),
                List.of());
    }

    static MileageRateResolution settingsFallback(BigDecimal rate) {
        return new MileageRateResolution(
                rate,
                decimalText(rate),
                SOURCE_SETTINGS_FALLBACK,
                null,
                null,
                null,
                null,
                List.of());
    }

    public static MileageRateResolution userOverride(BigDecimal rate) {
        return new MileageRateResolution(
                rate,
                decimalText(rate),
                SOURCE_USER_OVERRIDE,
                null,
                null,
                null,
                null,
                List.of());
    }

    static MileageRateResolution incomplete(List<String> warnings) {
        return new MileageRateResolution(null, null, SOURCE_SETTINGS_FALLBACK, null, null, null, null, warnings);
    }

    private static String decimalText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
