package com.cake.clockify.client.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Simplified reference payload for {@code EXPENSE_DELETED} and {@code EXPENSE_UPDATED} events.
 *
 * <p>These events carry only context IDs and the expense entity ID—no full expense body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpenseRefWebhookPayload(
        String workspaceId,
        String userId,
        String projectId,
        String expenseId,
        String categoryId
) {}
