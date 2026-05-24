package com.cake.clockify.addon.db.config;

import com.cake.clockify.addon.core.webhook.WebhookTokenLookup;
import com.cake.clockify.addon.db.entity.AddonWebhookToken;
import com.cake.clockify.addon.db.repository.AddonWebhookTokenRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed implementation of {@link WebhookTokenLookup}. Wired automatically when addon-db is
 * on the classpath. The auth filter uses this to verify inbound webhook tokens.
 */
@Component
public class JpaWebhookTokenLookup implements WebhookTokenLookup {

    private final AddonWebhookTokenRepository repository;

    public JpaWebhookTokenLookup(AddonWebhookTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StoredWebhookToken> findByWorkspaceAddonPath(
            String workspaceId, String addonId, String normalizedPath) {
        return repository.findByWorkspaceIdAndAddonIdAndPath(workspaceId, addonId, normalizedPath)
                .map(this::toRecord);
    }

    private StoredWebhookToken toRecord(AddonWebhookToken entity) {
        return new StoredWebhookToken(
                entity.getWorkspaceId(),
                entity.getAddonId(),
                entity.getPath(),
                entity.getEventType(),
                entity.getWebhookTokenEnc(),
                entity.getTokenKeyId());
    }
}
