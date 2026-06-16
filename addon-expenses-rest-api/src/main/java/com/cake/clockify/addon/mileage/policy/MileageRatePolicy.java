package com.cake.clockify.addon.mileage.policy;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "mileage_rate_policy")
public class MileageRatePolicy implements Persistable<UUID> {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(name = "unit", nullable = false, length = 16)
    private String unit = "mile";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_user_id", length = 64)
    private String updatedByUserId;

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

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override
    @Transient
    public boolean isNew() { return createdAt == null; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(String updatedByUserId) { this.updatedByUserId = updatedByUserId; }
}
