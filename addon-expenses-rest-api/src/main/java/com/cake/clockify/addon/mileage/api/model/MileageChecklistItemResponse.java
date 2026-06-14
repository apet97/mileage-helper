package com.cake.clockify.addon.mileage.api.model;

public record MileageChecklistItemResponse(
        String key,
        String label,
        boolean complete,
        String action
) {
}
