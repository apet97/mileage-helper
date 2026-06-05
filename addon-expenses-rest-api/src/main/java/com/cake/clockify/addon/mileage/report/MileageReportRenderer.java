package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.mileage.audit.MileageConversion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Pure HTML builder for the printable per-user mileage report. No Spring, no inline style/script (CSP-safe). */
public final class MileageReportRenderer {

    private MileageReportRenderer() {
    }

    public static String render(
            String userLabel,
            LocalDate from,
            LocalDate to,
            List<MileageConversion> rows,
            Map<String, String> projectNames,
            boolean truncated) {
        StringBuilder body = new StringBuilder();
        BigDecimal totalMiles = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (MileageConversion row : rows) {
            totalMiles = totalMiles.add(orZero(row.getMiles()));
            totalAmount = totalAmount.add(orZero(row.getCalculatedAmount()));
            body.append("<tr>")
                    .append("<td>").append(escape(text(row.getExpenseDate()))).append("</td>")
                    .append("<td>").append(escape(projectName(row.getProjectId(), projectNames))).append("</td>")
                    .append("<td>").append(escape(decimal(row.getMiles()))).append("</td>")
                    .append("<td>").append(escape(decimal(row.getRate()))).append("</td>")
                    .append("<td>").append(escape(decimal(row.getCalculatedAmount()))).append("</td>")
                    .append("</tr>\n");
        }
        String truncatedNotice = truncated
                ? "<p class=\"report-truncated\">Showing the first " + rows.size()
                        + " rows. Totals reflect only the rows shown.</p>"
                : "";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Mileage Reimbursement Report</title>
                  <link rel="stylesheet" href="/assets/mileage/report.css">
                  <script src="/assets/mileage/report.js" defer></script>
                </head>
                <body>
                  <header class="report-header">
                    <div>
                      <h1>Mileage Reimbursement Report</h1>
                      <dl class="report-meta">
                        <div><dt>User</dt><dd>%s</dd></div>
                        <div><dt>Period</dt><dd>%s to %s</dd></div>
                      </dl>
                    </div>
                    <button type="button" id="btn-print" class="report-print">Print / Save as PDF</button>
                  </header>
                  %s
                  <table class="report-table">
                    <thead><tr><th>Date</th><th>Project</th><th>Miles</th><th>Rate</th><th>Amount</th></tr></thead>
                    <tbody>
                %s</tbody>
                    <tfoot><tr><th colspan="2">Total</th><td>%s</td><td></td><td>%s</td></tr></tfoot>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(userLabel),
                escape(text(from)),
                escape(text(to)),
                truncatedNotice,
                body,
                escape(decimal(totalMiles)),
                escape(decimal(totalAmount)));
    }

    private static String projectName(String projectId, Map<String, String> projectNames) {
        if (projectId == null || projectId.isBlank()) {
            return "";
        }
        String name = projectNames.get(projectId);
        return name == null || name.isBlank() ? "" : name;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
