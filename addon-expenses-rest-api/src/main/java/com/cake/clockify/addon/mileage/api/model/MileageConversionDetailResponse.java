package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;

import java.time.Instant;
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
        String calculatedAmount,
        String roundedAmount,
        String roundingMode,
        MileageConversionStatus status,
        MileageSkipReason skipReason,
        String errorCode,
        String errorMessage,
        String noteMarker,
        Instant createdAt,
        Instant updatedAt,
        Instant convertedAt,
        Instant deletedAt
) {
    public static MileageConversionDetailResponse from(MileageConversion conversion) {
        return from(conversion, null);
    }

    public static MileageConversionDetailResponse from(MileageConversion conversion, String userName) {
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
                userName == null || userName.isBlank() ? conversion.getUserId() : userName,
                conversion.getProjectId(),
                conversion.getTaskId(),
                conversion.getExpenseDate() == null ? null : conversion.getExpenseDate().toString(),
                text(conversion.getMiles()),
                text(conversion.getRate()),
                text(conversion.getCalculatedAmount()),
                roundedText(conversion.getRoundedAmount()),
                conversion.getRoundingMode(),
                conversion.getStatus(),
                conversion.getSkipReason(),
                conversion.getErrorCode(),
                conversion.getErrorMessage(),
                conversion.getNoteMarker(),
                conversion.getCreatedAt(),
                conversion.getUpdatedAt(),
                conversion.getConvertedAt(),
                conversion.getDeletedAt());
    }

    private static String text(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String roundedText(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
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
