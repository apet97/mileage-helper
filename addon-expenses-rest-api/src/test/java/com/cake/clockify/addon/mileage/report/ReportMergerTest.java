package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseListItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMergerTest {

    @Test
    void nativeExpenseWithoutConversionRendersNativeValues() {
        List<ReportRow> rows = ReportMerger.merge(
                List.of(new ClockifyExpenseListItem("e2", "u2", LocalDate.parse("2026-05-04"),
                        "", "cat-meals", "Meals", new BigDecimal("18.00"))),
                Map.of(),
                Map.of("u2", "Alan Turing"));

        assertThat(rows).hasSize(1);
        ReportRow row = rows.get(0);
        assertThat(row.mileage()).isFalse();
        assertThat(row.categoryName()).isEqualTo("Meals");
        assertThat(row.amount()).isEqualByComparingTo("18.00");
        assertThat(row.miles()).isNull();
        assertThat(row.rate()).isNull();
        assertThat(row.userName()).isEqualTo("Alan Turing");
    }

    @Test
    void expenseWithConversionRendersAddonReconciledValues() {
        MileageConversion conversion = conversion("e1", "u1", "3.5", "7.2531", "25.38585");

        List<ReportRow> rows = ReportMerger.merge(
                List.of(new ClockifyExpenseListItem("e1", "u1", LocalDate.parse("2026-05-03"),
                        "North Route", "cat-mileage", "Mileage", new BigDecimal("7.25"))),
                Map.of("e1", conversion),
                Map.of("u1", "Ada Lovelace"));

        ReportRow row = rows.get(0);
        assertThat(row.mileage()).isTrue();
        assertThat(row.categoryName()).isEqualTo("Mileage");
        assertThat(row.miles()).isEqualByComparingTo("3.5");
        assertThat(row.rate()).isEqualByComparingTo("7.2531");
        assertThat(row.amount()).isEqualByComparingTo("25.38585"); // OUR calculatedAmount, not the native 7.25
        assertThat(row.projectName()).isEqualTo("North Route");     // project still comes from the native expense
    }

    @Test
    void sortsByDateThenExpenseId() {
        List<ReportRow> rows = ReportMerger.merge(
                List.of(item("e-b", "2026-05-10"), item("e-a", "2026-05-03"), item("e-c", "2026-05-10")),
                Map.of(),
                Map.of());

        assertThat(rows).extracting(ReportRow::expenseId).containsExactly("e-a", "e-b", "e-c");
    }

    @Test
    void mileageOnlyBuildsMileageRowsWithResolvedProjectNamesForDegradedFallback() {
        List<ReportRow> rows = ReportMerger.mileageOnly(
                List.of(conversion("e1", "u1", "2", "7.25", "14.5")),
                Map.of("u1", "Ada Lovelace"),
                Map.of("proj-e1", "North Route"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).mileage()).isTrue();
        assertThat(rows.get(0).categoryName()).isEqualTo("Mileage");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("14.5");
        assertThat(rows.get(0).userName()).isEqualTo("Ada Lovelace");
        assertThat(rows.get(0).projectName()).isEqualTo("North Route"); // resolved from projectId, not dropped
    }

    private static MileageConversion conversion(String expenseId, String userId, String miles, String rate, String calc) {
        MileageConversion conversion = new MileageConversion();
        conversion.setExpenseId(expenseId);
        conversion.setUserId(userId);
        conversion.setProjectId("proj-" + expenseId);
        conversion.setMiles(new BigDecimal(miles));
        conversion.setRate(new BigDecimal(rate));
        conversion.setCalculatedAmount(new BigDecimal(calc));
        conversion.setExpenseDate(LocalDate.parse("2026-05-03"));
        return conversion;
    }

    private static ClockifyExpenseListItem item(String id, String date) {
        return new ClockifyExpenseListItem(id, "u", LocalDate.parse(date), "", "cat", "Cat", new BigDecimal("1.00"));
    }
}
