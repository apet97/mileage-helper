package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyBinaryResponse;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class InvoicesClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public InvoicesClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getInvoices(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/invoices")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return readJson(request);
    }

    public JsonNode createInvoice(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/invoices", requestBody);
    }

    public JsonNode filterInvoices(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/invoices/info", requestBody);
    }

    public JsonNode getInvoiceSettings(String workspaceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return readJson(ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/invoices/settings")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode updateInvoiceSettings(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/invoices/settings", requestBody);
    }

    public JsonNode getInvoice(String workspaceId, String invoiceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        return readJson(ClockifyRequest.builder("GET", invoicePath(workspaceId, invoiceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode updateInvoice(String workspaceId, String invoiceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        return sendJson("PUT", invoicePath(workspaceId, invoiceId), requestBody);
    }

    public void deleteInvoice(String workspaceId, String invoiceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        transport.send(ClockifyRequest.builder("DELETE", invoicePath(workspaceId, invoiceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode duplicateInvoice(String workspaceId, String invoiceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        return sendJson("POST", invoicePath(workspaceId, invoiceId) + "/duplicate", requestBody);
    }

    public ClockifyBinaryResponse exportInvoice(String workspaceId, String invoiceId) throws IOException, InterruptedException {
        return exportInvoice(workspaceId, invoiceId, "en-US");
    }

    public ClockifyBinaryResponse exportInvoice(String workspaceId, String invoiceId, String userLocale) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        requireId("userLocale", userLocale);
        ClockifyRequest request = ClockifyRequest.builder("GET", invoicePath(workspaceId, invoiceId) + "/export")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("userLocale", userLocale)
                .binaryResponse(true)
                .build();
        return transport.sendBinary(request);
    }

    public JsonNode addInvoiceItem(String workspaceId, String invoiceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        return sendJson("POST", invoicePath(workspaceId, invoiceId) + "/items", requestBody);
    }

    public JsonNode importInvoiceItems(String workspaceId, String invoiceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        return sendJson("POST", invoicePath(workspaceId, invoiceId) + "/items/import", requestBody);
    }

    public void deleteInvoiceItem(String workspaceId, String invoiceId, int order) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        if (order < 0) throw new IllegalArgumentException("order must be >= 0");
        transport.send(ClockifyRequest.builder("DELETE", invoicePath(workspaceId, invoiceId) + "/items/" + order)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode getInvoicePayments(String workspaceId, String invoiceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", invoicePath(workspaceId, invoiceId) + "/payments")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return readJson(request);
    }

    public JsonNode createInvoicePayment(String workspaceId, String invoiceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        return sendJson("POST", invoicePath(workspaceId, invoiceId) + "/payments", requestBody);
    }

    public void deleteInvoicePayment(String workspaceId, String invoiceId, String paymentId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        requireId("paymentId", paymentId);
        transport.send(ClockifyRequest.builder("DELETE", invoicePath(workspaceId, invoiceId) + "/payments/" + segment("paymentId", paymentId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode changeInvoiceStatus(String workspaceId, String invoiceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("invoiceId", invoiceId);
        return sendJson("PATCH", invoicePath(workspaceId, invoiceId) + "/status", requestBody);
    }

    private JsonNode sendJson(String method, String path, JsonNode requestBody) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return readJson(request);
    }

    private JsonNode readJson(ClockifyRequest request) throws IOException, InterruptedException {
        return objectMapper.readTree(transport.send(request).body());
    }

    private static String invoicePath(String workspaceId, String invoiceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/invoices/" + segment("invoiceId", invoiceId);
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
