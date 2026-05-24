package com.cake.clockify.addon.mileage.clockify;

import com.cake.clockify.addon.db.service.ClockifyClientFactory;
import com.cake.clockify.client.ClockifyClient;
import com.cake.clockify.client.ClockifyPageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClockifyExpenseGateway {
    private static final int CATEGORY_PAGE_SIZE = 200;

    private final ClockifyClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    public ClockifyExpenseGateway(ClockifyClientFactory clientFactory, ObjectMapper objectMapper) {
        this.clientFactory = clientFactory;
        this.objectMapper = objectMapper;
    }

    public ClockifyExpenseSnapshot getExpense(String workspaceId, String expenseId) throws IOException, InterruptedException {
        JsonNode node = client(workspaceId).expenses().getExpense(workspaceId, expenseId);
        return new ClockifyExpenseSnapshot(
                text(node, "id"),
                text(node, "workspaceId"),
                text(node, "userId"),
                text(node, "date"),
                text(node, "projectId"),
                text(node, "taskId"),
                text(node, "categoryId"),
                text(node, "notes"),
                decimal(node.get("quantity")),
                node.has("billable") && !node.get("billable").isNull() ? node.get("billable").asBoolean() : null,
                text(node, "fileId"),
                decimal(node.get("total")),
                node.has("locked") && !node.get("locked").isNull() ? node.get("locked").asBoolean() : null);
    }

    public JsonNode createFlatExpense(String workspaceId, CreateFlatExpenseCommand command) throws IOException, InterruptedException {
        return client(workspaceId).expenses().createExpense(workspaceId, createBody(command));
    }

    public JsonNode createFlatExpenseWithReceipt(
            String workspaceId,
            CreateFlatExpenseCommand command,
            String fileName,
            String contentType,
            byte[] fileBytes) throws IOException, InterruptedException {
        return client(workspaceId).expenses().createExpense(workspaceId, createBody(command), fileName, contentType, fileBytes);
    }

    public JsonNode updateFlatExpense(String workspaceId, String expenseId, UpdateFlatExpenseCommand command)
            throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("categoryId", command.categoryId());
        putIfPresent(body, "userId", command.userId());
        putIfPresent(body, "date", command.date());
        putIfPresent(body, "projectId", command.projectId());
        putIfPresent(body, "taskId", command.taskId());
        if (command.billable() != null) {
            body.put("billable", command.billable());
        }
        body.put("amount", amountText(command.amount(), command.roundingMode()));
        body.put("notes", command.notes() == null ? "" : command.notes());
        body.put("changeFields", "CATEGORY,AMOUNT,NOTES");
        return client(workspaceId).expenses().updateExpense(workspaceId, expenseId, body);
    }

    public List<ClockifyCategoryOption> listCategories(String workspaceId) throws IOException, InterruptedException {
        JsonNode root = client(workspaceId).expenses().getCategories(workspaceId, new ClockifyPageRequest(1, CATEGORY_PAGE_SIZE));
        JsonNode categories = root.isArray() ? root : root.path("categories");
        List<ClockifyCategoryOption> out = new ArrayList<>();
        if (categories instanceof ArrayNode array) {
            for (JsonNode item : array) {
                boolean hasUnitPrice = item.path("hasUnitPrice").asBoolean(false);
                out.add(new ClockifyCategoryOption(
                        text(item, "id"),
                        text(item, "name"),
                        hasUnitPrice ? "UNIT" : "FLAT",
                        text(item, "unit"),
                        decimal(item.get("priceInCents"))));
            }
        }
        return out;
    }

    private ClockifyClient client(String workspaceId) {
        return clientFactory.getClient(workspaceId);
    }

    private ObjectNode createBody(CreateFlatExpenseCommand command) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("categoryId", command.categoryId());
        body.put("userId", command.userId());
        if (command.date() != null) {
            body.put("date", command.date() + "T12:00:00Z");
        }
        putIfPresent(body, "projectId", command.projectId());
        putIfPresent(body, "taskId", command.taskId());
        body.put("amount", amountText(command.amount(), command.roundingMode()));
        if (command.billable() != null) {
            body.put("billable", command.billable());
        }
        putIfPresent(body, "notes", command.notes());
        return body;
    }

    private static String amountText(BigDecimal amount, RoundingMode roundingMode) {
        RoundingMode mode = roundingMode == null ? RoundingMode.HALF_UP : roundingMode;
        return amount.setScale(2, mode).toPlainString();
    }

    private static void putIfPresent(ObjectNode body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            return new BigDecimal(node.asText());
        }
        return null;
    }
}
