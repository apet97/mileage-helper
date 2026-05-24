package com.cake.clockify.client.models;

public record ExpenseCategory(
        String id,
        String workspaceId,
        String name,
        boolean hasUnitPrice,
        int priceInCents,
        String unit,
        boolean archived
) {}
