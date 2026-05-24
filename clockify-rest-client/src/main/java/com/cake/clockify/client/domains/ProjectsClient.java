package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyPage;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyResponse;
import com.cake.clockify.client.ClockifyTransport;
import com.cake.clockify.client.models.Project;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class ProjectsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public ProjectsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getProjects(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/projects")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public List<Project> listAll(String workspaceId) throws IOException, InterruptedException {
        return listAll(workspaceId, 1000, 50);
    }

    public List<Project> listAll(String workspaceId, int maxRows, int pageSize) throws IOException, InterruptedException {
        if (maxRows < 1) throw new IllegalArgumentException("maxRows must be >= 1");
        List<Project> result = new ArrayList<>();
        forEachPage(workspaceId, pageSize, page -> {
            for (Project item : page.items()) {
                if (result.size() < maxRows) result.add(item);
            }
        }, maxRows);
        return List.copyOf(result);
    }

    public void forEachPage(String workspaceId, int pageSize, Consumer<ClockifyPage<Project>> consumer) throws IOException, InterruptedException {
        forEachPage(workspaceId, pageSize, consumer, 1000);
    }

    public void forEachPage(String workspaceId, int pageSize, Consumer<ClockifyPage<Project>> consumer, int maxRows) throws IOException, InterruptedException {
        Objects.requireNonNull(consumer, "consumer");
        int page = 1;
        int seen = 0;
        while (seen < maxRows) {
            ClockifyPageRequest request = new ClockifyPageRequest(page, Math.min(pageSize, maxRows - seen));
            ClockifyPage<Project> result = getProjectPage(workspaceId, request);
            consumer.accept(result);
            seen += result.items().size();
            if (result.lastPage() || result.items().isEmpty()) return;
            page++;
        }
    }

    public ClockifyPage<Project> getProjectPage(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/projects")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        ClockifyResponse<String> response = transport.send(request);
        List<Project> items = objectMapper.readValue(response.body(), new TypeReference<>() {});
        return new ClockifyPage<>(items, pageRequest, ClockifyPage.parseLastPageHeader(response.firstHeader("Last-Page")));
    }

    public JsonNode createProject(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/projects", requestBody);
    }

    public JsonNode getProject(String workspaceId, String projectId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        ClockifyRequest request = ClockifyRequest.builder("GET", projectPath(workspaceId, projectId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode updateProject(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("PUT", projectPath(workspaceId, projectId), requestBody);
    }

    public void deleteProject(String workspaceId, String projectId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", projectPath(workspaceId, projectId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        transport.send(request);
    }

    public JsonNode createProjectFromTemplate(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/projects/from-template", requestBody);
    }

    public JsonNode archiveProject(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("PUT", projectPath(workspaceId, projectId), requestBody);
    }

    public JsonNode updateProjectCostRate(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("PUT", projectPath(workspaceId, projectId) + "/cost-rate", requestBody);
    }

    public JsonNode updateProjectEstimate(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("PATCH", projectPath(workspaceId, projectId) + "/estimate", requestBody);
    }

    public JsonNode updateProjectBillableRate(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("PUT", projectPath(workspaceId, projectId) + "/hourly-rate", requestBody);
    }

    public JsonNode assignProjectUsers(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("POST", projectPath(workspaceId, projectId) + "/memberships", requestBody);
    }

    public JsonNode updateProjectMemberships(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("PATCH", projectPath(workspaceId, projectId) + "/memberships", requestBody);
    }

    public JsonNode updateTaskCostRate(String workspaceId, String projectId, String taskId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("taskId", taskId);
        return sendJson("PUT", taskPath(workspaceId, projectId, taskId) + "/cost-rate", requestBody);
    }

    public JsonNode updateTaskBillableRate(String workspaceId, String projectId, String taskId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("taskId", taskId);
        return sendJson("PUT", taskPath(workspaceId, projectId, taskId) + "/hourly-rate", requestBody);
    }

    public JsonNode updateProjectTemplate(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("PATCH", projectPath(workspaceId, projectId) + "/template", requestBody);
    }

    public JsonNode updateProjectUserCostRate(String workspaceId, String projectId, String userId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("userId", userId);
        return sendJson("PUT", projectPath(workspaceId, projectId) + "/users/" + segment("userId", userId) + "/cost-rate", requestBody);
    }

    public JsonNode updateProjectUserHourlyRate(String workspaceId, String projectId, String userId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("userId", userId);
        return sendJson("PUT", projectPath(workspaceId, projectId) + "/users/" + segment("userId", userId) + "/hourly-rate", requestBody);
    }

    private static String projectPath(String workspaceId, String projectId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/projects/" + segment("projectId", projectId);
    }

    private static String taskPath(String workspaceId, String projectId, String taskId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/projects/" + segment("projectId", projectId) + "/tasks/" + segment("taskId", taskId);
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
