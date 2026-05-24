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

public final class TagsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public TagsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getTags(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/tags")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode createTag(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/tags", requestBody);
    }

    public JsonNode getTag(String workspaceId, String tagId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("tagId", tagId);
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/tags/" + segment("tagId", tagId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode updateTag(String workspaceId, String tagId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("tagId", tagId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/tags/" + segment("tagId", tagId), requestBody);
    }

    public void deleteTag(String workspaceId, String tagId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("tagId", tagId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/tags/" + segment("tagId", tagId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        transport.send(request);
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
}
