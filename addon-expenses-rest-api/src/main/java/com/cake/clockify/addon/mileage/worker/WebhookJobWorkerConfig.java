package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.core.webhook.AddonWebhookHandler;
import com.cake.clockify.addon.core.webhook.WebhookEventService;
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
 * Activates the async webhook worker. Disable in the web-only pod of a split topology
 * by setting {@code MILEAGE_WORKER_ENABLED=false}.
 *
 * <p>{@link EnableScheduling} is scoped to this config so the {@code @Scheduled} poll
 * and reaper methods on {@link WebhookJobWorker} only register when the worker is on.
 *
 * <p>{@link ConditionalOnBean} is applied at the {@code @Bean} method level (not the
 * class level). At the class level it would evaluate BEFORE
 * {@code JpaRepositoriesAutoConfiguration} registers the repository beans, silently
 * skipping the entire worker config in production (verified via the 2026-05-30 deploy
 * that exposed conversion counters but no queue/timer). At the method level Spring
 * defers evaluation until each bean is about to be instantiated, by which point the
 * auto-configured beans are resolvable. Tests that exclude
 * {@code AddonDbAutoConfiguration} still cleanly skip both worker beans.
 */
@Configuration
@EnableConfigurationProperties(MileageWorkerProperties.class)
@ConditionalOnProperty(name = "mileage.worker.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class WebhookJobWorkerConfig {

    @Bean
    @ConditionalOnBean(AddonWebhookJobClaimService.class)
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
    @ConditionalOnBean(AddonWebhookJobClaimService.class)
    public WebhookJobMetrics webhookJobMetrics(
            MeterRegistry meterRegistry,
            AddonWebhookJobClaimService claimService) {
        return new WebhookJobMetrics(meterRegistry, claimService);
    }
}
