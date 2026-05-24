package com.cake.clockify.addon.core.webhook;

import com.cake.clockify.addon.core.auth.NormalizedClaims;

/**
 * Interface for typed webhook handlers. Implement as a Spring component and annotate with
 * {@link WebhookEvent} to register for a specific Clockify event type.
 *
 * <pre>{@code
 * @Component
 * @WebhookEvent("NEW_TIME_ENTRY")
 * class TimeEntryCreatedHandler implements AddonWebhookHandler {
 *     @Override
 *     public void handle(NormalizedClaims claims, String eventType, byte[] rawBody) {
 *         // process the webhook
 *     }
 * }
 * }</pre>
 */
public interface AddonWebhookHandler {

    /**
     * Called after signature verification and idempotency check. The raw body is passed so handlers
     * can deserialize with their own ObjectMapper / Gson configuration.
     *
     * @param claims    verified JWT claims (workspaceId, backendUrl, etc.)
     * @param eventType the Clockify event type (e.g. "NEW_TIME_ENTRY")
     * @param rawBody   the raw request body bytes (may be empty)
     */
    void handle(NormalizedClaims claims, String eventType, byte[] rawBody);
}
