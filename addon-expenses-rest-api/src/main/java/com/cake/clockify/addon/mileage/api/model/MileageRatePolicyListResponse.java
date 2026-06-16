package com.cake.clockify.addon.mileage.api.model;

import java.util.List;

public record MileageRatePolicyListResponse(
        List<MileageRatePolicyResponse> policies,
        String warning
) {
}
