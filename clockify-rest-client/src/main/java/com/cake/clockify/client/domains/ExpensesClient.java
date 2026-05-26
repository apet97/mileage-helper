package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyBinaryResponse;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class ExpensesClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public ExpensesClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getExpenses(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        return getExpenses(workspaceId, null, pageRequest);
    }

    public JsonNode getExpenses(String workspaceId, String userId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest.Builder builder = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/expenses")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize());
        if (userId != null && !userId.isBlank()) {
            builder.query("user-id", userId);
        }
        return readJson(builder.build());
    }

    public JsonNode createExpense(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        return createExpense(workspaceId, requestBody, null, null, null);
    }

    public JsonNode createExpense(String workspaceId, JsonNode requestBody, String fileName, String contentType, byte[] fileBytes) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(requestBody, "requestBody");
        
        String boundary = "clockify-rest-client-" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, requestBody, fileName, contentType, fileBytes);
        
        ClockifyRequest request = ClockifyRequest.builder("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/expenses")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .bytesBody(body, "multipart/form-data; boundary=" + boundary)
                .build();
        return readJson(request);
    }

    public JsonNode getExpense(String workspaceId, String expenseId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("expenseId", expenseId);
        return readJson(ClockifyRequest.builder("GET", expensePath(workspaceId, expenseId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode getCategory(String workspaceId, String categoryId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("categoryId", categoryId);
        return readJson(ClockifyRequest.builder("GET", categoryPath(workspaceId, categoryId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode updateExpense(String workspaceId, String expenseId, JsonNode requestBody) throws IOException, InterruptedException {
        return updateExpense(workspaceId, expenseId, requestBody, null, null, null);
    }

    public JsonNode updateExpense(String workspaceId, String expenseId, JsonNode requestBody, String fileName, String contentType, byte[] fileBytes) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("expenseId", expenseId);
        Objects.requireNonNull(requestBody, "requestBody");
        
        com.fasterxml.jackson.databind.node.ObjectNode fields = objectMapper.createObjectNode();
        if (requestBody.isObject()) {
            fields.setAll((com.fasterxml.jackson.databind.node.ObjectNode) requestBody);
        }
        
        if (fields.has("changeFields")) {
            JsonNode cfNode = fields.get("changeFields");
            if (cfNode.isArray()) {
                StringJoiner sj = new StringJoiner(",");
                for (JsonNode item : cfNode) {
                    sj.add(item.asText().toUpperCase(Locale.ROOT));
                }
                fields.put("changeFields", sj.toString());
            }
        }
        
        boolean hasChangeFields = false;
        var keys = fields.fieldNames();
        while (keys.hasNext()) {
            if (keys.next().equalsIgnoreCase("changeFields")) {
                hasChangeFields = true;
                break;
            }
        }
        if (!hasChangeFields) {
            StringJoiner sj = new StringJoiner(",");
            var entries = fields.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                String mapped = mapToChangeField(entry.getKey());
                if (mapped != null) {
                    sj.add(mapped);
                }
            }
            if (fileBytes != null && fileBytes.length > 0) {
                sj.add("FILE");
            }
            if (sj.length() > 0) {
                fields.put("changeFields", sj.toString());
            }
        }
        
        String boundary = "clockify-rest-client-" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, fields, fileName, contentType, fileBytes);
        
        ClockifyRequest request = ClockifyRequest.builder("PUT", expensePath(workspaceId, expenseId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .bytesBody(body, "multipart/form-data; boundary=" + boundary)
                .build();
        return readJson(request);
    }

    public void deleteExpense(String workspaceId, String expenseId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("expenseId", expenseId);
        transport.send(ClockifyRequest.builder("DELETE", expensePath(workspaceId, expenseId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode getCategories(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        return getCategories(workspaceId, null, null, null, null, pageRequest);
    }

    public JsonNode getCategories(String workspaceId, String sortColumn, String sortOrder, Boolean archived, String name, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest.Builder builder = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/expenses/categories")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize());
        if (sortColumn != null && !sortColumn.isBlank()) {
            builder.query("sort-column", sortColumn);
        }
        if (sortOrder != null && !sortOrder.isBlank()) {
            builder.query("sort-order", sortOrder);
        }
        if (archived != null) {
            builder.query("archived", archived);
        }
        if (name != null && !name.isBlank()) {
            builder.query("name", name);
        }
        return readJson(builder.build());
    }

    public JsonNode createCategory(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/expenses/categories", requestBody);
    }

    public JsonNode updateCategory(String workspaceId, String categoryId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("categoryId", categoryId);
        return sendJson("PUT", categoryPath(workspaceId, categoryId), requestBody);
    }

    public void deleteCategory(String workspaceId, String categoryId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("categoryId", categoryId);
        transport.send(ClockifyRequest.builder("DELETE", categoryPath(workspaceId, categoryId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode updateCategoryStatus(String workspaceId, String categoryId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("categoryId", categoryId);
        
        com.fasterxml.jackson.databind.node.ObjectNode fields = objectMapper.createObjectNode();
        if (requestBody != null && requestBody.isObject()) {
            fields.setAll((com.fasterxml.jackson.databind.node.ObjectNode) requestBody);
        }
        
        if (fields.has("status")) {
            String statusVal = fields.get("status").asText();
            if ("ARCHIVED".equalsIgnoreCase(statusVal)) {
                fields.put("archived", true);
            } else if ("ACTIVE".equalsIgnoreCase(statusVal)) {
                fields.put("archived", false);
            }
        }
        if (!fields.has("archived")) {
            fields.put("archived", true);
        }
        
        return sendJson("PATCH", categoryPath(workspaceId, categoryId) + "/status", fields);
    }

    public ClockifyBinaryResponse downloadFile(String workspaceId, String expenseId, String fileId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("expenseId", expenseId);
        requireId("fileId", fileId);
        ClockifyRequest request = ClockifyRequest.builder("GET", expensePath(workspaceId, expenseId) + "/files/" + segment("fileId", fileId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .binaryResponse(true)
                .build();
        return transport.sendBinary(request);
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

    private static String expensePath(String workspaceId, String expenseId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/expenses/" + segment("expenseId", expenseId);
    }

    private static String categoryPath(String workspaceId, String categoryId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/expenses/categories/" + segment("categoryId", categoryId);
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static byte[] buildMultipartBody(String boundary, JsonNode fields) throws IOException {
        return buildMultipartBody(boundary, fields, null, null, null);
    }

    private static byte[] buildMultipartBody(String boundary, JsonNode fields, String fileName, String contentType, byte[] fileBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var iterator = fields.fields();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNull() || "file".equals(name)) {
                continue;
            }
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            String stringValue;
            if (value.isTextual()) {
                stringValue = value.asText();
            } else {
                stringValue = value.toString();
            }
            out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(stringValue.getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        if (fileBytes != null && fileBytes.length > 0) {
            String actualFileName = safeMultipartFileName(fileName);
            String actualContentType = safeMultipartContentType(contentType);
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + actualFileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + actualContentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String safeMultipartFileName(String fileName) {
        String cleaned = fileName == null ? "" : fileName.trim().replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }
        cleaned = cleaned.replaceAll("[\\p{Cntrl}\";:]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "file";
        }
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120);
    }

    private static String safeMultipartContentType(String contentType) {
        String cleaned = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isBlank() || !cleaned.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) {
            return "application/octet-stream";
        }
        return cleaned;
    }

    private static String mapToChangeField(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "notes", "note" -> "NOTES";
            case "amount", "quantity" -> "AMOUNT";
            case "categoryid", "category" -> "CATEGORY";
            case "date", "datetime" -> "DATE";
            case "userid", "user" -> "USER";
            case "projectid", "project" -> "PROJECT";
            case "taskid", "task" -> "TASK";
            case "billable" -> "BILLABLE";
            default -> null;
        };
    }
}
