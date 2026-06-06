package com.cake.clockify.addon.mileage.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MileageReportRendererTest {

    private static final LocalDate FROM = LocalDate.parse("2026-05-01");
    private static final LocalDate TO = LocalDate.parse("2026-05-31");

    @Test
    void rendersColumnsMileageNativeRowsTotalsAndEscapes() {
        String html = MileageReportRenderer.render(
                "All users", FROM, TO,
                List.of(
                        mileage("e1", "2026-05-03", "Ada <Lovelace>", "North <b>Route</b>", "3.5", "7.2531", "25.38585"),
                        native_("e2", "2026-05-04", "Alan Turing", "", "Meals", "18.00")),
                true, false, false, false);

        assertThat(html).contains("Expense Report");
        assertThat(html).contains("2026-05-01 to 2026-05-31");
        // all-users mode includes the User column
        assertThat(html).contains("<th>User</th>");
        assertThat(html).contains("Ada &lt;Lovelace&gt;");
        assertThat(html).contains("North &lt;b&gt;Route&lt;/b&gt;");
        // mileage row carries miles/rate at natural precision; the Amount column is money at 2 dp
        // (25.38585 -> 25.39); the native row leaves miles/rate blank
        assertThat(html).contains("<td class=\"num\">3.5</td><td class=\"num\">7.2531</td><td class=\"num\">25.39</td>");
        assertThat(html).contains("<td>Meals</td><td class=\"num\"></td><td class=\"num\"></td><td class=\"num\">18.00</td>");
        // totals sum the displayed 2 dp amounts (25.39 + 18.00) under a 4-column "Total" (Date|User|Project|Category)
        assertThat(html).contains("<th colspan=\"4\">Total</th><td class=\"num\"></td><td class=\"num\"></td><td class=\"num\">43.39</td>");
        // CSP-safe
        assertThat(html).contains("<link rel=\"stylesheet\" href=\"/assets/mileage/report.css\">");
        assertThat(html).contains("src=\"/assets/mileage/report.js\" defer");
        assertThat(html).contains("id=\"btn-print\"");
        assertThat(html).doesNotContain("<style");
        assertThat(html).doesNotContain("onclick=");
    }

    @Test
    void declaresFaviconLinkToStopAutoFaviconRequest() {
        String html = MileageReportRenderer.render(
                "All users", FROM, TO,
                List.of(mileage("e1", "2026-05-03", "Ada", "Route", "1", "7.25", "7.25")),
                true, false, false, false);

        assertThat(html).contains("<link rel=\"icon\" type=\"image/png\" href=\"/assets/mileage/icon.png\">");
        // still CSP-safe
        assertThat(html).doesNotContain("<style");
    }

    @Test
    void omitsUserColumnInSingleUserMode() {
        String html = MileageReportRenderer.render(
                "Ada Lovelace", FROM, TO,
                List.of(mileage("e1", "2026-05-03", "Ada Lovelace", "Route", "1", "7.25", "7.25")),
                false, false, false, false);

        assertThat(html).doesNotContain("<th>User</th>");
        // 3-column "Total" (Date|Project|Category) when no User column
        assertThat(html).contains("<th colspan=\"3\">Total</th>");
    }

    @Test
    void showsDegradedScanAndRowCapNotices() {
        String html = MileageReportRenderer.render(
                "All users", FROM, TO,
                List.of(mileage("e1", "2026-05-03", "Ada", "Route", "1", "7.25", "7.25")),
                true, true, true, true);

        assertThat(html).contains("Live expense data is unavailable; showing reconciled mileage rows only.");
        assertThat(html).contains("The expense scan stopped at its page budget");
        assertThat(html).contains("Showing the first 1 rows");
    }

    private static ReportRow mileage(String id, String date, String user, String project, String miles, String rate, String amount) {
        return new ReportRow(id, LocalDate.parse(date), "u", user, project, "Mileage", true,
                new BigDecimal(miles), new BigDecimal(rate), new BigDecimal(amount));
    }

    private static ReportRow native_(String id, String date, String user, String project, String category, String amount) {
        return new ReportRow(id, LocalDate.parse(date), "u", user, project, category, false,
                null, null, new BigDecimal(amount));
    }
}
