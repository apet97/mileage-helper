package com.cake.clockify.addon.mileage.api.model;

import jakarta.validation.constraints.NotBlank;

public record MileagePreviewRequest(
        @NotBlank String miles,
        String rate
) {
}
