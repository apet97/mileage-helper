package com.cake.clockify.addon.mileage.api.model;

import jakarta.validation.constraints.NotBlank;

public record MileagePreviewRequest(
        String date,
        @NotBlank String miles,
        String rate
) {
}
