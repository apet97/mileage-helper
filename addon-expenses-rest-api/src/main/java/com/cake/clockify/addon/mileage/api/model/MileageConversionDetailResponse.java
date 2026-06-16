package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MileageConversionDetailResponse(
        UUID id,
        String workspaceId,
        String expenseId,
        MileageConversionSource source,
        String sourceEventType,
        String sourceLabel,
        String sourceCategoryId,
        String targetCategoryId,
        String userId,
        String userName,
        String projectId,
        String taskId,
        String expenseDate,
        String miles,
        String rate,
        String rateSource,
        UUID ratePolicyId,
        String ratePolicyName,
        String calculatedAmount,
        String roundedAmount,
        String roundingMode,
        MileageConversionStatus status,
        MileageSkipReason skipReason,
        String errorCode,
        String errorMessage,
        List<String> workflowWarnings,
        List<String> workflowBlockers,
        String noteMarker,
        String tripOrigin,
        String tripDestination,
        String tripPurpose,
        String odometerStart,
        String odometerEnd,
        String policyExceptionReason,
        Instant createdAt,
        Instant updatedAt,
        Instant convertedAt,
        Instant deletedAt
) {
    public static MileageConversionDetailResponse from(MileageConversion conversion) {
        return from(conversion, null);
    }

    public static MileageConversionDetailResponse from(MileageConversion conversion, String userName) {
        return from(conversion, userName, true);
    }

    /**
     * @param userIdFallback when no resolved {@code userName} is available, {@code true} echoes the raw
     *        {@code userId} (admin Team/Conversions lists, so unresolved users stay identifiable),
     *        {@code false} leaves {@code userName} null (the "/mine" own-rows view, which has no User column).
     */
    public static MileageConversionDetailResponse from(MileageConversion conversion, String userName, boolean userIdFallback) {
        return new MileageConversionDetailResponse(
                conversion.getId(),
                conversion.getWorkspaceId(),
                conversion.getExpenseId(),
                conversion.getSource(),
                conversion.getSourceEventType(),
                sourceLabel(conversion.getSource()),
                conversion.getSourceCategoryId(),
                conversion.getTargetCategoryId(),
                conversion.getUserId(),
                resolveUserName(conversion.getUserId(), userName, userIdFallback),
                conversion.getProjectId(),
                conversion.getTaskId(),
                conversion.getExpenseDate() == null ? null : conversion.getExpenseDate().toString(),
                text(conversion.getMiles()),
                text(conversion.getRate()),
                conversion.getRateSource(),
                conversion.getRatePolicyId(),
                conversion.getRatePolicyName(),
                text(conversion.getCalculatedAmount()),
                roundedText(conversion.getRoundedAmount()),
                conversion.getRoundingMode(),
                conversion.getStatus(),
                conversion.getSkipReason(),
                conversion.getErrorCode(),
                conversion.getErrorMessage(),
                workflowWarnings(conversion),
                workflowBlockers(conversion),
                conversion.getNoteMarker(),
                conversion.getTripOrigin(),
                conversion.getTripDestination(),
                conversion.getTripPurpose(),
                text(conversion.getOdometerStart()),
                text(conversion.getOdometerEnd()),
                conversion.getPolicyExceptionReason(),
                conversion.getCreatedAt(),
                conversion.getUpdatedAt(),
                conversion.getConvertedAt(),
                conversion.getDeletedAt());
    }

    private static String resolveUserName(String userId, String userName, boolean userIdFallback) {
        if (userName != null && !userName.isBlank()) {
            return userName;
        }
        return userIdFallback ? userId : null;
    }

    private static String text(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String roundedText(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static List<String> workflowWarnings(MileageConversion conversion) {
        return List.of();
    }

    private static List<String> workflowBlockers(MileageConversion conversion) {
        if (conversion.getSkipReason() == MileageSkipReason.FINALIZED_OR_LOCKED) {
            return List.of("Expense is locked or finalized");
        }
        return List.of();
    }

    public static String sourceLabel(MileageConversionSource source) {
        if (source == null) {
            return "";
        }
        return switch (source) {
            case ADDON_FORM -> "Created through add-on";
            case WEBHOOK_CREATED, WEBHOOK_UPDATED, WEBHOOK_RESTORED -> "Created through Expenses";
        };
    }
}
