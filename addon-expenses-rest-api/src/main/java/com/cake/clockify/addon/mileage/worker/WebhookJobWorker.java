package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.webhook.AddonWebhookHandler;
import com.cake.clockify.addon.core.webhook.WebhookEvent;
import com.cake.clockify.addon.core.webhook.WebhookEventService;
import com.cake.clockify.addon.db.entity.AddonWebhookJob;
import com.cake.clockify.addon.db.service.AddonWebhookJobClaimService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Polls the {@code addon_webhook_jobs} queue, claims PENDING rows via
 * {@code SELECT … FOR UPDATE SKIP LOCKED}, and dispatches each one to the matching
 * {@link AddonWebhookHandler}.
 *
 * <p>Two safety properties matter:
 * <ol>
 *   <li>SKIP LOCKED guarantees no two workers ever claim the same job row.</li>
 *   <li>The claim transaction is short and ends before the Clockify network call,
 *   so the database lock is released before any high-latency IO.</li>
 * </ol>
 *
 * <p>A second scheduled task reaps rows that were claimed but never completed
 * (worker crash, pod kill) and resets them to PENDING.
 */
public class WebhookJobWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookJobWorker.class);
    private static final String TIMER_NAME = "mileage.webhook.job.process";

    private final AddonWebhookJobClaimService claimService;
    private final WebhookEventService eventService;
    private final ObjectMapper objectMapper;
    private final MileageWorkerProperties properties;
    private final Map<String, AddonWebhookHandler> handlerMap = new HashMap<>();
    private final Timer processTimer;
    private final MeterRegistry meterRegistry;

    public WebhookJobWorker(
            List<AddonWebhookHandler> handlers,
            AddonWebhookJobClaimService claimService,
            WebhookEventService eventService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            MileageWorkerProperties properties) {
        this.claimService = claimService;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.processTimer = Timer.builder(TIMER_NAME)
                .description("Latency of a single webhook job process: claim → dispatch → mark complete")
                .register(meterRegistry);
        for (AddonWebhookHandler handler : handlers) {
            WebhookEvent annotation = AnnotationUtils.findAnnotation(handler.getClass(), WebhookEvent.class);
            if (annotation != null) {
                handlerMap.put(annotation.value().toUpperCase(Locale.ROOT), handler);
            }
        }
    }

    @Scheduled(fixedDelayString = "${mileage.worker.poll-delay-ms:250}")
    public void pollAndProcess() {
        int batchSize = Math.max(1, properties.batchSize());
        for (int i = 0; i < batchSize; i++) {
            if (!processOneJob()) {
                return;
            }
        }
    }

    /**
     * Resets jobs stuck in CLAIMED past the configured timeout — e.g., a worker crashed
     * mid-process. Idempotent, safe to run alongside the polling loop.
     */
    @Scheduled(fixedDelay = 60_000L)
    public void reapStuckJobs() {
        long timeout = Math.max(60L, properties.stuckJobTimeoutSeconds());
        int reset = claimService.resetStuckJobs(Instant.now().minusSeconds(timeout));
        if (reset > 0) {
            log.warn("mileage.webhook.job.reaper count={} stuckJobTimeoutSeconds={}", reset, timeout);
        }
    }

    boolean processOneJob() {
        Optional<AddonWebhookJob> claimed = claimService.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        AddonWebhookJob job = claimed.get();
        try {
            dispatch(job);
            claimService.markCompleted(job.getId(), AddonWebhookJob.STATUS_COMPLETED, null);
            markEventProcessed(job);
        } catch (Exception e) {
            String redacted = redactErrorMessage(e);
            claimService.markCompleted(job.getId(), AddonWebhookJob.STATUS_FAILED, redacted);
            markEventFailed(job, redacted);
            log.error("mileage.webhook.job.failed jobId={} eventType={}", job.getId(), job.getEventType(), e);
        } finally {
            sample.stop(processTimer);
        }
        return true;
    }

    private void dispatch(AddonWebhookJob job) throws Exception {
        AddonWebhookHandler handler = handlerMap.get(job.getEventType().toUpperCase(Locale.ROOT));
        if (handler == null) {
            log.debug("mileage.webhook.job.no-handler eventType={}", job.getEventType());
            return;
        }
        if (eventService != null && job.getEventId() != null
                && !eventService.tryStartProcessing(job.getEventId())) {
            log.info("mileage.webhook.job.event-already-processing eventId={}", job.getEventId());
            return;
        }
        NormalizedClaims claims = objectMapper.readValue(job.getClaimsJson(), NormalizedClaims.class);
        handler.handle(claims, job.getEventType(), job.getPayload());
    }

    private void markEventProcessed(AddonWebhookJob job) {
        if (eventService == null || job.getEventId() == null) {
            return;
        }
        try {
            eventService.markProcessed(job.getEventId());
        } catch (Exception e) {
            log.error("mileage.webhook.job.event-status-failed eventId={} status=PROCESSED",
                    job.getEventId(), e);
        }
    }

    private void markEventFailed(AddonWebhookJob job, String redactedError) {
        if (eventService == null || job.getEventId() == null) {
            return;
        }
        try {
            eventService.markFailed(job.getEventId(), redactedError);
        } catch (Exception e) {
            log.error("mileage.webhook.job.event-status-failed eventId={} status=FAILED",
                    job.getEventId(), e);
        }
    }

    private static String redactErrorMessage(Throwable t) {
        String raw = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        // Same redaction rules the addon-db event service uses for audit messages.
        return raw
                .replaceAll("(?i)(\"(?:x-api-key|x-addon-token|authorization|authToken|addonToken|api[-_]?key|token|password|secret)\"\\s*:\\s*\")[^\"]*(\")", "$1REDACTED$2")
                .replaceAll("(?i)(authorization\\s*:\\s*bearer\\s+)[^,}\\s\\n]+", "$1REDACTED")
                .replaceAll("(?i)(bearer\\s+)[^,}\\s\\n]+", "$1REDACTED")
                .replaceAll("(?i)((?:x-api-key|x-addon-token|authorization|authToken|addonToken|api[-_]?key|token|password|secret)\\s*[:=]\\s*)[^,}\\s\\n]+", "$1REDACTED");
    }
}
