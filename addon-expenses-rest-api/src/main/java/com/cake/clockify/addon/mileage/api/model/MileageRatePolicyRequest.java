package com.cake.clockify.addon.mileage.api.model;

import java.time.LocalDate;

public record MileageRatePolicyRequest(
        String name,
        String rate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active
) {
}
