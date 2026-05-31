package com.cake.clockify.addon.mileage.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "mileage_conversion")
public class MileageConversion implements Persistable<UUID> {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "expense_id", nullable = false, length = 64)
    private String expenseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private MileageConversionSource source;

    @Column(name = "source_event_type", length = 64)
    private String sourceEventType;

    @Column(name = "source_category_id", length = 64)
    private String sourceCategoryId;

    @Column(name = "target_category_id", length = 64)
    private String targetCategoryId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "expense_date")
    private LocalDate expenseDate;

    @Column(name = "miles", precision = 18, scale = 6)
    private BigDecimal miles;

    @Column(name = "rate", precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(name = "calculated_amount", precision = 18, scale = 6)
    private BigDecimal calculatedAmount;

    @Column(name = "rounded_amount", precision = 18, scale = 2)
    private BigDecimal roundedAmount;

    @Column(name = "rounding_mode", length = 32)
    private String roundingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MileageConversionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 128)
    private MileageSkipReason skipReason;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "note_marker", length = 128)
    private String noteMarker;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override
    @Transient
    public boolean isNew() { return createdAt == null; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getExpenseId() { return expenseId; }
    public void setExpenseId(String expenseId) { this.expenseId = expenseId; }
    public MileageConversionSource getSource() { return source; }
    public void setSource(MileageConversionSource source) { this.source = source; }
    public String getSourceEventType() { return sourceEventType; }
    public void setSourceEventType(String sourceEventType) { this.sourceEventType = sourceEventType; }
    public String getSourceCategoryId() { return sourceCategoryId; }
    public void setSourceCategoryId(String sourceCategoryId) { this.sourceCategoryId = sourceCategoryId; }
    public String getTargetCategoryId() { return targetCategoryId; }
    public void setTargetCategoryId(String targetCategoryId) { this.targetCategoryId = targetCategoryId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public BigDecimal getMiles() { return miles; }
    public void setMiles(BigDecimal miles) { this.miles = miles; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public BigDecimal getCalculatedAmount() { return calculatedAmount; }
    public void setCalculatedAmount(BigDecimal calculatedAmount) { this.calculatedAmount = calculatedAmount; }
    public BigDecimal getRoundedAmount() { return roundedAmount; }
    public void setRoundedAmount(BigDecimal roundedAmount) { this.roundedAmount = roundedAmount; }
    public String getRoundingMode() { return roundingMode; }
    public void setRoundingMode(String roundingMode) { this.roundingMode = roundingMode; }
    public MileageConversionStatus getStatus() { return status; }
    public void setStatus(MileageConversionStatus status) { this.status = status; }
    public MileageSkipReason getSkipReason() { return skipReason; }
    public void setSkipReason(MileageSkipReason skipReason) { this.skipReason = skipReason; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getNoteMarker() { return noteMarker; }
    public void setNoteMarker(String noteMarker) { this.noteMarker = noteMarker; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getConvertedAt() { return convertedAt; }
    public void setConvertedAt(Instant convertedAt) { this.convertedAt = convertedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
