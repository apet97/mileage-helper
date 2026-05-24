package com.cake.clockify.addon.mileage.clockify;

import java.math.BigDecimal;

public record ClockifyExpenseSnapshot(
        String id,
        String workspaceId,
        String userId,
        String date,
        String projectId,
        String taskId,
        String categoryId,
        String notes,
        BigDecimal quantity,
        Boolean billable,
        String fileId,
        BigDecimal total,
        Boolean locked
) {
}
