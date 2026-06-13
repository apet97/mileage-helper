package com.cake.clockify.addon.core.webhook;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.core.auth.filter.ClockifyWebhookAuthFilter;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Receives verified Clockify webhook deliveries.
 *
 * <p>When a {@link WebhookJobQueue} bean is wired (production path), the request thread
 * verifies → dedupes → persists a PENDING job row and returns 2xx with zero Clockify
 * writes. A worker (see {@code WebhookJobWorker} in the add-on app) claims the job via
 * {@code SELECT … FOR UPDATE SKIP LOCKED} and invokes the matching handler.
 *
 * <p>When no queue is wired (legacy tests / minimal apps without a database), the
 * controller falls back to synchronous handler dispatch — preserving the original
 * 2xx-after-failure contract for callers that do not need horizontal scaling.
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final Map<String, AddonWebhookHandler> handlerMap = new HashMap<>();
    private final ClockifyAddonProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookEventService eventService;
    private final WebhookJobQueue jobQueue;

    public WebhookController(
            List<AddonWebhookHandler> handlers,
            ClockifyAddonProperties properties,
            ObjectMapper objectMapper,
            @Autowired(required = false) WebhookEventService eventService,
            @Autowired(required = false) WebhookJobQueue jobQueue) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventService = eventService;
        this.jobQueue = jobQueue;
        for (AddonWebhookHandler handler : handlers) {
            WebhookEvent annotation = AnnotationUtils.findAnnotation(handler.getClass(), WebhookEvent.class);
            if (annotation != null) {
                String type = annotation.value().toUpperCase(Locale.ROOT);
                if (handlerMap.containsKey(type)) {
                    log.warn("webhook.handler.duplicate-registration: event={}", type);
                }
                handlerMap.put(type, handler);
            }
        }
    }

    @PostMapping("/**")
    public ResponseEntity<Void> handleWebhook(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        String rawEventType = (String) request.getAttribute(ClockifyWebhookAuthFilter.ATTR_EVENT_TYPE);

        if (rawEventType == null) {
            log.warn("webhook.handler.unauthorized: missing verified event type attribute");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String eventType = rawEventType.toUpperCase(Locale.ROOT);

        AddonWebhookHandler targetHandler = handlerMap.get(eventType);
        if (targetHandler == null) {
            log.debug("webhook.handler.unsupported: event={} has no registered handler", rawEventType);
            return ResponseEntity.ok().build();
        }

        Optional<String> dedupeKeyOpt = WebhookDedupeKey.from(rawEventType, body, objectMapper);
        if (dedupeKeyOpt.isEmpty()) {
            // No deduplication key derivable from the body — handler chain would no-op anyway.
            return ResponseEntity.ok().build();
        }
        String dedupeKey = dedupeKeyOpt.get();

        UUID eventId = null;
        if (eventService != null) {
            try {
                if (eventService.isDuplicate(properties.key(), claims.workspaceId(), dedupeKey)) {
                    log.info("webhook.handler.duplicate: workspace={} event={} dedupeKey={}",
                            claims.workspaceId(), rawEventType, dedupeKey);
                    return ResponseEntity.ok().build();
                }
                String payloadHash = WebhookDedupeKey.payloadHash(body);
                eventId = eventService.recordEvent(properties.key(), claims.workspaceId(),
                        eventType, dedupeKey, payloadHash);
            } catch (Exception e) {
                log.error("webhook.handler.audit-start-failed: workspace={} event={}",
                        claims.workspaceId(), rawEventType, e);
                return ResponseEntity.ok().build();
            }
        }

        if (jobQueue != null) {
            return enqueueAndAck(claims, eventType, eventId, dedupeKey, body, rawEventType);
        }

        return runSynchronously(targetHandler, claims, eventType, eventId, body, rawEventType);
    }

    private ResponseEntity<Void> enqueueAndAck(
            NormalizedClaims claims,
            String eventType,
            UUID eventId,
            String dedupeKey,
            byte[] body,
            String rawEventType) {
        try {
            String claimsJson = objectMapper.writeValueAsString(claims);
            jobQueue.enqueue(new WebhookJobQueue.EnqueueRequest(
                    properties.key(),
                    claims.workspaceId(),
                    eventType,
                    eventId,
                    dedupeKey,
                    body,
                    claimsJson));
        } catch (JsonProcessingException e) {
            log.error("webhook.queue.claims-serialize-failed: workspace={} event={}",
                    claims.workspaceId(), rawEventType, e);
            markFailed(eventId, e);
        } catch (Exception e) {
            log.error("webhook.queue.enqueue-failed: workspace={} event={}",
                    claims.workspaceId(), rawEventType, e);
            markFailed(eventId, e);
        }
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Void> runSynchronously(
            AddonWebhookHandler targetHandler,
            NormalizedClaims claims,
            String eventType,
            UUID eventId,
            byte[] body,
            String rawEventType) {
        if (eventService != null && eventId != null) {
            try {
                if (!eventService.tryStartProcessing(eventId)) {
                    log.info("webhook.handler.processing-conflict: eventId={}", eventId);
                    return ResponseEntity.ok().build();
                }
            } catch (Exception e) {
                log.error("webhook.handler.processing-start-failed: eventId={}", eventId, e);
                return ResponseEntity.ok().build();
            }
        }
        try {
            targetHandler.handle(claims, eventType, body);
            markProcessed(eventId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("webhook.handler.failed: workspace={} event={}",
                    claims.workspaceId(), rawEventType, e);
            markFailed(eventId, e);
            return ResponseEntity.ok().build();
        }
    }

    private void markProcessed(UUID eventId) {
        if (eventService == null || eventId == null) {
            return;
        }
        try {
            eventService.markProcessed(eventId);
        } catch (Exception e) {
            log.error("webhook.handler.status-update-failed: eventId={} status=PROCESSED", eventId, e);
        }
    }

    private void markFailed(UUID eventId, Exception failure) {
        if (eventService == null || eventId == null) {
            return;
        }
        try {
            eventService.markFailed(eventId, failure.getMessage());
        } catch (Exception e) {
            log.error("webhook.handler.status-update-failed: eventId={} status=FAILED", eventId, e);
        }
    }
}
