package com.cake.clockify.addon.mileage.webhook;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.webhook.AbstractTypedWebhookHandler;
import com.cake.clockify.addon.core.webhook.WebhookEvent;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.conversion.MileageConversionService;
import com.cake.clockify.client.models.ExpenseRefWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
@WebhookEvent("EXPENSE_UPDATED")
public class ExpenseUpdatedWebhookHandler extends AbstractTypedWebhookHandler<ExpenseRefWebhookPayload> {
    private final MileageConversionService conversionService;

    public ExpenseUpdatedWebhookHandler(ObjectMapper objectMapper, MileageConversionService conversionService) {
        super(objectMapper, ExpenseRefWebhookPayload.class);
        this.conversionService = conversionService;
    }

    @Override
    protected void handleTyped(NormalizedClaims claims, String eventType, ExpenseRefWebhookPayload payload) {
        String expenseId = payload == null ? null : payload.effectiveExpenseId();
        if (expenseId == null || expenseId.isBlank()) {
            return;
        }
        conversionService.convertIfEligible(claims, expenseId, MileageConversionSource.WEBHOOK_UPDATED, eventType);
    }
}
