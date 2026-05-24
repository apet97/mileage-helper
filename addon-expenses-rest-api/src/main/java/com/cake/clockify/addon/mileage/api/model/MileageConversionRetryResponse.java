package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import com.cake.clockify.addon.mileage.conversion.ConversionResult;

import java.util.UUID;

public record MileageConversionRetryResponse(
        UUID conversionId,
        String expenseId,
        MileageConversionStatus status,
        MileageSkipReason skipReason,
        String message
) {
    public static MileageConversionRetryResponse from(ConversionResult result) {
        return new MileageConversionRetryResponse(
                result.conversionId(),
                result.expenseId(),
                result.status(),
                result.skipReason(),
                result.message());
    }
}
