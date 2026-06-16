package com.cake.clockify.addon.mileage.conversion;

import com.cake.clockify.addon.mileage.audit.MileageSkipReason;

import java.util.List;

public record EligibilityDecision(
        boolean eligible,
        boolean dryRun,
        MileageSkipReason skipReason,
        String message,
        List<String> warnings
) {
    public static EligibilityDecision eligibleDecision() {
        return eligibleDecision(List.of());
    }

    public static EligibilityDecision eligibleDecision(List<String> warnings) {
        return new EligibilityDecision(true, false, null, "Eligible", List.copyOf(warnings));
    }

    public static EligibilityDecision dryRunDecision() {
        return new EligibilityDecision(false, true, MileageSkipReason.DRY_RUN, "Dry-run mode enabled", List.of());
    }

    public static EligibilityDecision skipped(MileageSkipReason reason, String message) {
        return new EligibilityDecision(false, false, reason, message, List.of());
    }
}
