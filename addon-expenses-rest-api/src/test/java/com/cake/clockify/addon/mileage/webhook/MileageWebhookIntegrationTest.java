package com.cake.clockify.addon.mileage.webhook;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.filter.ClockifyWebhookAuthFilter;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.core.webhook.WebhookController;
import com.cake.clockify.addon.core.webhook.WebhookTokenLookup;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.conversion.MileageConversionService;
import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageWebhookIntegrationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MileageConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = mock(MileageConversionService.class);
    }

    @Test
    void expenseCreatedWebhookFetchesAndConverts() {
        NormalizedClaims claims = claims();
        new ExpenseCreatedWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_CREATED", createdPayload("exp-1"));

        verify(conversionService).convertIfEligible(
                claims, "exp-1", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");
    }

    @Test
    void expenseCreatedReferencePayloadFetchesAndConverts() {
        NormalizedClaims claims = claims();
        new ExpenseCreatedWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_CREATED", refPayload("exp-ref"));

        verify(conversionService).convertIfEligible(
                claims, "exp-ref", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");
    }

    @Test
    void expenseUpdatedWebhookAfterConversionIsIgnored() {
        NormalizedClaims claims = claims();
        new ExpenseUpdatedWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_UPDATED", refPayload("exp-1"));

        verify(conversionService).convertIfEligible(
                claims, "exp-1", MileageConversionSource.WEBHOOK_UPDATED, "EXPENSE_UPDATED");
    }

    @Test
    void expenseUpdatedWebhookCanRepairUnconvertedInputExpense() {
        NormalizedClaims claims = claims();
        new ExpenseUpdatedWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_UPDATED", refPayload("exp-unconverted"));

        verify(conversionService).convertIfEligible(
                claims, "exp-unconverted", MileageConversionSource.WEBHOOK_UPDATED, "EXPENSE_UPDATED");
    }

    @Test
    void expenseUpdatedFullPayloadFetchesAndConverts() {
        NormalizedClaims claims = claims();
        new ExpenseUpdatedWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_UPDATED", createdPayload("exp-updated-full"));

        verify(conversionService).convertIfEligible(
                claims, "exp-updated-full", MileageConversionSource.WEBHOOK_UPDATED, "EXPENSE_UPDATED");
    }

    @Test
    void expenseDeletedMarksAuditDeleted() {
        NormalizedClaims claims = claims();
        new ExpenseDeletedWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_DELETED", refPayload("exp-1"));

        verify(conversionService).markDeleted(claims, "exp-1");
    }

    @Test
    void expenseDeletedFullPayloadMarksAuditDeleted() {
        NormalizedClaims claims = claims();
        new ExpenseDeletedWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_DELETED", createdPayload("exp-full"));

        verify(conversionService).markDeleted(claims, "exp-full");
    }

    @Test
    void expenseRestoredAlreadyConvertedIsIgnored() {
        NormalizedClaims claims = claims();
        new ExpenseRestoredWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_RESTORED", createdPayload("exp-1"));

        verify(conversionService).convertIfEligible(
                claims, "exp-1", MileageConversionSource.WEBHOOK_RESTORED, "EXPENSE_RESTORED");
    }

    @Test
    void expenseRestoredInputCategoryConverts() {
        NormalizedClaims claims = claims();
        new ExpenseRestoredWebhookHandler(objectMapper, conversionService)
                .handle(claims, "EXPENSE_RESTORED", createdPayload("exp-restored"));

        verify(conversionService).convertIfEligible(
                claims, "exp-restored", MileageConversionSource.WEBHOOK_RESTORED, "EXPENSE_RESTORED");
    }

    @Test
    void duplicateWebhookDeliveryDoesNotCreateSecondConversion() {
        new ExpenseUpdatedWebhookHandler(objectMapper, conversionService)
                .handle(claims(), "EXPENSE_UPDATED", refPayload(""));

        verify(conversionService, never()).convertIfEligible(
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void nullPayloadReturnsWithoutCallingConversionService() {
        new ExpenseCreatedWebhookHandler(objectMapper, conversionService)
                .handle(claims(), "EXPENSE_CREATED", new byte[0]);

        verifyNoInteractions(conversionService);
    }

    @Test
    void invalidWebhookSignatureIsUnauthorized() throws Exception {
        ClockifyAddonProperties props = props();
        ClockifyWebhookAuthFilter filter = new ClockifyWebhookAuthFilter(
                new RejectingSignatureParser(props.key()),
                mock(WebhookTokenLookup.class),
                new TokenCodec(Map.of("k1", "00000000000000000000000000000000000000000000000000000000000000aa"), "k1"),
                props);
        WebhookController controller = new WebhookController(
                List.of(new ExpenseCreatedWebhookHandler(objectMapper, conversionService)),
                props,
                objectMapper,
                null,
                null);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(filter)
                .build();

        mockMvc.perform(post("/webhook/expense-created")
                        .header("Clockify-Signature", "bad-signature")
                        .header("Clockify-Webhook-Event-Type", "EXPENSE_CREATED")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(conversionService);
    }

    private static byte[] createdPayload(String expenseId) {
        return """
                {"id":"%s","workspaceId":"ws-webhook","userId":"user-1","date":"2026-05-24","categoryId":"cat-input","quantity":37.4,"billable":true,"total":0}
                """.formatted(expenseId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] refPayload(String expenseId) {
        return """
                {"workspaceId":"ws-webhook","userId":"user-1","expenseId":"%s","categoryId":"cat-input"}
                """.formatted(expenseId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static NormalizedClaims claims() {
        return new NormalizedClaims("ws-webhook", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", "OWNER", "en", "DEFAULT", "UTC", Instant.now());
    }

    private static ClockifyAddonProperties props() {
        return new ClockifyAddonProperties(
                "mileage-for-clockify",
                "Mileage for Clockify",
                "https://mileage.example.test",
                "Convert native Clockify unit mileage expenses into flat reimbursement expenses.",
                true,
                "",
                "",
                null);
    }

    private static final class RejectingSignatureParser extends ClockifySignatureParser {
        private RejectingSignatureParser(String addonKey) {
            super(addonKey, publicKey());
        }

        @Override
        public Map<String, Object> parseClaims(String token) {
            throw new IllegalArgumentException("invalid signature");
        }

        private static RSAPublicKey publicKey() {
            try {
                return (RSAPublicKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
