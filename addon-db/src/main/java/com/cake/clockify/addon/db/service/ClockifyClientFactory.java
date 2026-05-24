package com.cake.clockify.addon.db.service;

import com.cake.clockify.client.ClockifyClient;
import com.cake.clockify.client.ClockifyClientBuilder;
import com.cake.clockify.addon.db.entity.AddonInstallation;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Factory to build pre-configured {@link ClockifyClient} instances for specific workspaces.
 * Automatically handles DB lookup of installation parameters, token decryption, and regional URL settings.
 */
@Service
public class ClockifyClientFactory {

    private final AddonInstallationService installationService;

    public ClockifyClientFactory(AddonInstallationService installationService) {
        this.installationService = installationService;
    }

    /**
     * Builds and returns a fully-configured ClockifyClient for the given workspace,
     * using the workspace's decrypted installation token and regional API URLs.
     *
     * @param workspaceId the workspace ID to target
     * @return a fully-configured ClockifyClient
     * @throws IllegalArgumentException if no active installation exists for the workspace
     */
    public ClockifyClient getClient(String workspaceId) {
        AddonInstallation inst = installationService.getRequiredInstallation(workspaceId);
        return clientForInstallation(workspaceId, inst);
    }

    /**
     * Builds and returns a fully-configured ClockifyClient for the given workspace,
     * or empty if no installation exists.
     *
     * @param workspaceId the workspace ID to target
     * @return an Optional containing the ClockifyClient if installed, or empty
     */
    public Optional<ClockifyClient> getClientOptional(String workspaceId) {
        return installationService.findInstallation(workspaceId)
                .map(inst -> clientForInstallation(workspaceId, inst));
    }

    private ClockifyClient clientForInstallation(String workspaceId, AddonInstallation inst) {
        String token = installationService.decryptToken(inst);

        ClockifyClientBuilder builder = new ClockifyClientBuilder()
                .backendBaseUrl(inst.getBackendUrl())
                .addonToken(token)
                .workspaceId(workspaceId);

        if (inst.getReportsUrl() != null && !inst.getReportsUrl().isBlank()) {
            builder.reportsBaseUrl(inst.getReportsUrl());
        }

        return builder.build();
    }
}
