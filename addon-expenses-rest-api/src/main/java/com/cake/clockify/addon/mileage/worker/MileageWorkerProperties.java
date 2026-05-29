package com.cake.clockify.addon.mileage.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for the async webhook worker. All knobs survive deploys via env vars
 * (see {@code application.yaml} for the {@code MILEAGE_WORKER_*} mappings).
 */
@ConfigurationProperties(prefix = "mileage.worker")
public record MileageWorkerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("250") long pollDelayMs,
        @DefaultValue("300") long stuckJobTimeoutSeconds,
        @DefaultValue("8") int batchSize) {
}
