package com.cake.clockify.client.models;

import java.time.Instant;

public record Expense(
        String id,
        String workspaceId,
        String userId,
        Instant date,
        String projectId,
        String taskId,
        String categoryId,
        String notes,
        double quantity,
        boolean billable,
        String fileId,
        double total,
        boolean locked
) {}
