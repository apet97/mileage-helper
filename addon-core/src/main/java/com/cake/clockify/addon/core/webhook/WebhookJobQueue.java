package com.cake.clockify.addon.core.webhook;

import java.util.UUID;

/**
 * Persists verified webhook requests so a worker can process them asynchronously.
 *
 * <p>The {@link WebhookController} writes a PENDING job row and immediately returns 2xx;
 * a worker then claims the row with {@code SELECT … FOR UPDATE SKIP LOCKED} and invokes
 * the matching {@link AddonWebhookHandler}. Implementations live in {@code addon-db}.
 */
public interface WebhookJobQueue {

    /**
     * Persists a PENDING job for later worker processing.
     *
     * <p>Implementations must be idempotent on {@code dedupeKey}: a duplicate enqueue
     * for the same {@code (addonKey, workspaceId, dedupeKey)} should not produce
     * a second pending job. Callers must dedupe upstream via {@link WebhookEventService}
     * before reaching this point; this is a belt-and-suspenders safety net.
     */
    void enqueue(EnqueueRequest request);

    /**
     * Single-row enqueue payload.
     *
     * @param addonKey     verified add-on key
     * @param workspaceId  verified workspace id from claims
     * @param eventType    normalized webhook event type (upper case)
     * @param eventId      id of the corresponding {@code addon_webhook_events} row, may be null
     * @param dedupeKey    {@link WebhookDedupeKey} value for this delivery
     * @param payload      raw request body bytes — may be null/empty
     * @param claimsJson   serialized {@link com.cake.clockify.addon.core.auth.NormalizedClaims}
     *                     so the worker can dispatch with the same claim values the controller saw
     */
    record EnqueueRequest(
            String addonKey,
            String workspaceId,
            String eventType,
            UUID eventId,
            String dedupeKey,
            byte[] payload,
            String claimsJson) {
    }
}
