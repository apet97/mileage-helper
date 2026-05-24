package com.cake.clockify.addon.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persists a Clockify add-on installation. One row per workspace — the workspace is the
 * primary isolation boundary.
 *
 * <p>The installation token is stored encrypted (AES-GCM-256 via {@code TokenCodec}).
 * The raw plaintext must never appear in logs, exception messages, or HTTP responses.
 */
@Entity
@Table(name = "addon_installations")
public class AddonInstallation {

    @Id
    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "addon_key", nullable = false, length = 128)
    private String addonKey;

    @Column(name = "addon_id", length = 64)
    private String addonId;

    /** AES-GCM-256 cipher, base64-encoded. Never expose raw value. */
    @Column(name = "installation_token_enc", nullable = false, columnDefinition = "TEXT")
    private String installationTokenEnc;

    @Column(name = "token_key_id", nullable = false, length = 64)
    private String tokenKeyId;

    @Column(name = "backend_url", nullable = false, length = 512)
    private String backendUrl;

    @Column(name = "reports_url", length = 512)
    private String reportsUrl;

    @Column(name = "installer_user_id", length = 64)
    private String installerUserId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (installedAt == null) installedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String v) { this.workspaceId = v; }
    public String getAddonKey() { return addonKey; }
    public void setAddonKey(String v) { this.addonKey = v; }
    public String getAddonId() { return addonId; }
    public void setAddonId(String v) { this.addonId = v; }
    public String getInstallationTokenEnc() { return installationTokenEnc; }
    public void setInstallationTokenEnc(String v) { this.installationTokenEnc = v; }
    public String getTokenKeyId() { return tokenKeyId; }
    public void setTokenKeyId(String v) { this.tokenKeyId = v; }
    public String getBackendUrl() { return backendUrl; }
    public void setBackendUrl(String v) { this.backendUrl = v; }
    public String getReportsUrl() { return reportsUrl; }
    public void setReportsUrl(String v) { this.reportsUrl = v; }
    public String getInstallerUserId() { return installerUserId; }
    public void setInstallerUserId(String v) { this.installerUserId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getInstalledAt() { return installedAt; }
    public void setInstalledAt(Instant v) { this.installedAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
