package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class WebhooksClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public WebhooksClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getAddonWebhooks(String workspaceId, String addonId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("addonId", addonId);
        return readJson(ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/addons/" + segment("addonId", addonId) + "/webhooks")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode getWebhooks(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", webhooksPath(workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return readJson(request);
    }

    public JsonNode createWebhook(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", webhooksPath(workspaceId), requestBody);
    }

    public JsonNode getWebhook(String workspaceId, String webhookId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("webhookId", webhookId);
        return readJson(ClockifyRequest.builder("GET", webhookPath(workspaceId, webhookId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode updateWebhook(String workspaceId, String webhookId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("webhookId", webhookId);
        return sendJson("PUT", webhookPath(workspaceId, webhookId), requestBody);
    }

    public void deleteWebhook(String workspaceId, String webhookId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("webhookId", webhookId);
        transport.send(ClockifyRequest.builder("DELETE", webhookPath(workspaceId, webhookId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode generateNewToken(String workspaceId, String webhookId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("webhookId", webhookId);
        return sendJson("PATCH", webhookPath(workspaceId, webhookId) + "/token", requestBody);
    }

    public JsonNode getWebhookLogs(String workspaceId, String webhookId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("webhookId", webhookId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", webhookPath(workspaceId, webhookId) + "/logs")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return readJson(request);
    }

    public JsonNode filterWebhookLogs(String workspaceId, String webhookId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("webhookId", webhookId);
        return sendJson("POST", webhookPath(workspaceId, webhookId) + "/logs", requestBody);
    }

    /**
     * @deprecated Use {@link #generateNewToken(String, String, JsonNode)} to match Clockify's documented operation.
     */
    @Deprecated(forRemoval = false)
    public JsonNode updateWebhookToken(String workspaceId, String webhookId, JsonNode requestBody) throws IOException, InterruptedException {
        return generateNewToken(workspaceId, webhookId, requestBody);
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

    private static String webhooksPath(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/webhooks";
    }

    private static String webhookPath(String workspaceId, String webhookId) {
        return webhooksPath(workspaceId) + "/" + segment("webhookId", webhookId);
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
