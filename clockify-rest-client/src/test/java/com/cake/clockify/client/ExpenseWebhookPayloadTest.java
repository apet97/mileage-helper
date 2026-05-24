package com.cake.clockify.client;

import com.cake.clockify.client.models.ExpenseRefWebhookPayload;
import com.cake.clockify.client.models.ExpenseWebhookPayload;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseWebhookPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    @Test
    void deserializeExpenseCreatedPayload() throws Exception {
        String json = """
                {
                    "id": "68ae0cafcef78725aa10db15",
                    "workspaceId": "68adfddad138cb5f24c63b22",
                    "userId": "64621faec4d2cc53b91fce6c",
                    "date": "2025-08-26T00:00:00Z",
                    "projectId": "68ae0b03dc4864638480887f",
                    "taskId": null,
                    "categoryId": "68ae0c8189b9b14a1304e26e",
                    "notes": "",
                    "quantity": 22,
                    "billable": true,
                    "fileId": "",
                    "total": 220000
                }
                """;

        ExpenseWebhookPayload payload = objectMapper.readValue(json, ExpenseWebhookPayload.class);

        assertNotNull(payload);
        assertEquals("68ae0cafcef78725aa10db15", payload.id());
        assertEquals("68adfddad138cb5f24c63b22", payload.workspaceId());
        assertEquals("64621faec4d2cc53b91fce6c", payload.userId());
        assertEquals("2025-08-26T00:00:00Z", payload.date());
        assertEquals("68ae0b03dc4864638480887f", payload.projectId());
        assertNull(payload.taskId());
        assertEquals("68ae0c8189b9b14a1304e26e", payload.categoryId());
        assertEquals("", payload.notes());
        assertEquals(new BigDecimal("22"), payload.quantity());
        assertTrue(payload.billable());
        assertEquals("", payload.fileId());
        assertEquals(new BigDecimal("220000"), payload.total());
        assertNull(payload.locked());
    }

    @Test
    void deserializeExpenseCreatedReferencePayload() throws Exception {
        String json = """
                {
                    "workspaceId": "68adfddad138cb5f24c63b22",
                    "userId": "64621faec4d2cc53b91fce6c",
                    "expenseId": "68ae0cafcef78725aa10db15",
                    "categoryId": "68ae0c8189b9b14a1304e26e"
                }
                """;

        ExpenseWebhookPayload payload = objectMapper.readValue(json, ExpenseWebhookPayload.class);

        assertNotNull(payload);
        assertNull(payload.id());
        assertEquals("68ae0cafcef78725aa10db15", payload.expenseId());
    }

    @Test
    void deserializeExpenseRestoredPayload() throws Exception {
        String json = """
                {
                    "id": "6626722235baad1bce9e13c4",
                    "workspaceId": "65f31c3ca1390f6d7cf1d033",
                    "userId": "65f31c3ca1390f6d7cf1d032",
                    "date": "2024-04-22T00:00:00Z",
                    "projectId": "6606d1c0ad0bc15d89f41ae0",
                    "categoryId": "660298b663b23a11842833e8",
                    "notes": "",
                    "quantity": 1,
                    "billable": true,
                    "fileId": "",
                    "total": 500,
                    "locked": false
                }
                """;

        ExpenseWebhookPayload payload = objectMapper.readValue(json, ExpenseWebhookPayload.class);

        assertNotNull(payload);
        assertEquals("6626722235baad1bce9e13c4", payload.id());
        assertEquals("65f31c3ca1390f6d7cf1d033", payload.workspaceId());
        assertEquals("65f31c3ca1390f6d7cf1d032", payload.userId());
        assertEquals("2024-04-22T00:00:00Z", payload.date());
        assertEquals("6606d1c0ad0bc15d89f41ae0", payload.projectId());
        assertEquals("660298b663b23a11842833e8", payload.categoryId());
        assertEquals("", payload.notes());
        assertEquals(new BigDecimal("1"), payload.quantity());
        assertTrue(payload.billable());
        assertEquals("", payload.fileId());
        assertEquals(new BigDecimal("500"), payload.total());
        assertEquals(false, payload.locked());
    }

    @Test
    void deserializeExpenseDeletedPayload() throws Exception {
        String json = """
                {
                    "workspaceId": "6137bb5addd64b2759e031e8",
                    "userId": "61387478050bf21482aad3a8",
                    "projectId": "658422d9ac7a9530e4f049ea",
                    "expenseId": "658e6c507c6dd067d908c8f5",
                    "categoryId": "65842232ac7a9530e4f049df"
                }
                """;

        ExpenseRefWebhookPayload payload = objectMapper.readValue(json, ExpenseRefWebhookPayload.class);

        assertNotNull(payload);
        assertEquals("6137bb5addd64b2759e031e8", payload.workspaceId());
        assertEquals("61387478050bf21482aad3a8", payload.userId());
        assertEquals("658422d9ac7a9530e4f049ea", payload.projectId());
        assertEquals("658e6c507c6dd067d908c8f5", payload.expenseId());
        assertEquals("65842232ac7a9530e4f049df", payload.categoryId());
    }

    @Test
    void deserializeExpenseUpdatedPayload() throws Exception {
        String json = """
                {
                    "workspaceId": "6137bb5addd64b2759e031e8",
                    "userId": "61387478050bf21482aad3a8",
                    "projectId": "658422d9ac7a9530e4f049ea",
                    "expenseId": "658e6c507c6dd067d908c8f5",
                    "categoryId": "65842232ac7a9530e4f049df"
                }
                """;

        ExpenseRefWebhookPayload payload = objectMapper.readValue(json, ExpenseRefWebhookPayload.class);

        assertNotNull(payload);
        assertEquals("6137bb5addd64b2759e031e8", payload.workspaceId());
        assertEquals("61387478050bf21482aad3a8", payload.userId());
        assertEquals("658422d9ac7a9530e4f049ea", payload.projectId());
        assertEquals("658e6c507c6dd067d908c8f5", payload.expenseId());
        assertEquals("65842232ac7a9530e4f049df", payload.categoryId());
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        String json = """
                {
                    "id": "68ae0cafcef78725aa10db15",
                    "workspaceId": "68adfddad138cb5f24c63b22",
                    "userId": "64621faec4d2cc53b91fce6c",
                    "date": "2025-08-26T00:00:00Z",
                    "projectId": "68ae0b03dc4864638480887f",
                    "taskId": null,
                    "categoryId": "68ae0c8189b9b14a1304e26e",
                    "notes": "",
                    "quantity": 22,
                    "billable": true,
                    "fileId": "",
                    "total": 220000,
                    "unexpectedField": "should-be-ignored"
                }
                """;

        ExpenseWebhookPayload payload = objectMapper.readValue(json, ExpenseWebhookPayload.class);

        assertNotNull(payload);
        assertEquals("68ae0cafcef78725aa10db15", payload.id());
    }
}
