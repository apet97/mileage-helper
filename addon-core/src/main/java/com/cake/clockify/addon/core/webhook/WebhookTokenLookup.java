package com.cake.clockify.addon.core.webhook;

import java.util.Optional;

/**
 * Port for looking up stored webhook auth tokens. Implementations live in addon-db (JPA) or
 * can be supplied by the add-on itself (e.g. in-memory for tests).
 */
public interface WebhookTokenLookup {

    Optional<StoredWebhookToken> findByWorkspaceAddonPath(String workspaceId, String addonId, String normalizedPath);

    record StoredWebhookToken(
            String workspaceId,
            String addonId,
            String path,
            String eventType,
            /** Encrypted ciphertext (base64 or byte array depending on impl). */
            String encryptedToken,
            String keyId) {
    }
}
