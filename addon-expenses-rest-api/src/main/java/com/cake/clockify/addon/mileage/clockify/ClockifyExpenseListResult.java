package com.cake.clockify.addon.mileage.clockify;

import java.util.List;

/**
 * Result of listing workspace expenses for the report. {@code truncated} is true when the scan hit
 * the page budget before reaching a short page (the in-range result may be incomplete) so the caller
 * can surface a truncation notice instead of silently dropping rows.
 */
public record ClockifyExpenseListResult(List<ClockifyExpenseListItem> items, boolean truncated) {
}
