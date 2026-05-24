package com.cake.clockify.addon.mileage.conversion;

import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;

import java.util.UUID;

public record ConversionResult(
        UUID conversionId,
        String expenseId,
        MileageConversionStatus status,
        MileageSkipReason skipReason,
        String message
) {
}
