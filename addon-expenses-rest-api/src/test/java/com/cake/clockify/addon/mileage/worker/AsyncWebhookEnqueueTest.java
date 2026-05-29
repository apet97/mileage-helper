package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.core.auth.filter.ClockifyWebhookAuthFilter;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addon.core.webhook.WebhookController;
import com.cake.clockify.addon.core.webhook.WebhookEventService;
import com.cake.clockify.addon.core.webhook.WebhookJobQueue;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.conversion.MileageConversionService;
import com.cake.clockify.addon.mileage.webhook.ExpenseCreatedWebhookHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * G1 contract test: when a {@link WebhookJobQueue} is wired, the webhook controller
 * MUST enqueue a PENDING row and return 2xx without invoking the handler chain
 * (and therefore without making any Clockify writes) on the request thread.
 */
class AsyncWebhookEnqueueTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void webhookEnqueuesPendingJobAndReturnsAckWithoutCallingConversion() throws Exception {
        MileageConversionService conversionService = mock(MileageConversionService.class);
        ExpenseCreatedWebhookHandler handler = new ExpenseCreatedWebhookHandler(objectMapper, conversionService);
        WebhookJobQueue queue = mock(WebhookJobQueue.class);
        WebhookEventService eventService = mock(WebhookEventService.class);
        when(eventService.isDuplicate(anyString(), anyString(), anyString())).thenReturn(false);
        when(eventService.recordEvent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EVENT_ID);

        WebhookController controller = new WebhookController(
                List.of(handler),
                properties(),
                objectMapper,
                eventService,
                queue);

        byte[] body = createdPayload("exp-async-1");
        ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ArgumentCaptor<WebhookJobQueue.EnqueueRequest> captor =
                ArgumentCaptor.forClass(WebhookJobQueue.EnqueueRequest.class);
        verify(queue).enqueue(captor.capture());
        WebhookJobQueue.EnqueueRequest enqueued = captor.getValue();

        assertThat(enqueued.addonKey()).isEqualTo("mileage-for-clockify");
        assertThat(enqueued.workspaceId()).isEqualTo("ws-async");
        assertThat(enqueued.eventType()).isEqualTo("EXPENSE_CREATED");
        assertThat(enqueued.eventId()).isEqualTo(EVENT_ID);
        assertThat(enqueued.payload()).isEqualTo(body);
        assertThat(enqueued.claimsJson()).contains("ws-async");

        // No conversion service call on the request thread — async contract.
        verifyNoInteractions(conversionService);
        verify(eventService, never()).tryStartProcessing(any());
        verify(eventService, never()).markProcessed(any());
        verify(eventService, never()).markFailed(any(), any());
    }

    @Test
    void duplicateWebhookSkipsEnqueueAndAcks() {
        MileageConversionService conversionService = mock(MileageConversionService.class);
        ExpenseCreatedWebhookHandler handler = new ExpenseCreatedWebhookHandler(objectMapper, conversionService);
        WebhookJobQueue queue = mock(WebhookJobQueue.class);
        WebhookEventService eventService = mock(WebhookEventService.class);
        when(eventService.isDuplicate(eq("mileage-for-clockify"), eq("ws-async"), anyString())).thenReturn(true);

        WebhookController controller = new WebhookController(
                List.of(handler),
                properties(),
                objectMapper,
                eventService,
                queue);

        ResponseEntity<Void> response = controller.handleWebhook(
                request("EXPENSE_CREATED"), createdPayload("dup-exp"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(queue);
        verifyNoInteractions(conversionService);
        verify(eventService, never()).recordEvent(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private static byte[] createdPayload(String expenseId) {
        return ("{\"id\":\"" + expenseId + "\",\"workspaceId\":\"ws-async\",\"categoryId\":\"cat-input\","
                + "\"quantity\":12.3,\"billable\":true,\"date\":\"2026-05-29\",\"userId\":\"user-async\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static HttpServletRequest request(String eventType) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestAttributes.NORMALIZED_CLAIMS)).thenReturn(claims());
        when(request.getAttribute(ClockifyWebhookAuthFilter.ATTR_EVENT_TYPE)).thenReturn(eventType);
        return request;
    }

    private static NormalizedClaims claims() {
        return new NormalizedClaims("ws-async", "mileage-for-clockify",
                "https://backend.example.test", "https://reports.example.test",
                null, null, "user-async", "OWNER", "en", "DEFAULT", "UTC", Instant.parse("2026-05-29T12:00:00Z"));
    }

    private static ClockifyAddonProperties properties() {
        return new ClockifyAddonProperties("mileage-for-clockify", "Mileage for Clockify",
                "https://mileage.example.test", "", true, "", "", null);
    }

    @SuppressWarnings("unused")
    private static MileageConversionSource ensureSourceImport() {
        return MileageConversionSource.WEBHOOK_CREATED;
    }
}
