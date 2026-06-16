package com.cake.clockify.addon.mileage.api.model;

import java.util.List;

public record MileageInsightsResponse(
        String totalConvertedMiles,
        String totalCalculatedAmount,
        String totalRoundedAmount,
        long failedConversions,
        long rowsMissingTripPurpose,
        long rowsWithPolicyExceptions,
        List<CountItem> statusCounts,
        List<CountItem> skipReasonCounts,
        List<TopItem> topProjects,
        List<TopItem> topUsers) {

    public record CountItem(
            String key,
            long count) {
    }

    public record TopItem(
            String id,
            String name,
            String calculatedAmount,
            String miles,
            long count) {
    }
}
