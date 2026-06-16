package com.cake.clockify.addon.mileage.api.model;

import java.util.List;
import java.util.UUID;

public record MileagePreviewResponse(
        String miles,
        String rate,
        String calculatedAmount,
        String roundedAmount,
        String rateSource,
        UUID ratePolicyId,
        String ratePolicyName,
        List<String> warnings
) {
}
