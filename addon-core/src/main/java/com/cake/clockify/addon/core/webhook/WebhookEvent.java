package com.cake.clockify.addon.core.webhook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link AddonWebhookHandler} to receive a specific Clockify event type.
 * The value must match the Clockify event type string (e.g. {@code "NEW_TIME_ENTRY"}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WebhookEvent {
    String value();
}
