package com.cake.clockify.addon.mileage.api.model;

import java.time.Instant;

public record MileageOperationalHealthResponse(
        long pendingJobs,
        long claimedJobs,
        long failedJobs,
        Long oldestPendingAgeSeconds,
        Instant lastCompletedJobAt
) {
}
