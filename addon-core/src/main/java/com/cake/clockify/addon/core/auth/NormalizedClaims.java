package com.cake.clockify.addon.core.auth;

import java.time.Instant;

/**
 * Canonical Clockify JWT claims after alias normalization.
 *
 * <p>Clockify issues three token types (installation, user, webhook-signature), each with
 * slightly different claim names across API regions and SDK versions. {@link ClaimsNormalizer}
 * resolves all aliases and emits this single canonical record so every downstream consumer
 * reads one well-typed object rather than raw {@code Map<String,Object>}.
 */
public record NormalizedClaims(
        String workspaceId,
        String addonId,
        /** Primary API base URL extracted from the JWT — never hardcode this. */
        String backendUrl,
        String reportsUrl,
        String locationsUrl,
        String screenshotsUrl,
        String userId,
        String workspaceRole,
        String language,
        String theme,
        String userTimeZone,
        Instant iat) {
}
