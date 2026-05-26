package com.cake.clockify.addon.core.webhook;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.core.auth.filter.ClockifyWebhookAuthFilter;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller to handle verified Clockify webhook events and dispatch them to handlers.
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final Map<String, AddonWebhookHandler> handlerMap = new java.util.HashMap<>();
    private final ClockifyAddonProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookEventService eventService;

    public WebhookController(
            List<AddonWebhookHandler> handlers,
            ClockifyAddonProperties properties,
            ObjectMapper objectMapper,
            @Autowired(required = false) WebhookEventService eventService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventService = eventService;
        for (AddonWebhookHandler handler : handlers) {
            WebhookEvent annotation = AnnotationUtils.findAnnotation(handler.getClass(), WebhookEvent.class);
            if (annotation != null) {
                String type = annotation.value().toUpperCase(java.util.Locale.ROOT);
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
        String eventType = (String) request.getAttribute(ClockifyWebhookAuthFilter.ATTR_EVENT_TYPE);

        if (eventType == null) {
            log.warn("webhook.handler.unauthorized: missing verified event type attribute");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AddonWebhookHandler targetHandler = handlerMap.get(eventType.toUpperCase(java.util.Locale.ROOT));

        if (targetHandler == null) {
            log.debug("webhook.handler.unsupported: event={} has no registered handler", eventType);
            return ResponseEntity.ok().build();
        }

        UUID eventId = null;
        if (eventService != null) {
            Optional<String> dedupeKeyOpt = WebhookDedupeKey.from(eventType, body, objectMapper);
            if (dedupeKeyOpt.isPresent()) {
                String dedupeKey = dedupeKeyOpt.get();
                if (eventService.isDuplicate(properties.key(), claims.workspaceId(), dedupeKey)) {
                    log.info("webhook.handler.duplicate: workspace={} event={} dedupeKey={}",
                            claims.workspaceId(), eventType, dedupeKey);
                    return ResponseEntity.ok().build();
                }

                String payloadHash = WebhookDedupeKey.payloadHash(body);
                eventId = eventService.recordEvent(properties.key(), claims.workspaceId(), eventType, dedupeKey, payloadHash);

                if (eventId != null && !eventService.tryStartProcessing(eventId)) {
                    log.info("webhook.handler.processing-conflict: eventId={}", eventId);
                    return ResponseEntity.ok().build();
                }
            }
        }

        try {
            targetHandler.handle(claims, eventType, body);
            markProcessed(eventId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("webhook.handler.failed: workspace={} event={}", claims.workspaceId(), eventType, e);
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
