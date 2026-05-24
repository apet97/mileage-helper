package com.cake.clockify.addon.mileage.api.model;

public record MileagePreviewResponse(
        String miles,
        String rate,
        String calculatedAmount,
        String roundedAmount
) {
}
