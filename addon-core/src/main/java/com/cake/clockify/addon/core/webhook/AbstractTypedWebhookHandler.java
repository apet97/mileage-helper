package com.cake.clockify.addon.core.webhook;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for strongly-typed webhook handlers. Automatically parses the raw JSON payload
 * into the specified type {@code T} before invoking the handler logic.
 *
 * @param <T> the type of the parsed webhook payload
 */
public abstract class AbstractTypedWebhookHandler<T> implements AddonWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractTypedWebhookHandler.class);

    private final ObjectMapper objectMapper;
    private final Class<T> payloadType;

    /**
     * @param objectMapper the Jackson object mapper for deserialization
     * @param payloadType  the class representing the expected JSON payload
     */
    protected AbstractTypedWebhookHandler(ObjectMapper objectMapper, Class<T> payloadType) {
        this.objectMapper = objectMapper;
        this.payloadType = payloadType;
    }

    @Override
    public final void handle(NormalizedClaims claims, String eventType, byte[] rawBody) {
        T payload = null;
        if (rawBody != null && rawBody.length > 0) {
            try {
                payload = objectMapper.readValue(rawBody, payloadType);
            } catch (Exception e) {
                log.error("Failed to parse {} webhook payload into {}: {}", eventType, payloadType.getSimpleName(), e.getMessage());
                throw new IllegalArgumentException("Invalid webhook payload format", e);
            }
        }
        handleTyped(claims, eventType, payload);
    }

    /**
     * Handles the webhook event with the strongly-typed payload.
     *
     * @param claims    the verified JWT claims
     * @param eventType the type of the webhook event
     * @param payload   the parsed payload, or null if the body was empty
     */
    protected abstract void handleTyped(NormalizedClaims claims, String eventType, T payload);
}
