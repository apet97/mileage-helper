package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExpensesClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void expenseMethodsUseDocumentedBackendPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]", "{}", "{}", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.expenses().getExpenses("w1", new ClockifyPageRequest(2, 25));
        client.expenses().createExpense("w1", body());
        client.expenses().getExpense("w1", "e1");
        client.expenses().updateExpense("w1", "e1", body());
        client.expenses().deleteExpense("w1", "e1");

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/expenses?page=2&page-size=25", null);
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/expenses", "marker");
        assertRequest(transport.requests.get(2), "GET", "/v1/workspaces/w1/expenses/e1", null);
        assertRequest(transport.requests.get(3), "PUT", "/v1/workspaces/w1/expenses/e1", "marker");
        assertRequest(transport.requests.get(4), "DELETE", "/v1/workspaces/w1/expenses/e1", null);
    }

    @Test
    void categoryMethodsUseDocumentedBackendPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]", "{}", "{}", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.expenses().getCategories("w1", new ClockifyPageRequest(1, 10));
        client.expenses().createCategory("w1", body());
        client.expenses().updateCategory("w1", "c1", body());
        client.expenses().deleteCategory("w1", "c1");
        client.expenses().updateCategoryStatus("w1", "c1", body());

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/expenses/categories?page=1&page-size=10", null);
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/expenses/categories", "marker");
        assertRequest(transport.requests.get(2), "PUT", "/v1/workspaces/w1/expenses/categories/c1", "marker");
        assertRequest(transport.requests.get(3), "DELETE", "/v1/workspaces/w1/expenses/categories/c1", null);
        assertRequest(transport.requests.get(4), "PATCH", "/v1/workspaces/w1/expenses/categories/c1/status", "marker");
    }

    @Test
    void downloadFileUsesBinaryHandling() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of());
        ClockifyClient client = TestClockifyClient.client(transport);

        ClockifyBinaryResponse response = client.expenses().downloadFile("w1", "e1", "f1");

        assertArrayEquals(new byte[]{4, 5, 6}, response.bytes());
        assertEquals("GET", transport.binaryRequests.get(0).method());
        assertEquals("/v1/workspaces/w1/expenses/e1/files/f1", transport.binaryRequests.get(0).pathWithQuery());
        assertTrue(transport.binaryRequests.get(0).binaryResponse());
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = TestClockifyClient.client(new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.expenses().getExpenses(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.expenses().getExpenses("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.expenses().getExpense("w1", " "));
        assertThrows(NullPointerException.class, () -> client.expenses().createExpense("w1", null));
    }

    @Test
    void getExpensesWithUserIdFilter() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.expenses().getExpenses("w1", "user-123", new ClockifyPageRequest(1, 50));

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/expenses?page=1&page-size=50&user-id=user-123", null);
    }

    @Test
    void getCategoriesWithFilteringAndSorting() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.expenses().getCategories("w1", "NAME", "DESCENDING", true, "travel", new ClockifyPageRequest(1, 10));

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/expenses/categories?page=1&page-size=10&sort-column=NAME&sort-order=DESCENDING&archived=true&name=travel", null);
    }

    @Test
    void createExpenseWithFile() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.expenses().createExpense("w1", body(), "receipt.pdf", "application/pdf", new byte[]{1, 2, 3});

        ClockifyRequest request = transport.requests.get(0);
        assertEquals("POST", request.method());
        assertNotNull(request.bytesBody());
        String bodyStr = new String(request.bytesBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("filename=\"receipt.pdf\""));
        assertTrue(bodyStr.contains("Content-Type: application/pdf"));
        assertTrue(bodyStr.contains("marker"));
    }

    @Test
    void createExpenseWithFileSanitizesMultipartHeaders() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.expenses().createExpense(
                "w1",
                body(),
                "../evil\r\nContent-Disposition: form-data; name=\"pwned\".pdf",
                "application/pdf\r\nX-Injected: yes",
                new byte[]{1, 2, 3});

        String bodyStr = new String(transport.requests.get(0).bytesBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("filename=\"evil__Content-Disposition_ form-data_ name=_pwned_.pdf\""));
        assertTrue(bodyStr.contains("Content-Type: application/octet-stream"));
        assertFalse(bodyStr.contains("name=\"pwned\""));
        assertFalse(bodyStr.contains("X-Injected: yes"));
    }

    @Test
    void updateExpenseWithFileAppendsFileToChangeFields() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.expenses().updateExpense("w1", "e1", body(), "receipt.pdf", "application/pdf", new byte[]{1, 2, 3});

        ClockifyRequest request = transport.requests.get(0);
        assertEquals("PUT", request.method());
        assertNotNull(request.bytesBody());
        String bodyStr = new String(request.bytesBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("filename=\"receipt.pdf\""));
        assertTrue(bodyStr.contains("changeFields"));
        assertTrue(bodyStr.contains("FILE"));
    }

    @Test
    void updateExpenseWithChangeFieldsJsonArray() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        com.fasterxml.jackson.databind.node.ObjectNode body = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode array = objectMapper.createArrayNode().add("USER").add("amount");
        body.set("changeFields", array);

        client.expenses().updateExpense("w1", "e1", body);

        ClockifyRequest request = transport.requests.get(0);
        assertNotNull(request.bytesBody());
        String bodyStr = new String(request.bytesBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("changeFields"));
        assertTrue(bodyStr.contains("USER,AMOUNT"));
    }

    @Test
    void updateCategoryStatusActiveMapsToArchivedFalse() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        com.fasterxml.jackson.databind.node.ObjectNode body = objectMapper.createObjectNode().put("status", "ACTIVE");
        client.expenses().updateCategoryStatus("w1", "c1", body);

        assertRequest(transport.requests.get(0), "PATCH", "/v1/workspaces/w1/expenses/categories/c1/status", "archived\":false");
    }

    @Test
    void updateExpenseGeneratesChangeFields() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        com.fasterxml.jackson.databind.node.ObjectNode body = objectMapper.createObjectNode()
                .put("notes", "New Notes")
                .put("amount", 1234)
                .put("categoryId", "cat1")
                .put("date", "2026-05-24")
                .put("userId", "user1")
                .put("projectId", "proj1")
                .put("taskId", "task1")
                .put("billable", true);

        client.expenses().updateExpense("w1", "e1", body);

        ClockifyRequest req = transport.requests.get(0);
        assertNotNull(req.bytesBody());
        String bodyStr = new String(req.bytesBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("changeFields"));
        assertTrue(bodyStr.contains("NOTES"));
        assertTrue(bodyStr.contains("AMOUNT"));
        assertTrue(bodyStr.contains("CATEGORY"));
        assertTrue(bodyStr.contains("DATE"));
        assertTrue(bodyStr.contains("USER"));
        assertTrue(bodyStr.contains("PROJECT"));
        assertTrue(bodyStr.contains("TASK"));
        assertTrue(bodyStr.contains("BILLABLE"));
    }

    @Test
    void updateExpensePreservesExplicitChangeFields() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        com.fasterxml.jackson.databind.node.ObjectNode body = objectMapper.createObjectNode()
                .put("notes", "New Notes")
                .put("changeFields", "NOTES");

        client.expenses().updateExpense("w1", "e1", body);

        ClockifyRequest req = transport.requests.get(0);
        String bodyStr = new String(req.bytesBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("changeFields"));
        assertTrue(bodyStr.contains("NOTES"));
        assertFalse(bodyStr.contains("AMOUNT"));
    }

    private com.fasterxml.jackson.databind.JsonNode body() {
        return objectMapper.createObjectNode().put("marker", true);
    }

    private static void assertRequest(ClockifyRequest request, String method, String pathWithQuery, String bodyContains) {
        assertEquals(method, request.method());
        assertEquals(pathWithQuery, request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
        if (bodyContains == null) {
            assertNull(request.jsonBody());
            assertNull(request.bytesBody());
        } else {
            if (request.contentType() != null && request.contentType().startsWith("multipart/form-data")) {
                assertNotNull(request.bytesBody());
                String bodyStr = new String(request.bytesBody(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(bodyStr.contains(bodyContains), "Expected body to contain: " + bodyContains + ", but was: " + bodyStr);
            } else {
                assertNotNull(request.jsonBody());
                assertTrue(request.jsonBody().contains(bodyContains));
            }
        }
    }

    static final class RecordingTransport implements ClockifyTransport {
        final List<String> bodies;
        final List<ClockifyRequest> requests = new ArrayList<>();
        final List<ClockifyRequest> binaryRequests = new ArrayList<>();
        int index;

        RecordingTransport(List<String> bodies) {
            this.bodies = bodies;
        }

        @Override
        public ClockifyResponse<String> send(ClockifyRequest request) {
            requests.add(request);
            return new ClockifyResponse<>(200, Map.of(), bodies.get(index++));
        }

        @Override
        public ClockifyBinaryResponse sendBinary(ClockifyRequest request) {
            binaryRequests.add(request);
            return new ClockifyBinaryResponse(200, Map.of("content-type", List.of("application/octet-stream")), new byte[]{4, 5, 6});
        }
    }
}
