package com.cake.clockify.client;

import com.cake.clockify.client.spring.ClockifyGlobalExceptionHandler;
import com.cake.clockify.client.spring.ClockifyRestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClockifyRestControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void controllerIsSpringRestControllerUnderClockifyPrefix() {
        assertNotNull(ClockifyRestController.class.getAnnotation(RestController.class));
        RequestMapping mapping = ClockifyRestController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertEquals("/clockify", mapping.value()[0]);
        ConditionalOnProperty conditional = ClockifyRestController.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(conditional);
        assertEquals("clockify.rest-controller", conditional.prefix());
        assertEquals("enabled", conditional.name()[0]);
        assertEquals("true", conditional.havingValue());
    }

    @Test
    void controllerDelegatesPagedClientReadsToTypedClient() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]"));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);
        ClockifyRestController controller = new ClockifyRestController(client);

        assertTrue(((com.fasterxml.jackson.databind.JsonNode) controller.getClients("w1", 3, 10)).isArray());

        ClockifyRequest request = transport.requests.get(0);
        assertEquals("GET", request.method());
        assertEquals("/v1/workspaces/w1/clients?page=3&page-size=10", request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
    }

    @Test
    void controllerDelegatesJsonBodyWritesAndDeleteResponses() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{\"id\":\"t1\"}", "{}"));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);
        ClockifyRestController controller = new ClockifyRestController(client);

        controller.createTag("w1", objectMapper.createObjectNode().put("name", "tag"));
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteTag("w1", "t1").getStatusCode());

        assertEquals("POST", transport.requests.get(0).method());
        assertEquals("/v1/workspaces/w1/tags", transport.requests.get(0).pathWithQuery());
        assertTrue(transport.requests.get(0).jsonBody().contains("tag"));
        assertEquals("DELETE", transport.requests.get(1).method());
        assertEquals("/v1/workspaces/w1/tags/t1", transport.requests.get(1).pathWithQuery());
    }

    @Test
    void controllerDelegatesPathVariablesAsEncodedPathSegments() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{\"id\":\"c1\"}"));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);
        ClockifyRestController controller = new ClockifyRestController(client);

        controller.getClient("workspace/one", "client/one");

        assertEquals("/v1/workspaces/workspace%2Fone/clients/client%2Fone", transport.requests.get(0).pathWithQuery());
    }

    @Test
    void controllerHasOneMappingForEveryClean1OpenApiOperation() {
        Set<String> mappings = new HashSet<>();
        for (Method method : ClockifyRestController.class.getDeclaredMethods()) {
            addMapping(mappings, "GET", method.getAnnotation(GetMapping.class));
            addMapping(mappings, "POST", method.getAnnotation(PostMapping.class));
            addMapping(mappings, "PUT", method.getAnnotation(PutMapping.class));
            addMapping(mappings, "PATCH", method.getAnnotation(PatchMapping.class));
            addMapping(mappings, "DELETE", method.getAnnotation(DeleteMapping.class));
        }
        mappings.removeIf(m -> m.contains("/addon/"));
        assertEquals(193, mappings.size(), "supported generated facade operations");
    }

    @Test
    void controllerDoesNotExposeUnsupportedAuditLogRoute() {
        Set<String> mappings = new HashSet<>();
        for (Method method : ClockifyRestController.class.getDeclaredMethods()) {
            addMapping(mappings, "GET", method.getAnnotation(GetMapping.class));
            addMapping(mappings, "POST", method.getAnnotation(PostMapping.class));
            addMapping(mappings, "PUT", method.getAnnotation(PutMapping.class));
            addMapping(mappings, "PATCH", method.getAnnotation(PatchMapping.class));
            addMapping(mappings, "DELETE", method.getAnnotation(DeleteMapping.class));
        }

        assertTrue(mappings.stream().noneMatch(mapping -> mapping.contains("audit-log")),
                "Audit logs are absent from the allowed official OpenAPI/prose sources and must not be scaffolded as supported");
    }

    @Test
    void controllerExposesOfficialWebhookTokenRouteOnly() {
        Set<String> mappings = new HashSet<>();
        for (Method method : ClockifyRestController.class.getDeclaredMethods()) {
            addMapping(mappings, "GET", method.getAnnotation(GetMapping.class));
            addMapping(mappings, "POST", method.getAnnotation(PostMapping.class));
            addMapping(mappings, "PUT", method.getAnnotation(PutMapping.class));
            addMapping(mappings, "PATCH", method.getAnnotation(PatchMapping.class));
            addMapping(mappings, "DELETE", method.getAnnotation(DeleteMapping.class));
        }

        assertTrue(mappings.contains("PATCH /workspaces/{workspaceId}/webhooks/{webhookId}/token"));
        assertTrue(mappings.stream().noneMatch(mapping -> mapping.contains("/generateNewToken")),
                "Official OpenAPI documents PATCH /token, not /generateNewToken");
    }

    @Test
    void generatedOpenApiFacadeRoutesReportsAndBinaryFamilies() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{\"ok\":true}"));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);
        ClockifyRestController controller = new ClockifyRestController(client);

        controller.generateAttendanceReport("w1", objectMapper.createObjectNode().put("dateRangeStart", "2026-01-01"));
        assertEquals("POST", transport.requests.get(0).method());
        assertEquals("/v1/workspaces/w1/reports/attendance", transport.requests.get(0).pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.REPORTS, transport.requests.get(0).baseUrlFamily());

        assertEquals(HttpStatus.OK, controller.downloadExpenseReceipt("w1", "e1", "f1").getStatusCode());
        assertEquals("GET", transport.binaryRequests.get(0).method());
        assertEquals("/v1/workspaces/w1/expenses/e1/files/f1", transport.binaryRequests.get(0).pathWithQuery());
        assertTrue(transport.binaryRequests.get(0).binaryResponse());
    }

    @Test
    void sharedReportsUseReportsBaseUrlAndReturnJson() throws Exception {
        RecordingTransport transport = new RecordingTransport(
                List.of("{\"id\":\"r1\"}", "[]", "{\"id\":\"r2\"}", "{}", "{}"));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);
        ClockifyRestController controller = new ClockifyRestController(client);

        controller.getSharedReport("r1");
        controller.getWorkspaceSharedReports("w1");
        controller.createSharedReport("w1", objectMapper.createObjectNode());
        controller.updateSharedReport("w1", "r1", objectMapper.createObjectNode());
        controller.deleteSharedReport("w1", "r1");

        for (int i = 0; i < 5; i++) {
            assertEquals(ClockifyBaseUrlFamily.REPORTS, transport.requests.get(i).baseUrlFamily(),
                    "shared-reports request " + i + " must use REPORTS base URL");
        }
    }

    @Test
    void invoiceExportIsBinaryAndExpenseReceiptIsBinary() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of());
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);
        ClockifyRestController controller = new ClockifyRestController(client);

        controller.exportInvoice("w1", "inv1");
        controller.downloadExpenseReceipt("w1", "exp1", "f1");

        assertEquals(2, transport.binaryRequests.size(), "both invoice export and expense receipt must be binary");
        assertEquals("/v1/workspaces/w1/invoices/inv1/export?userLocale=en-US", transport.binaryRequests.get(0).pathWithQuery());
        assertEquals("/v1/workspaces/w1/expenses/exp1/files/f1", transport.binaryRequests.get(1).pathWithQuery());
        assertTrue(transport.binaryRequests.get(0).binaryResponse());
        assertTrue(transport.binaryRequests.get(1).binaryResponse());
    }

    @Test
    void globalExceptionHandlerMapsClockifyApiException() {
        var handler = new ClockifyGlobalExceptionHandler();

        var apiEx = new ClockifyApiException("Something bad", "POST", "/v1/test", 400, Map.of(), "{\"error\":\"invalid\",\"message\":\"bad input\"}");
        var response = handler.handleClockifyApiException(apiEx);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid", response.getBody().get("error").asText());

        var badBodyEx = new ClockifyApiException("Fail", "GET", "/v1/test", 503, Map.of(), "Non-json text");
        var response2 = handler.handleClockifyApiException(badBodyEx);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response2.getStatusCode());
        assertEquals("clockify_api_error", response2.getBody().get("error").asText());
    }

    private static void addMapping(Set<String> mappings, String httpMethod, GetMapping mapping) {
        if (mapping != null) {
            String consumes = mapping.consumes().length > 0 ? " consumes=" + String.join(",", mapping.consumes()) : "";
            assertTrue(mappings.add(httpMethod + " " + mapping.value()[0] + consumes));
        }
    }

    private static void addMapping(Set<String> mappings, String httpMethod, PostMapping mapping) {
        if (mapping != null) {
            String consumes = mapping.consumes().length > 0 ? " consumes=" + String.join(",", mapping.consumes()) : "";
            assertTrue(mappings.add(httpMethod + " " + mapping.value()[0] + consumes));
        }
    }

    private static void addMapping(Set<String> mappings, String httpMethod, PutMapping mapping) {
        if (mapping != null) {
            String consumes = mapping.consumes().length > 0 ? " consumes=" + String.join(",", mapping.consumes()) : "";
            assertTrue(mappings.add(httpMethod + " " + mapping.value()[0] + consumes));
        }
    }

    private static void addMapping(Set<String> mappings, String httpMethod, PatchMapping mapping) {
        if (mapping != null) {
            String consumes = mapping.consumes().length > 0 ? " consumes=" + String.join(",", mapping.consumes()) : "";
            assertTrue(mappings.add(httpMethod + " " + mapping.value()[0] + consumes));
        }
    }

    private static void addMapping(Set<String> mappings, String httpMethod, DeleteMapping mapping) {
        if (mapping != null) {
            String consumes = mapping.consumes().length > 0 ? " consumes=" + String.join(",", mapping.consumes()) : "";
            assertTrue(mappings.add(httpMethod + " " + mapping.value()[0] + consumes));
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
            return new ClockifyBinaryResponse(200, Map.of("content-type", List.of("application/octet-stream")), new byte[]{1, 2, 3});
        }
    }
}
