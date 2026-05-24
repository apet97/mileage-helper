package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.ClockifyPath;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

public final class ClientsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public ClientsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getClients(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/clients")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode createClient(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/clients", requestBody);
    }

    public JsonNode getClient(String workspaceId, String clientId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("clientId", clientId);
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/clients/" + segment("clientId", clientId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode updateClient(String workspaceId, String clientId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("clientId", clientId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/clients/" + segment("clientId", clientId), requestBody);
    }

    public void deleteClient(String workspaceId, String clientId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("clientId", clientId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/clients/" + segment("clientId", clientId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        transport.send(request);
    }

    public JsonNode archiveClient(String workspaceId, String clientId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("clientId", clientId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/clients/" + segment("clientId", clientId), requestBody);
    }

    private JsonNode sendJson(String method, String path, JsonNode requestBody) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static String segment(String name, String value) {
        return ClockifyPath.segment(name, value);
    }
}
