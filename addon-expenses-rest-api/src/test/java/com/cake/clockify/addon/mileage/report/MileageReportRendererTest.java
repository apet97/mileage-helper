package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MileageReportRendererTest {

    @Test
    void rendersHeaderRowsTotalsAndEscapesDynamicText() {
        MileageConversion row = row("2026-05-24", "p1", "12.5", "0.725", "9.0625");

        String html = MileageReportRenderer.render(
                "Ada <Lovelace>",
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-31"),
                List.of(row),
                Map.of("p1", "North <b>Route</b>"),
                false);

        assertThat(html).contains("Mileage Reimbursement Report");
        assertThat(html).contains("Ada &lt;Lovelace&gt;");
        assertThat(html).contains("2026-05-01 to 2026-05-31");
        assertThat(html).contains("North &lt;b&gt;Route&lt;/b&gt;");
        assertThat(html).contains("12.5");
        assertThat(html).contains("0.725");
        assertThat(html).contains("9.0625");
        assertThat(html).contains("<link rel=\"stylesheet\" href=\"/assets/mileage/report.css\">");
        assertThat(html).contains("src=\"/assets/mileage/report.js\" defer");
        assertThat(html).contains("id=\"btn-print\"");
        assertThat(html).doesNotContain("<style");
        assertThat(html).doesNotContain("onclick=");
    }

    @Test
    void sumsMilesAndCalculatedAmountInTotalsRow() {
        String html = MileageReportRenderer.render(
                "Ada",
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-31"),
                List.of(
                        row("2026-05-10", "p1", "10", "0.725", "7.25"),
                        row("2026-05-11", "p1", "2.5", "0.725", "1.8125")),
                Map.of("p1", "Route"),
                false);

        assertThat(html).contains("<th colspan=\"2\">Total</th><td>12.5</td><td></td><td>9.0625</td>");
    }

    @Test
    void showsTruncationNoticeWhenCapped() {
        String html = MileageReportRenderer.render(
                "Ada",
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-31"),
                List.of(row("2026-05-10", "p1", "1", "0.725", "0.725")),
                Map.of("p1", "Route"),
                true);

        assertThat(html).contains("Showing the first 1 rows");
    }

    private static MileageConversion row(String date, String projectId, String miles, String rate, String calculated) {
        MileageConversion conversion = new MileageConversion();
        conversion.setExpenseDate(LocalDate.parse(date));
        conversion.setProjectId(projectId);
        conversion.setMiles(new BigDecimal(miles));
        conversion.setRate(new BigDecimal(rate));
        conversion.setCalculatedAmount(new BigDecimal(calculated));
        return conversion;
    }
}
