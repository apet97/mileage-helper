package com.cake.clockify.addon.mileage.clockify;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record UpdateFlatExpenseCommand(
        String categoryId,
        String userId,
        String date,
        String projectId,
        String taskId,
        Boolean billable,
        BigDecimal amount,
        String notes,
        RoundingMode roundingMode,
        boolean amountIsQuantity
) {
    public UpdateFlatExpenseCommand(
            String categoryId,
            String userId,
            String date,
            String projectId,
            String taskId,
            Boolean billable,
            BigDecimal amount,
            String notes,
            RoundingMode roundingMode) {
        this(categoryId, userId, date, projectId, taskId, billable, amount, notes, roundingMode, false);
    }
}
