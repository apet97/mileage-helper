package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.db.entity.AddonWebhookEvent;
import com.cake.clockify.addon.db.repository.AddonWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.cake.clockify.addon.core.webhook.WebhookEventService;

/**
 * Service to handle webhook event tracking, deduplication, and status transitions.
 */
@Service
public class AddonWebhookEventService implements WebhookEventService {

    private final AddonWebhookEventRepository repository;

    public AddonWebhookEventService(AddonWebhookEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a newly received event in the database with RECEIVED status.
     */
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public AddonWebhookEvent recordReceived(
            String addonKey,
            String workspaceId,
            String eventType,
            String dedupeKey,
            String payloadHash) {
        
        AddonWebhookEvent event = repository.findByAddonKeyAndWorkspaceIdAndDedupeKey(addonKey, workspaceId, dedupeKey)
                .orElseGet(AddonWebhookEvent::new);
        
        event.setAddonKey(addonKey);
        event.setWorkspaceId(workspaceId);
        event.setEventType(eventType);
        event.setDedupeKey(dedupeKey);
        event.setPayloadHash(payloadHash);
        event.setStatus("RECEIVED");
        
        try {
            return repository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            return repository.findByAddonKeyAndWorkspaceIdAndDedupeKey(addonKey, workspaceId, dedupeKey)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Checks if the event has already been recorded (is a duplicate).
     * Events in FAILED status are not considered duplicates and can be retried.
     */
    @Override
    public boolean isDuplicate(String addonKey, String workspaceId, String dedupeKey) {
        return repository.findByAddonKeyAndWorkspaceIdAndDedupeKey(addonKey, workspaceId, dedupeKey)
                .map(event -> !"FAILED".equals(event.getStatus()))
                .orElse(false);
    }

    /**
     * Records the event and returns its ID.
     */
    @Override
    @Transactional
    public UUID recordEvent(String addonKey, String workspaceId, String eventType, String dedupeKey, String payloadHash) {
        return recordReceived(addonKey, workspaceId, eventType, dedupeKey, payloadHash).getId();
    }

    /**
     * Atomically transitions the event from RECEIVED to PROCESSING.
     * Returns true if the transition succeeded, false if it was already processing/processed.
     */
    @Override
    @Transactional
    public boolean tryStartProcessing(UUID eventId) {
        int updated = repository.tryStartProcessing(eventId, Instant.now());
        return updated == 1;
    }

    /**
     * Marks the event processing as successfully PROCESSED.
     */
    @Override
    @Transactional
    public void markProcessed(UUID eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus("PROCESSED");
            event.setProcessedAt(Instant.now());
            repository.save(event);
        });
    }

    /**
     * Marks the event processing as FAILED, increments the retry count, and stores a sanitized error message.
     */
    @Override
    @Transactional
    public void markFailed(UUID eventId, String redactedErrorMessage) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus("FAILED");
            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessageRedacted(redactSecrets(redactedErrorMessage));
            repository.save(event);
        });
    }

    /**
     * Helper to sanitize messages to ensure no secrets like API keys or tokens are stored in plain text.
     */
    private String redactSecrets(String message) {
        if (message == null) {
            return null;
        }
        return message
                .replaceAll("(?i)(\"(?:x-api-key|x-addon-token|authorization|authToken|addonToken|api[-_]?key|token|password|secret)\"\\s*:\\s*\")[^\"]*(\")", "$1REDACTED$2")
                .replaceAll("(?i)(authorization\\s*:\\s*bearer\\s+)[^,}\\s\\n]+", "$1REDACTED")
                .replaceAll("(?i)(bearer\\s+)[^,}\\s\\n]+", "$1REDACTED")
                .replaceAll("(?i)((?:x-api-key|x-addon-token|authorization|authToken|addonToken|api[-_]?key|token|password|secret)\\s*[:=]\\s*)[^,}\\s\\n]+", "$1REDACTED");
    }
}
