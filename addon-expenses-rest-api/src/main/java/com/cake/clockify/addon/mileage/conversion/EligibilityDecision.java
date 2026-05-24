package com.cake.clockify.addon.mileage.conversion;

import com.cake.clockify.addon.mileage.audit.MileageSkipReason;

public record EligibilityDecision(
        boolean eligible,
        boolean dryRun,
        MileageSkipReason skipReason,
        String message
) {
    public static EligibilityDecision eligibleDecision() {
        return new EligibilityDecision(true, false, null, "Eligible");
    }

    public static EligibilityDecision dryRunDecision() {
        return new EligibilityDecision(false, true, MileageSkipReason.DRY_RUN, "Dry-run mode enabled");
    }

    public static EligibilityDecision skipped(MileageSkipReason reason, String message) {
        return new EligibilityDecision(false, false, reason, message);
    }
}
