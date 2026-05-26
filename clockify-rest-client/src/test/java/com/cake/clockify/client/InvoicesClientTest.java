package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InvoicesClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void invoiceMethodsUseDocumentedBackendPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of(
                "[]", "{}", "{}", "{}", "{}", "{}", "{}", "{}", "{}", "{}"
        ));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.invoices().getInvoices("w1", new ClockifyPageRequest(2, 25));
        client.invoices().createInvoice("w1", body());
        client.invoices().filterInvoices("w1", body());
        client.invoices().getInvoiceSettings("w1");
        client.invoices().updateInvoiceSettings("w1", body());
        client.invoices().getInvoice("w1", "i1");
        client.invoices().updateInvoice("w1", "i1", body());
        client.invoices().deleteInvoice("w1", "i1");
        client.invoices().duplicateInvoice("w1", "i1", body());
        client.invoices().changeInvoiceStatus("w1", "i1", body());

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/invoices?page=2&page-size=25", null);
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/invoices", "marker");
        assertRequest(transport.requests.get(2), "POST", "/v1/workspaces/w1/invoices/info", "marker");
        assertRequest(transport.requests.get(3), "GET", "/v1/workspaces/w1/invoices/settings", null);
        assertRequest(transport.requests.get(4), "PUT", "/v1/workspaces/w1/invoices/settings", "marker");
        assertRequest(transport.requests.get(5), "GET", "/v1/workspaces/w1/invoices/i1", null);
        assertRequest(transport.requests.get(6), "PUT", "/v1/workspaces/w1/invoices/i1", "marker");
        assertRequest(transport.requests.get(7), "DELETE", "/v1/workspaces/w1/invoices/i1", null);
        assertRequest(transport.requests.get(8), "POST", "/v1/workspaces/w1/invoices/i1/duplicate", "marker");
        assertRequest(transport.requests.get(9), "PATCH", "/v1/workspaces/w1/invoices/i1/status", "marker");
    }

    @Test
    void invoiceItemAndPaymentMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}", "{}", "{}", "[]", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.invoices().addInvoiceItem("w1", "i1", body());
        client.invoices().importInvoiceItems("w1", "i1", body());
        client.invoices().deleteInvoiceItem("w1", "i1", 3);
        client.invoices().getInvoicePayments("w1", "i1", new ClockifyPageRequest(1, 10));
        client.invoices().createInvoicePayment("w1", "i1", body());
        client.invoices().deleteInvoicePayment("w1", "i1", "p1");

        assertRequest(transport.requests.get(0), "POST", "/v1/workspaces/w1/invoices/i1/items", "marker");
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/invoices/i1/items/import", "marker");
        assertRequest(transport.requests.get(2), "DELETE", "/v1/workspaces/w1/invoices/i1/items/3", null);
        assertRequest(transport.requests.get(3), "GET", "/v1/workspaces/w1/invoices/i1/payments?page=1&page-size=10", null);
        assertRequest(transport.requests.get(4), "POST", "/v1/workspaces/w1/invoices/i1/payments", "marker");
        assertRequest(transport.requests.get(5), "DELETE", "/v1/workspaces/w1/invoices/i1/payments/p1", null);
    }

    @Test
    void invoiceExportUsesBinaryHandling() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of());
        ClockifyClient client = TestClockifyClient.client(transport);

        ClockifyBinaryResponse response = client.invoices().exportInvoice("w1", "i1");

        assertArrayEquals(new byte[]{1, 2, 3}, response.bytes());
        assertEquals("GET", transport.binaryRequests.get(0).method());
        assertEquals("/v1/workspaces/w1/invoices/i1/export?userLocale=en-US", transport.binaryRequests.get(0).pathWithQuery());
        assertTrue(transport.binaryRequests.get(0).binaryResponse());
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = TestClockifyClient.client(new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.invoices().getInvoices(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.invoices().getInvoices("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.invoices().getInvoice("w1", " "));
        assertThrows(NullPointerException.class, () -> client.invoices().createInvoice("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.invoices().deleteInvoiceItem("w1", "i1", -1));
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
        } else {
            assertNotNull(request.jsonBody());
            assertTrue(request.jsonBody().contains(bodyContains));
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
            return new ClockifyBinaryResponse(200, Map.of("content-type", List.of("application/pdf")), new byte[]{1, 2, 3});
        }
    }
}
