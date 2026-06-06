package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.core.webhook.AddonWebhookHandler;
import com.cake.clockify.addon.core.webhook.WebhookEventService;
import com.cake.clockify.addon.db.config.AddonDbAutoConfiguration;
import com.cake.clockify.addon.db.service.AddonWebhookJobClaimService;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.metrics.WebhookJobMetrics;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.List;

/**
 * Activates the async webhook worker. Disable in the web-only pod of a split topology
 * by setting {@code MILEAGE_WORKER_ENABLED=false}.
 *
 * <p>Declared as an {@link AutoConfiguration} ordered AFTER {@link AddonDbAutoConfiguration}
 * so Spring guarantees the repository / claim-service beans are registered before any
 * {@link ConditionalOnBean} check on the worker beans is evaluated. A previous attempt
 * to use {@code @Configuration} + {@code @ConditionalOnBean} silently skipped the
 * worker in production (the 2026-05-30 deploy exposed conversion counters but neither
 * the queue-depth gauge nor the worker timer); the auto-config ordering fixes that.
 *
 * <p>{@link EnableScheduling} is scoped to this config so the {@code @Scheduled} poll
 * and reaper methods on {@link WebhookJobWorker} only register when the worker is on.
 */
@AutoConfiguration(after = AddonDbAutoConfiguration.class)
@EnableConfigurationProperties(MileageWorkerProperties.class)
@ConditionalOnProperty(name = "mileage.worker.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AddonWebhookJobClaimService.class)
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

    @Bean
    public MileageNoteReconcileWorker mileageNoteReconcileWorker(
            MileageConversionRepository conversionRepository,
            ClockifyExpenseGateway gateway,
            MileageNoteService noteService) {
        return new MileageNoteReconcileWorker(conversionRepository, gateway, noteService, Clock.systemUTC());
    }
}
