package com.cake.clockify.addon.mileage.clockify;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record CreateFlatExpenseCommand(
        String categoryId,
        String userId,
        LocalDate date,
        String projectId,
        String taskId,
        BigDecimal amount,
        Boolean billable,
        String notes,
        RoundingMode roundingMode,
        boolean amountIsQuantity
) {
    public CreateFlatExpenseCommand(
            String categoryId,
            String userId,
            LocalDate date,
            String projectId,
            String taskId,
            BigDecimal amount,
            Boolean billable,
            String notes,
            RoundingMode roundingMode) {
        this(categoryId, userId, date, projectId, taskId, amount, billable, notes, roundingMode, false);
    }
}
