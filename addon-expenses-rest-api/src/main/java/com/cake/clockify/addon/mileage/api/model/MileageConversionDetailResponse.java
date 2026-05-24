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
        String sourceCategoryId,
        String targetCategoryId,
        String userId,
        String projectId,
        String taskId,
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
        return new MileageConversionDetailResponse(
                conversion.getId(),
                conversion.getWorkspaceId(),
                conversion.getExpenseId(),
                conversion.getSource(),
                conversion.getSourceEventType(),
                conversion.getSourceCategoryId(),
                conversion.getTargetCategoryId(),
                conversion.getUserId(),
                conversion.getProjectId(),
                conversion.getTaskId(),
                text(conversion.getMiles()),
                text(conversion.getRate()),
                text(conversion.getCalculatedAmount()),
                text(conversion.getRoundedAmount()),
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
        return value == null ? null : value.toPlainString();
    }
}
