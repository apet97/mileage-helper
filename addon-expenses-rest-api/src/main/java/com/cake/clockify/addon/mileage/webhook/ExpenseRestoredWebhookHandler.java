package com.cake.clockify.addon.mileage.webhook;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.webhook.AbstractTypedWebhookHandler;
import com.cake.clockify.addon.core.webhook.WebhookEvent;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.conversion.MileageConversionService;
import com.cake.clockify.client.models.ExpenseWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
@WebhookEvent("EXPENSE_RESTORED")
public class ExpenseRestoredWebhookHandler extends AbstractTypedWebhookHandler<ExpenseWebhookPayload> {
    private final MileageConversionService conversionService;

    public ExpenseRestoredWebhookHandler(ObjectMapper objectMapper, MileageConversionService conversionService) {
        super(objectMapper, ExpenseWebhookPayload.class);
        this.conversionService = conversionService;
    }

    @Override
    protected void handleTyped(NormalizedClaims claims, String eventType, ExpenseWebhookPayload payload) {
        if (payload == null || payload.id() == null || payload.id().isBlank()) {
            return;
        }
        conversionService.convertIfEligible(claims, payload.id(), MileageConversionSource.WEBHOOK_RESTORED, eventType);
    }
}
