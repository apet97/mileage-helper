package com.cake.clockify.addon.core.webhook;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.core.auth.filter.ClockifyWebhookAuthFilter;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookControllerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000777");

    @Test
    void handlerFailureIsRecordedAndStillAcknowledged() {
        WebhookEventService eventService = mock(WebhookEventService.class);
        byte[] body = "{\"expenseId\":\"exp-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(eventService.recordEvent("mileage-for-clockify", "ws-1", "EXPENSE_CREATED",
                WebhookDedupeKey.from("EXPENSE_CREATED", body, new ObjectMapper()).orElseThrow(),
                WebhookDedupeKey.payloadHash(body))).thenReturn(EVENT_ID);
        when(eventService.tryStartProcessing(EVENT_ID)).thenReturn(true);

        WebhookController controller = new WebhookController(
                List.of(new ThrowingExpenseWebhookHandler()),
                properties(),
                new ObjectMapper(),
                eventService,
                null);

        ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(eventService).markFailed(EVENT_ID, "boom");
    }

    @Test
    void emptyPayloadFailureIsRecordedAndStillAcknowledged() {
        WebhookEventService eventService = mock(WebhookEventService.class);
        byte[] body = new byte[0];
        when(eventService.recordEvent("mileage-for-clockify", "ws-1", "EXPENSE_CREATED",
                WebhookDedupeKey.from("EXPENSE_CREATED", body, new ObjectMapper()).orElseThrow(),
                WebhookDedupeKey.payloadHash(body))).thenReturn(EVENT_ID);
        when(eventService.tryStartProcessing(EVENT_ID)).thenReturn(true);

        WebhookController controller = new WebhookController(
                List.of(new ThrowingExpenseWebhookHandler()),
                properties(),
                new ObjectMapper(),
                eventService,
                null);

        ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(eventService).markFailed(EVENT_ID, "boom");
    }

    @Test
    void auditFailureUpdateFailureIsStillAcknowledged() {
        WebhookEventService eventService = mock(WebhookEventService.class);
        byte[] body = "{\"expenseId\":\"exp-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(eventService.recordEvent("mileage-for-clockify", "ws-1", "EXPENSE_CREATED",
                WebhookDedupeKey.from("EXPENSE_CREATED", body, new ObjectMapper()).orElseThrow(),
                WebhookDedupeKey.payloadHash(body))).thenReturn(EVENT_ID);
        when(eventService.tryStartProcessing(EVENT_ID)).thenReturn(true);
        doThrow(new IllegalStateException("audit down")).when(eventService).markFailed(EVENT_ID, "boom");

        WebhookController controller = new WebhookController(
                List.of(new ThrowingExpenseWebhookHandler()),
                properties(),
                new ObjectMapper(),
                eventService,
                null);

        ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(eventService).markFailed(EVENT_ID, "boom");
    }

    @Test
    void auditProcessedUpdateFailureIsStillAcknowledged() {
        WebhookEventService eventService = mock(WebhookEventService.class);
        byte[] body = "{\"expenseId\":\"exp-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(eventService.recordEvent("mileage-for-clockify", "ws-1", "EXPENSE_CREATED",
                WebhookDedupeKey.from("EXPENSE_CREATED", body, new ObjectMapper()).orElseThrow(),
                WebhookDedupeKey.payloadHash(body))).thenReturn(EVENT_ID);
        when(eventService.tryStartProcessing(EVENT_ID)).thenReturn(true);
        doThrow(new IllegalStateException("audit down")).when(eventService).markProcessed(EVENT_ID);

        WebhookController controller = new WebhookController(
                List.of(new AcknowledgingExpenseWebhookHandler()),
                properties(),
                new ObjectMapper(),
                eventService,
                null);

        ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(eventService).markProcessed(EVENT_ID);
    }

    @Test
    void dedupeLookupFailureIsAcknowledgedWithoutCallingHandler() {
        WebhookEventService eventService = mock(WebhookEventService.class);
        byte[] body = "{\"expenseId\":\"exp-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(eventService.isDuplicate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("db down"));
        AcknowledgingExpenseWebhookHandler handler = new AcknowledgingExpenseWebhookHandler();
        WebhookController controller = new WebhookController(List.of(handler), properties(), new ObjectMapper(), eventService, null);

        ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(handler.calls).isZero();
    }

    @Test
    void recordEventFailureIsAcknowledgedWithoutCallingHandler() {
        WebhookEventService eventService = mock(WebhookEventService.class);
        byte[] body = "{\"expenseId\":\"exp-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(eventService.recordEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("db down"));
        AcknowledgingExpenseWebhookHandler handler = new AcknowledgingExpenseWebhookHandler();
        WebhookController controller = new WebhookController(List.of(handler), properties(), new ObjectMapper(), eventService, null);

        ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(handler.calls).isZero();
    }

    @Test
    void processingStartFailureIsAcknowledgedWithoutCallingHandler() {
        WebhookEventService eventService = mock(WebhookEventService.class);
        byte[] body = "{\"expenseId\":\"exp-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(eventService.recordEvent("mileage-for-clockify", "ws-1", "EXPENSE_CREATED",
                WebhookDedupeKey.from("EXPENSE_CREATED", body, new ObjectMapper()).orElseThrow(),
                WebhookDedupeKey.payloadHash(body))).thenReturn(EVENT_ID);
        when(eventService.tryStartProcessing(EVENT_ID)).thenThrow(new IllegalStateException("db down"));
        AcknowledgingExpenseWebhookHandler handler = new AcknowledgingExpenseWebhookHandler();
        WebhookController controller = new WebhookController(List.of(handler), properties(), new ObjectMapper(), eventService, null);

        ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(handler.calls).isZero();
    }

    private static HttpServletRequest request(String eventType) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestAttributes.NORMALIZED_CLAIMS)).thenReturn(claims());
        when(request.getAttribute(ClockifyWebhookAuthFilter.ATTR_EVENT_TYPE)).thenReturn(eventType);
        return request;
    }

    private static NormalizedClaims claims() {
        return new NormalizedClaims("ws-1", "mileage-for-clockify", "https://backend.example.test/api",
                "https://reports.example.test", null, null, "user-1", "OWNER", "en", "DEFAULT", "UTC", Instant.now());
    }

    private static ClockifyAddonProperties properties() {
        return new ClockifyAddonProperties("mileage-for-clockify", "Mileage for Clockify",
                "https://mileage.example.test", "", true, "", "", null);
    }

    @WebhookEvent("EXPENSE_CREATED")
    private static final class ThrowingExpenseWebhookHandler implements AddonWebhookHandler {
        @Override
        public void handle(NormalizedClaims claims, String eventType, byte[] rawBody) {
            throw new IllegalStateException("boom");
        }
    }

    @WebhookEvent("EXPENSE_CREATED")
    private static final class AcknowledgingExpenseWebhookHandler implements AddonWebhookHandler {
        int calls;

        @Override
        public void handle(NormalizedClaims claims, String eventType, byte[] rawBody) {
            calls++;
        }
    }
}
