package com.cake.clockify.addon.mileage.workflow;

import java.util.List;

public record MileageWorkflowPreflight(
        boolean locked,
        boolean finalized,
        boolean submitted,
        boolean approved,
        boolean rejected,
        boolean invoiced,
        boolean billable,
        List<String> warnings,
        List<String> blockers
) {
    public boolean blocked() {
        return !blockers.isEmpty();
    }
}
