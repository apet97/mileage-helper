package com.cake.clockify.addon.core.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDedupeKeyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void includesEventTypeEntityIdAndPayloadHashSoDifferentEventsForSameEntityDoNotCollide() {
        byte[] payload = "{\"id\":\"entity-1\",\"name\":\"A\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String created = WebhookDedupeKey.from("CLIENT_CREATED", payload, objectMapper).orElseThrow();
        String deleted = WebhookDedupeKey.from("CLIENT_DELETED", payload, objectMapper).orElseThrow();

        assertThat(created).startsWith("CLIENT_CREATED:id:entity-1:");
        assertThat(deleted).startsWith("CLIENT_DELETED:id:entity-1:");
        assertThat(created).isNotEqualTo(deleted);
    }

    @Test
    void includesPayloadHashSoChangedUpdatesForSameEntityCanBothProcess() {
        byte[] firstPayload = "{\"id\":\"entity-1\",\"name\":\"A\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] secondPayload = "{\"id\":\"entity-1\",\"name\":\"B\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String first = WebhookDedupeKey.from("CLIENT_UPDATED", firstPayload, objectMapper).orElseThrow();
        String second = WebhookDedupeKey.from("CLIENT_UPDATED", secondPayload, objectMapper).orElseThrow();

        assertThat(first).startsWith("CLIENT_UPDATED:id:entity-1:");
        assertThat(second).startsWith("CLIENT_UPDATED:id:entity-1:");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void usesEntitySpecificIdFieldWhenNoRootIdIsPresent() {
        byte[] payload = "{\"assignmentId\":\"assignment-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String key = WebhookDedupeKey.from("USER_ASSIGNED", payload, objectMapper).orElseThrow();

        assertThat(key).startsWith("USER_ASSIGNED:assignmentId:assignment-1:");
    }

    @Test
    void usesSha256PayloadHash() {
        byte[] payload = "{\"id\":\"entity-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String hash = WebhookDedupeKey.payloadHash(payload);

        assertThat(hash)
                .hasSize(64)
                .isEqualTo("26006193df079efd2fa394382714a18e295f1e216fca4dec0b701878c926e066");
    }

    @Test
    void capsReadableComponentSoDedupeKeyFitsDatabaseColumn() {
        byte[] payload = ("{\"eventId\":\"" + "x".repeat(240) + "\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String longEventType = "A".repeat(100);

        String key = WebhookDedupeKey.from(longEventType, payload, objectMapper).orElseThrow();

        assertThat(key).hasSizeLessThanOrEqualTo(255);
    }

    @Test
    void usesExpenseIdFieldForExpenseDeletedAndUpdatedEvents() {
        byte[] payload = "{\"workspaceId\":\"ws-1\",\"userId\":\"user-1\",\"projectId\":\"proj-1\",\"expenseId\":\"expense-123\",\"categoryId\":\"cat-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String key = WebhookDedupeKey.from("EXPENSE_DELETED", payload, objectMapper).orElseThrow();

        assertThat(key).startsWith("EXPENSE_DELETED:expenseId:expense-123:");
    }
}
