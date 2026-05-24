package com.cake.clockify.addon.mileage.api.model;

import jakarta.validation.constraints.NotBlank;

public record CreateMileageExpenseRequest(
        @NotBlank String date,
        String projectId,
        String taskId,
        @NotBlank String miles,
        String rate,
        Boolean billable,
        String notes
) {
}
