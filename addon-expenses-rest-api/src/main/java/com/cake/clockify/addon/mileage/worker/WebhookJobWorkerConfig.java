package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.core.webhook.AddonWebhookHandler;
import com.cake.clockify.addon.core.webhook.WebhookEventService;
import com.cake.clockify.addon.db.repository.AddonWebhookJobRepository;
import com.cake.clockify.addon.db.service.AddonWebhookJobClaimService;
import com.cake.clockify.addon.mileage.metrics.WebhookJobMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * Activates the async webhook worker. Disable in single-process dev (or in the
 * web-only pod of a horizontal split) by setting {@code MILEAGE_WORKER_ENABLED=false}.
 *
 * <p>{@link EnableScheduling} is scoped to this config so the {@code @Scheduled} poll
 * and reaper methods on {@link WebhookJobWorker} only register when the worker is on.
 */
@Configuration
@EnableConfigurationProperties(MileageWorkerProperties.class)
@ConditionalOnProperty(name = "mileage.worker.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AddonWebhookJobRepository.class)
@EnableScheduling
public class WebhookJobWorkerConfig {

    @Bean
    public WebhookJobWorker webhookJobWorker(
            List<AddonWebhookHandler> handlers,
            AddonWebhookJobClaimService claimService,
            ObjectProvider<WebhookEventService> eventService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            MileageWorkerProperties properties) {
        return new WebhookJobWorker(
                handlers,
                claimService,
                eventService.getIfAvailable(),
                objectMapper,
                meterRegistry,
                properties);
    }

    @Bean
    public WebhookJobMetrics webhookJobMetrics(
            MeterRegistry meterRegistry,
            AddonWebhookJobClaimService claimService) {
        return new WebhookJobMetrics(meterRegistry, claimService);
    }
}
