package com.cake.clockify.addon.mileage.packet;

import java.util.UUID;

public record MileageReimbursementPacketRow(
        String expenseDate,
        String userId,
        String userName,
        String projectId,
        String projectName,
        String expenseId,
        String miles,
        String rate,
        String rateSource,
        UUID ratePolicyId,
        String ratePolicyName,
        String calculatedAmount,
        String roundedAmount,
        String clockifyCategoryCharge,
        String billable,
        String status,
        String exceptionReason,
        String skipReason,
        String errorCode,
        String currency,
        String receiptPresent,
        String tripOrigin,
        String tripDestination,
        String tripPurpose,
        String odometerStart,
        String odometerEnd,
        String policyExceptionReason) {
}
