package com.cake.clockify.addon.core.webhook;

import java.util.UUID;

/**
 * Service interface for tracking and deduplicating webhook events.
 */
public interface WebhookEventService {

    /**
     * Checks if the event is a duplicate.
     */
    boolean isDuplicate(String addonKey, String workspaceId, String dedupeKey);

    /**
     * Records the received event.
     */
    UUID recordEvent(String addonKey, String workspaceId, String eventType, String dedupeKey, String payloadHash);

    /**
     * Transitions event to processing. Returns true if successful.
     */
    boolean tryStartProcessing(UUID eventId);

    /**
     * Marks event as processed.
     */
    void markProcessed(UUID eventId);

    /**
     * Marks event as failed.
     */
    void markFailed(UUID eventId, String errorMessage);
}
