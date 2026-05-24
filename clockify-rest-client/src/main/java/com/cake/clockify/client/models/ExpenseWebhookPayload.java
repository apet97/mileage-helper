package com.cake.clockify.client.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Webhook payload for {@code EXPENSE_CREATED} and {@code EXPENSE_RESTORED} events.
 *
 * <p>Both events share the same shape. {@code EXPENSE_RESTORED} includes the
 * {@code locked} field; {@code EXPENSE_CREATED} omits it, so it deserializes as {@code null}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpenseWebhookPayload(
        String id,
        String workspaceId,
        String userId,
        String date,
        String projectId,
        String taskId,
        String categoryId,
        String notes,
        double quantity,
        boolean billable,
        String fileId,
        double total,
        Boolean locked
) {}
