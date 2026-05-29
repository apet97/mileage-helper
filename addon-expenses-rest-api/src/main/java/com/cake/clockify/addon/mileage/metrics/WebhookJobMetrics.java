package com.cake.clockify.addon.mileage.metrics;

import com.cake.clockify.addon.db.service.AddonWebhookJobClaimService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Queue-depth gauge for the async webhook job table. Scraped via {@code /actuator/prometheus};
 * a flat or growing value indicates worker backpressure.
 *
 * <p>Tagged ONLY by status. Never tag with workspaceId, userId, or event payload contents.
 */
public class WebhookJobMetrics {

    public static final String QUEUE_DEPTH_GAUGE = "mileage.webhook.queue.depth";

    public WebhookJobMetrics(MeterRegistry registry, AddonWebhookJobClaimService claimService) {
        Gauge.builder(QUEUE_DEPTH_GAUGE, claimService, AddonWebhookJobClaimService::countPending)
                .description("Pending webhook jobs awaiting worker pickup")
                .tag("status", "PENDING")
                .register(registry);
    }
}
