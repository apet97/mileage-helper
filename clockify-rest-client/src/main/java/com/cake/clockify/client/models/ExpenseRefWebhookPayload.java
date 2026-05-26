package com.cake.clockify.client.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Simplified reference payload for {@code EXPENSE_DELETED} and {@code EXPENSE_UPDATED} events.
 *
 * <p>Clockify may send the expense entity ID as either {@code expenseId} or {@code id};
 * callers should use {@link #effectiveExpenseId()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpenseRefWebhookPayload(
        String id,
        String workspaceId,
        String userId,
        String projectId,
        String expenseId,
        String categoryId
) {
    public String effectiveExpenseId() {
        if (id != null && !id.isBlank()) {
            return id;
        }
        return expenseId;
    }
}
