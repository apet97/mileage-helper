package com.cake.clockify.client.models;

import java.util.List;

public record TimeEntry(
        String id,
        String description,
        List<String> tagIds,
        String userId,
        boolean billable,
        String taskId,
        String projectId,
        String workspaceId,
        TimeInterval timeInterval,
        boolean isLocked
) {}
