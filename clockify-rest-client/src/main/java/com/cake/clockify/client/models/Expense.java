package com.cake.clockify.client.models;

import java.math.BigDecimal;
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
        BigDecimal quantity,
        boolean billable,
        String fileId,
        BigDecimal total,
        boolean locked
) {}
