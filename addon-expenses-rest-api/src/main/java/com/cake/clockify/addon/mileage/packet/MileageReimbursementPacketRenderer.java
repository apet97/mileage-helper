package com.cake.clockify.addon.mileage.packet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.stream.Collectors;

public final class MileageReimbursementPacketRenderer {
    private MileageReimbursementPacketRenderer() {
    }

    public static String render(MileageReimbursementPacket packet) {
        String rows = packet.rows().stream()
                .map(MileageReimbursementPacketRenderer::row)
                .collect(Collectors.joining());
        String policies = packet.ratePolicyNames().isEmpty()
                ? ""
                : packet.ratePolicyNames().stream()
                        .map(MileageReimbursementPacketRenderer::escape)
                        .collect(Collectors.joining(", "));
        String notice = packet.truncated()
                ? "<p class=\"packet-notice\">Packet row limit reached; narrow the date range for a complete packet.</p>"
                : "";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Mileage reimbursement packet</title>
                  <link rel="icon" type="image/png" href="/assets/mileage/icon.png">
                  <link rel="stylesheet" href="/assets/mileage/packet.css">
                  <script src="/assets/mileage/packet.js" defer></script>
                </head>
                <body>
                  <header class="packet-header">
                    <div>
                      <div class="packet-brand">
                        <img class="packet-logo" src="/assets/mileage/icon.png" alt="" width="28" height="28">
                        <h1>Mileage reimbursement packet</h1>
                      </div>
                      <dl class="packet-meta">
                        <div><dt>User</dt><dd>%s</dd></div>
                        <div><dt>Period</dt><dd>%s to %s</dd></div>
                        <div><dt>Generated</dt><dd>%s</dd></div>
                      </dl>
                    </div>
                    <button type="button" id="btn-print" class="packet-print">Print / Save as PDF</button>
                  </header>
                  %s
                  <section class="packet-summary" aria-label="Packet summary">
                    <dl>
                      <div><dt>Total miles</dt><dd>%s</dd></div>
                      <div><dt>Calculated total</dt><dd>%s</dd></div>
                      <div><dt>Expense total</dt><dd>%s</dd></div>
                      <div><dt>Rows</dt><dd>%d</dd></div>
                      <div><dt>Exceptions</dt><dd>%d</dd></div>
                      <div><dt>Rate policies</dt><dd>%s</dd></div>
                    </dl>
                  </section>
                  <table class="packet-table">
                    <thead><tr><th>Date</th><th>User</th><th>Project</th><th>Purpose</th><th>From</th><th>To</th><th>Odometer</th><th>Expense</th><th class="num">Miles</th><th class="num">Rate</th><th>Rate policy</th><th class="num">Calculated</th><th class="num">Expense</th><th>Status</th><th>Exception</th><th>Policy exception</th></tr></thead>
                    <tbody>
                %s</tbody>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(packet.userLabel()),
                escape(text(packet.from())),
                escape(text(packet.to())),
                escape(text(packet.generatedAt())),
                notice,
                escape(decimal(packet.totalMiles())),
                escape(money(packet.totalCalculatedAmount())),
                escape(money(packet.totalRoundedAmount())),
                packet.rowCount(),
                packet.exceptionCount(),
                policies,
                rows);
    }

    private static String row(MileageReimbursementPacketRow row) {
        return """
                  <tr>
                    <td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>
                    <td class="num">%s</td><td class="num">%s</td><td>%s</td>
                    <td class="num">%s</td><td class="num">%s</td><td>%s</td><td>%s</td><td>%s</td>
                  </tr>
                """.formatted(
                escape(row.expenseDate()),
                escape(row.userName()),
                escape(row.projectName()),
                escape(row.tripPurpose()),
                escape(row.tripOrigin()),
                escape(row.tripDestination()),
                escape(odometer(row)),
                escape(row.expenseId()),
                escape(row.miles()),
                escape(row.rate()),
                escape(ratePolicy(row)),
                escape(row.calculatedAmount()),
                escape(row.roundedAmount()),
                escape(row.status()),
                escape(row.exceptionReason()),
                escape(row.policyExceptionReason()));
    }

    private static String odometer(MileageReimbursementPacketRow row) {
        if (row.odometerStart() == null || row.odometerStart().isBlank()) {
            return row.odometerEnd() == null ? "" : row.odometerEnd();
        }
        if (row.odometerEnd() == null || row.odometerEnd().isBlank()) {
            return row.odometerStart();
        }
        return row.odometerStart() + " to " + row.odometerEnd();
    }

    private static String ratePolicy(MileageReimbursementPacketRow row) {
        if (row.ratePolicyName() != null && !row.ratePolicyName().isBlank()) {
            return row.ratePolicyName();
        }
        if (row.rateSource() != null && !row.rateSource().isBlank()) {
            return row.rateSource();
        }
        return "";
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String text(Object value) {
        if (value instanceof Instant instant) {
            return instant.toString();
        }
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
