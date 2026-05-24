package com.cake.clockify.addon.mileage.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "mileage_workspace_settings")
public class MileageWorkspaceSettings implements Persistable<String> {
    @Id
    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "rate", precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(name = "unit", nullable = false, length = 16)
    private String unit = "mi";

    @Column(name = "input_category_id", length = 64)
    private String inputCategoryId;

    @Column(name = "output_category_id", length = 64)
    private String outputCategoryId;

    @Column(name = "rounding_mode", nullable = false, length = 32)
    private String roundingMode = "HALF_UP";

    @Column(name = "convert_on_create", nullable = false)
    private boolean convertOnCreate = true;

    @Column(name = "convert_on_update", nullable = false)
    private boolean convertOnUpdate = true;

    @Column(name = "preserve_original_notes", nullable = false)
    private boolean preserveOriginalNotes = true;

    @Column(name = "dry_run_mode", nullable = false)
    private boolean dryRunMode;

    @Column(name = "allow_user_rate_override", nullable = false)
    private boolean allowUserRateOverride;

    @Column(name = "note_template", columnDefinition = "TEXT")
    private String noteTemplate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_user_id", length = 64)
    private String updatedByUserId;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    @Override
    public String getId() { return workspaceId; }
    @Override
    @Transient
    public boolean isNew() { return createdAt == null; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getInputCategoryId() { return inputCategoryId; }
    public void setInputCategoryId(String inputCategoryId) { this.inputCategoryId = inputCategoryId; }
    public String getOutputCategoryId() { return outputCategoryId; }
    public void setOutputCategoryId(String outputCategoryId) { this.outputCategoryId = outputCategoryId; }
    public String getRoundingMode() { return roundingMode; }
    public void setRoundingMode(String roundingMode) { this.roundingMode = roundingMode; }
    public boolean isConvertOnCreate() { return convertOnCreate; }
    public void setConvertOnCreate(boolean convertOnCreate) { this.convertOnCreate = convertOnCreate; }
    public boolean isConvertOnUpdate() { return convertOnUpdate; }
    public void setConvertOnUpdate(boolean convertOnUpdate) { this.convertOnUpdate = convertOnUpdate; }
    public boolean isPreserveOriginalNotes() { return preserveOriginalNotes; }
    public void setPreserveOriginalNotes(boolean preserveOriginalNotes) { this.preserveOriginalNotes = preserveOriginalNotes; }
    public boolean isDryRunMode() { return dryRunMode; }
    public void setDryRunMode(boolean dryRunMode) { this.dryRunMode = dryRunMode; }
    public boolean isAllowUserRateOverride() { return allowUserRateOverride; }
    public void setAllowUserRateOverride(boolean allowUserRateOverride) { this.allowUserRateOverride = allowUserRateOverride; }
    public String getNoteTemplate() { return noteTemplate; }
    public void setNoteTemplate(String noteTemplate) { this.noteTemplate = noteTemplate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(String updatedByUserId) { this.updatedByUserId = updatedByUserId; }
}
