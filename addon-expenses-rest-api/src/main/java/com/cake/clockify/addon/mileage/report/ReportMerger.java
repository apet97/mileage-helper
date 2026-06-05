package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseListItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Merges the native Clockify expense list with the add-on's CONVERTED mileage conversions: every
 * expense appears once; an expense that has a conversion (matched by {@code expenseId}) renders the
 * add-on's reconciled mileage values and category "Mileage", everything else renders native values.
 */
final class ReportMerger {
    static final String MILEAGE_CATEGORY_LABEL = "Mileage";

    private ReportMerger() {
    }

    static List<ReportRow> merge(
            List<ClockifyExpenseListItem> expenses,
            Map<String, MileageConversion> conversionsByExpenseId,
            Map<String, String> userNamesById) {
        List<ReportRow> rows = new ArrayList<>();
        for (ClockifyExpenseListItem expense : expenses) {
            MileageConversion conversion = expense.expenseId() == null
                    ? null
                    : conversionsByExpenseId.get(expense.expenseId());
            String userName = userName(expense.userId(), userNamesById);
            if (conversion != null) {
                rows.add(new ReportRow(
                        expense.expenseId(), expense.date(), expense.userId(), userName,
                        expense.projectName(), MILEAGE_CATEGORY_LABEL, true,
                        conversion.getMiles(), conversion.getRate(), orZero(conversion.getCalculatedAmount())));
            } else {
                rows.add(new ReportRow(
                        expense.expenseId(), expense.date(), expense.userId(), userName,
                        expense.projectName(), expense.categoryName(), false,
                        null, null, orZero(expense.amount())));
            }
        }
        rows.sort(ROW_ORDER);
        return rows;
    }

    /** Mileage-only rows for the degraded fallback (when the live expense list cannot be fetched). */
    static List<ReportRow> mileageOnly(
            Iterable<MileageConversion> conversions,
            Map<String, String> userNamesById) {
        List<ReportRow> rows = new ArrayList<>();
        for (MileageConversion conversion : conversions) {
            rows.add(new ReportRow(
                    conversion.getExpenseId(), conversion.getExpenseDate(), conversion.getUserId(),
                    userName(conversion.getUserId(), userNamesById), null, MILEAGE_CATEGORY_LABEL, true,
                    conversion.getMiles(), conversion.getRate(), orZero(conversion.getCalculatedAmount())));
        }
        rows.sort(ROW_ORDER);
        return rows;
    }

    private static final Comparator<ReportRow> ROW_ORDER = Comparator
            .comparing((ReportRow row) -> row.date(), Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(row -> row.expenseId() == null ? "" : row.expenseId());

    private static String userName(String userId, Map<String, String> userNamesById) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        String name = userNamesById.get(userId);
        return name == null || name.isBlank() ? userId : name;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
