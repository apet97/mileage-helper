package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.db.entity.AddonInstallation;
import com.cake.clockify.addon.db.repository.AddonInstallationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Base64;
import java.util.Optional;

/**
 * Service to manage add-on installation status and credentials.
 */
@Service
public class AddonInstallationService {

    private final AddonInstallationRepository repository;
    private final TokenCodec tokenCodec;

    public AddonInstallationService(AddonInstallationRepository repository, TokenCodec tokenCodec) {
        this.repository = repository;
        this.tokenCodec = tokenCodec;
    }

    /**
     * Idempotently creates or updates an installation record. Encrypts the raw installation token
     * before storing.
     */
    @Transactional
    public AddonInstallation markInstalled(
            String workspaceId,
            String addonKey,
            String addonId,
            String rawToken,
            String backendUrl,
            String reportsUrl,
            String installerUserId) {
        
        TokenCodec.Encrypted enc = tokenCodec.encrypt(rawToken);
        String tokenEnc = Base64.getEncoder().encodeToString(enc.cipher());
        String tokenKeyId = enc.keyId();

        AddonInstallation installation = repository.findById(workspaceId)
                .orElseGet(AddonInstallation::new);
        
        installation.setWorkspaceId(workspaceId);
        installation.setAddonKey(addonKey);
        installation.setAddonId(addonId);
        installation.setInstallationTokenEnc(tokenEnc);
        installation.setTokenKeyId(tokenKeyId);
        installation.setBackendUrl(backendUrl);
        installation.setReportsUrl(reportsUrl);
        installation.setInstallerUserId(installerUserId);
        installation.setStatus("ACTIVE");

        return repository.save(installation);
    }

    /**
     * Idempotently removes an installation and its encrypted installation token.
     */
    @Transactional
    public void markUninstalled(String workspaceId) {
        repository.findById(workspaceId).ifPresent(repository::delete);
    }

    /**
     * Finds installation by workspace ID, returning empty if not found.
     */
    public Optional<AddonInstallation> findInstallation(String workspaceId) {
        return repository.findById(workspaceId)
                .filter(inst -> "ACTIVE".equals(inst.getStatus()));
    }

    /**
     * Finds installation by workspace ID, throwing an exception if not found.
     */
    public AddonInstallation getRequiredInstallation(String workspaceId) {
        return findInstallation(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("No active installation found for workspace: " + workspaceId));
    }

    /**
     * Decrypts and returns the raw installation token for the given installation.
     */
    public String decryptToken(AddonInstallation installation) {
        if (installation == null || installation.getInstallationTokenEnc() == null) {
            return null;
        }
        byte[] cipherBytes = Base64.getDecoder().decode(installation.getInstallationTokenEnc());
        return tokenCodec.decrypt(installation.getTokenKeyId(), cipherBytes);
    }

    /**
     * Checks if workspace has an active installation record.
     */
    public boolean isInstalled(String workspaceId) {
        return repository.findById(workspaceId)
                .map(installation -> "ACTIVE".equals(installation.getStatus()))
                .orElse(false);
    }
}
