package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.webhook.AddonWebhookHandler;
import com.cake.clockify.addon.core.webhook.WebhookEvent;
import com.cake.clockify.addon.core.webhook.WebhookEventService;
import com.cake.clockify.addon.db.entity.AddonWebhookJob;
import com.cake.clockify.addon.db.service.AddonWebhookJobClaimService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookJobWorkerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-0000000abc01");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000abc02");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void workerDispatchesClaimedJobAndMarksCompleted() {
        RecordingHandler handler = new RecordingHandler();
        AddonWebhookJobClaimService claimService = mock(AddonWebhookJobClaimService.class);
        WebhookEventService eventService = mock(WebhookEventService.class);
        when(eventService.tryStartProcessing(EVENT_ID)).thenReturn(true);
        when(claimService.claimNext()).thenReturn(Optional.of(job("EXPENSE_CREATED")));

        WebhookJobWorker worker = newWorker(handler, claimService, eventService);
        boolean processed = worker.processOneJob();

        assertThat(processed).isTrue();
        assertThat(handler.invocations).isEqualTo(1);
        assertThat(handler.lastClaims.workspaceId()).isEqualTo("ws-worker");
        assertThat(handler.lastEventType).isEqualTo("EXPENSE_CREATED");
        verify(claimService).markCompleted(JOB_ID, AddonWebhookJob.STATUS_COMPLETED, null);
        verify(eventService).markProcessed(EVENT_ID);
    }

    @Test
    void workerMarksJobFailedAndEventFailedWhenHandlerThrows() {
        ThrowingHandler handler = new ThrowingHandler();
        AddonWebhookJobClaimService claimService = mock(AddonWebhookJobClaimService.class);
        WebhookEventService eventService = mock(WebhookEventService.class);
        when(eventService.tryStartProcessing(EVENT_ID)).thenReturn(true);
        when(claimService.claimNext()).thenReturn(Optional.of(job("EXPENSE_CREATED")));

        WebhookJobWorker worker = newWorker(handler, claimService, eventService);
        worker.processOneJob();

        verify(claimService).markCompleted(eq(JOB_ID), eq(AddonWebhookJob.STATUS_FAILED), any());
        verify(eventService).markFailed(eq(EVENT_ID), any());
    }

    @Test
    void workerDoesNotCompleteStaleProcessingEventWithoutDispatch() {
        RecordingHandler handler = new RecordingHandler();
        AddonWebhookJobClaimService claimService = mock(AddonWebhookJobClaimService.class);
        WebhookEventService eventService = mock(WebhookEventService.class);
        when(eventService.tryStartProcessing(EVENT_ID)).thenReturn(false);
        when(claimService.claimNext()).thenReturn(Optional.of(job("EXPENSE_CREATED")));

        WebhookJobWorker worker = newWorker(handler, claimService, eventService);
        boolean processed = worker.processOneJob();

        assertThat(processed).isTrue();
        assertThat(handler.invocations).isZero();
        verify(claimService).markCompleted(JOB_ID, AddonWebhookJob.STATUS_FAILED, "event is already processing");
        verify(eventService, never()).markProcessed(EVENT_ID);
    }

    @Test
    void emptyQueuePollReturnsFalseAndCallsNoHandler() {
        RecordingHandler handler = new RecordingHandler();
        AddonWebhookJobClaimService claimService = mock(AddonWebhookJobClaimService.class);
        WebhookEventService eventService = mock(WebhookEventService.class);
        when(claimService.claimNext()).thenReturn(Optional.empty());

        WebhookJobWorker worker = newWorker(handler, claimService, eventService);
        boolean processed = worker.processOneJob();

        assertThat(processed).isFalse();
        assertThat(handler.invocations).isZero();
        verify(claimService, never()).markCompleted(any(), any(), any());
    }

    private WebhookJobWorker newWorker(
            AddonWebhookHandler handler,
            AddonWebhookJobClaimService claimService,
            WebhookEventService eventService) {
        return new WebhookJobWorker(
                List.of(handler),
                claimService,
                eventService,
                objectMapper,
                new SimpleMeterRegistry(),
                new MileageWorkerProperties(true, 250L, 300L, 8));
    }

    private static AddonWebhookJob job(String eventType) {
        AddonWebhookJob job = new AddonWebhookJob();
        job.setId(JOB_ID);
        job.setAddonKey("mileage-for-clockify");
        job.setWorkspaceId("ws-worker");
        job.setEventType(eventType);
        job.setDedupeKey("dedupe-worker");
        job.setEventId(EVENT_ID);
        job.setPayload("{\"id\":\"exp-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        job.setClaimsJson("""
                {"workspaceId":"ws-worker","addonId":"mileage-for-clockify",
                "backendUrl":"https://backend.example.test","reportsUrl":null,
                "locationsUrl":null,"screenshotsUrl":null,"userId":"user-1",
                "workspaceRole":"OWNER","language":"en","theme":"DEFAULT","userTimeZone":"UTC",
                "iat":"2026-05-29T12:00:00Z"}
                """.replace("\n", "").replace("                ", ""));
        job.setStatus(AddonWebhookJob.STATUS_CLAIMED);
        job.setClaimedAt(Instant.now());
        job.setAttempts(1);
        return job;
    }

    @WebhookEvent("EXPENSE_CREATED")
    private static final class RecordingHandler implements AddonWebhookHandler {
        int invocations = 0;
        NormalizedClaims lastClaims;
        String lastEventType;
        final AtomicReference<byte[]> lastBody = new AtomicReference<>();

        @Override
        public void handle(NormalizedClaims claims, String eventType, byte[] rawBody) {
            invocations++;
            lastClaims = claims;
            lastEventType = eventType;
            lastBody.set(rawBody);
        }
    }

    @WebhookEvent("EXPENSE_CREATED")
    private static final class ThrowingHandler implements AddonWebhookHandler {
        @Override
        public void handle(NormalizedClaims claims, String eventType, byte[] rawBody) {
            throw new IllegalStateException("worker test boom");
        }
    }
}
