package com.cake.clockify.addon.mileage.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One rendered report line. Mileage rows ({@code mileage == true}) carry the add-on's reconciled
 * {@code miles}/{@code rate}/{@code amount} and category "Mileage"; non-mileage rows carry the native
 * Clockify category and amount with {@code miles}/{@code rate} left null. All money is {@link BigDecimal}.
 */
public record ReportRow(
        String expenseId,
        LocalDate date,
        String userId,
        String userName,
        String projectName,
        String categoryName,
        boolean mileage,
        BigDecimal miles,
        BigDecimal rate,
        BigDecimal amount) {
}
