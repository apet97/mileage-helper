package com.cake.clockify.addon.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Per-webhook auth token registered at INSTALLED time. Used by {@link
 * com.cake.clockify.addon.core.auth.filter.ClockifyWebhookAuthFilter} to verify that inbound
 * webhooks carry the correct auth token for this workspace's route.
 */
@Entity
@Table(
        name = "addon_webhook_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_addon_webhook_tokens_workspace_addon_path",
                columnNames = {"workspace_id", "addon_key", "path"}))
public class AddonWebhookToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "addon_key", nullable = false, length = 128)
    private String addonKey;

    @Column(name = "addon_id", length = 64)
    private String addonId;

    /** Normalized path, e.g. "/new-time-entry". */
    @Column(name = "path", nullable = false, length = 256)
    private String path;

    @Column(name = "event_type", length = 64)
    private String eventType;

    /** AES-GCM-256 cipher, base64-encoded. */
    @Column(name = "webhook_token_enc", nullable = false, columnDefinition = "TEXT")
    private String webhookTokenEnc;

    @Column(name = "token_key_id", nullable = false, length = 64)
    private String tokenKeyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String v) { this.workspaceId = v; }
    public String getAddonKey() { return addonKey; }
    public void setAddonKey(String v) { this.addonKey = v; }
    public String getAddonId() { return addonId; }
    public void setAddonId(String v) { this.addonId = v; }
    public String getPath() { return path; }
    public void setPath(String v) { this.path = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getWebhookTokenEnc() { return webhookTokenEnc; }
    public void setWebhookTokenEnc(String v) { this.webhookTokenEnc = v; }
    public String getTokenKeyId() { return tokenKeyId; }
    public void setTokenKeyId(String v) { this.tokenKeyId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
