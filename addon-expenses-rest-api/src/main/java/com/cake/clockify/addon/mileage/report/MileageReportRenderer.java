package com.cake.clockify.addon.mileage.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Pure HTML builder for the printable expense report. No Spring, no inline style/script (CSP-safe:
 * external /assets/mileage/report.css + report.js only). Columns: Date | [User] | Project | Category |
 * Miles | Rate | Amount. Mileage rows fill Miles/Rate; non-mileage rows leave them blank. The User
 * column is only emitted in all-users mode ({@code includeUser}).
 */
public final class MileageReportRenderer {

    private MileageReportRenderer() {
    }

    public static String render(
            String userLabel,
            LocalDate from,
            LocalDate to,
            List<ReportRow> rows,
            boolean includeUser,
            boolean scanTruncated,
            boolean rowCapHit,
            boolean degraded) {
        int leadColumns = includeUser ? 4 : 3;
        BigDecimal totalAmount = BigDecimal.ZERO;

        StringBuilder head = new StringBuilder("<tr><th>Date</th>");
        if (includeUser) {
            head.append("<th>User</th>");
        }
        head.append("<th>Project</th><th>Category</th>")
                .append("<th class=\"num\">Miles</th><th class=\"num\">Rate</th><th class=\"num\">Amount</th></tr>");

        StringBuilder body = new StringBuilder();
        for (ReportRow row : rows) {
            // The printable report is a reimbursement document: Amount/Total are money, rendered at a
            // consistent 2 dp (the add-on's full-precision calculated amount lives in the Mine/Team/
            // Conversions audit views). Miles/Rate keep their natural precision. Summing the rounded
            // per-row amounts keeps the Total footing the visible rows exactly.
            BigDecimal rowAmount = orZero(row.amount()).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(rowAmount);
            body.append("<tr>")
                    .append("<td>").append(escape(text(row.date()))).append("</td>");
            if (includeUser) {
                body.append("<td>").append(escape(row.userName())).append("</td>");
            }
            body.append("<td>").append(escape(row.projectName())).append("</td>")
                    .append("<td>").append(escape(row.categoryName())).append("</td>")
                    .append("<td class=\"num\">").append(row.mileage() ? escape(decimal(row.miles())) : "").append("</td>")
                    .append("<td class=\"num\">").append(row.mileage() ? escape(decimal(row.rate())) : "").append("</td>")
                    .append("<td class=\"num\">").append(escape(money(rowAmount))).append("</td>")
                    .append("</tr>\n");
        }

        StringBuilder notices = new StringBuilder();
        if (degraded) {
            notices.append("<p class=\"report-truncated\">Live expense data is unavailable; "
                    + "showing reconciled mileage rows only.</p>");
        }
        if (scanTruncated) {
            notices.append("<p class=\"report-truncated\">The expense scan stopped at its page budget; "
                    + "some in-range expenses may be missing. Narrow the date range for a complete report.</p>");
        }
        if (rowCapHit) {
            notices.append("<p class=\"report-truncated\">Showing the first ").append(rows.size())
                    .append(" rows. Totals reflect only the rows shown.</p>");
        }

        String foot = "<tr><th colspan=\"" + leadColumns + "\">Total</th>"
                + "<td class=\"num\"></td><td class=\"num\"></td>"
                + "<td class=\"num\">" + escape(money(totalAmount)) + "</td></tr>";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Expense Report</title>
                  <link rel="icon" type="image/png" href="/assets/mileage/icon.png">
                  <link rel="stylesheet" href="/assets/mileage/report.css">
                  <script src="/assets/mileage/report.js" defer></script>
                </head>
                <body>
                  <header class="report-header">
                    <div>
                      <div class="report-brand">
                        <img class="report-logo" src="/assets/mileage/icon.png" alt="" width="28" height="28">
                        <h1>Expense Report</h1>
                      </div>
                      <dl class="report-meta">
                        <div><dt>User</dt><dd>%s</dd></div>
                        <div><dt>Period</dt><dd>%s to %s</dd></div>
                      </dl>
                    </div>
                    <button type="button" id="btn-print" class="report-print">Print / Save as PDF</button>
                  </header>
                  %s
                  <table class="report-table">
                    <thead>%s</thead>
                    <tbody>
                %s</tbody>
                    <tfoot>%s</tfoot>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(userLabel),
                escape(text(from)),
                escape(text(to)),
                notices,
                head,
                body,
                foot);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /** Money columns render at a fixed 2 dp so the report reads as a reimbursement total. */
    private static String money(BigDecimal value) {
        return orZero(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
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
