package com.cake.clockify.addon.mileage.api.model;

import java.util.Map;

public record MileageErrorResponse(
        String error,
        String message,
        Map<String, String> fields,
        String upstreamMessage,
        String retryAfter
) {
}
