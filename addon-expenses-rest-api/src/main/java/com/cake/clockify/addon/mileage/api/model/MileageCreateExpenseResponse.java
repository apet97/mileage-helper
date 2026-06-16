package com.cake.clockify.addon.mileage.api.model;

import java.util.UUID;

public record MileageCreateExpenseResponse(
        String expenseId,
        UUID conversionId,
        String miles,
        String rate,
        String calculatedAmount,
        String roundedAmount,
        String rateSource,
        UUID ratePolicyId,
        String ratePolicyName
) {
}
