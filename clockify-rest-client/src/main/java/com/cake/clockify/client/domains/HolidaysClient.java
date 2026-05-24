package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class HolidaysClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public HolidaysClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getHolidays(String workspaceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        ClockifyRequest request = ClockifyRequest.builder("GET", path(workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode getHolidaysInPeriod(String workspaceId, String start, String end) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        ClockifyRequest.Builder builder = ClockifyRequest.builder("GET", path(workspaceId) + "/in-period")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND);
        if (start != null && !start.isBlank()) builder.query("start", start);
        if (end != null && !end.isBlank()) builder.query("end", end);
        return objectMapper.readTree(transport.send(builder.build()).body());
    }

    public JsonNode createHoliday(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", path(workspaceId), requestBody);
    }

    public JsonNode updateHoliday(String workspaceId, String holidayId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("holidayId", holidayId);
        return sendJson("PUT", path(workspaceId) + "/" + segment("holidayId", holidayId), requestBody);
    }

    public void deleteHoliday(String workspaceId, String holidayId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("holidayId", holidayId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", path(workspaceId) + "/" + segment("holidayId", holidayId))
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

    private static String path(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/holidays";
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
