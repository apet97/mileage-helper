package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.policy.MileageRatePolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MileageRatePolicyResponse(
        UUID id,
        String name,
        String rate,
        String unit,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        String updatedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
    public static MileageRatePolicyResponse from(MileageRatePolicy policy) {
        return new MileageRatePolicyResponse(
                policy.getId(),
                policy.getName(),
                decimalText(policy.getRate()),
                policy.getUnit(),
                policy.getEffectiveFrom(),
                policy.getEffectiveTo(),
                policy.isActive(),
                policy.getUpdatedByUserId(),
                policy.getCreatedAt(),
                policy.getUpdatedAt());
    }

    private static String decimalText(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
