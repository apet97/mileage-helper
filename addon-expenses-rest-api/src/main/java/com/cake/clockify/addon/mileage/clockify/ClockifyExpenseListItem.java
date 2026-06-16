package com.cake.clockify.addon.mileage.clockify;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Clockify expense as returned by the workspace expense list (all categories), with the
 * project/category names that Clockify includes inline. {@code amount} is in major units (scale 2),
 * derived from the Clockify {@code total} cents value.
 */
public record ClockifyExpenseListItem(
        String expenseId,
        String userId,
        LocalDate date,
        String projectName,
        String categoryId,
        String categoryName,
        BigDecimal amount,
        String currency) {
}
