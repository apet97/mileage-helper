package com.cake.clockify.addon.mileage.clockify;

import com.cake.clockify.addon.db.service.ClockifyClientFactory;
import com.cake.clockify.client.ClockifyClient;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.models.User;
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
    private static final int PROJECT_PAGE_SIZE = 200;
    private static final int USER_PAGE_SIZE = 200;
    private static final String MILEAGE_CATEGORY_NAME = "Mileage";
    private static final String MILEAGE_UNIT = "mile";

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
        body.put("amount", amountText(command.amount(), command.roundingMode(), command.amountIsQuantity()));
        body.put("notes", command.notes() == null ? "" : command.notes());
        body.put("changeFields", "CATEGORY,AMOUNT,NOTES");
        return client(workspaceId).expenses().updateExpense(workspaceId, expenseId, body);
    }

    public List<ClockifyCategoryOption> listCategories(String workspaceId) throws IOException, InterruptedException {
        ClockifyClient clockify = client(workspaceId);
        return listAllPages(CATEGORY_PAGE_SIZE, (page, out) -> {
            ArrayNode array = arrayNode(
                    clockify.expenses().getCategories(workspaceId, new ClockifyPageRequest(page, CATEGORY_PAGE_SIZE)),
                    "categories");
            if (array != null) {
                for (JsonNode item : array) {
                    out.add(categoryOption(item));
                }
            }
            return sizeOf(array);
        });
    }

    public ClockifyCategoryOption createOrRepairMileageCategory(String workspaceId, BigDecimal rate)
            throws IOException, InterruptedException {
        BigDecimal priceInCents = rate == null
                ? BigDecimal.ZERO
                : rate.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        ObjectNode body = mileageCategoryBody(priceInCents);
        ClockifyCategoryOption existing = listCategories(workspaceId).stream()
                .filter(category -> MILEAGE_CATEGORY_NAME.equalsIgnoreCase(category.name()))
                .findFirst()
                .orElse(null);
        JsonNode response = existing == null
                ? client(workspaceId).expenses().createCategory(workspaceId, body)
                : client(workspaceId).expenses().updateCategory(workspaceId, existing.id(), body);
        ClockifyCategoryOption option = categoryOption(response);
        if (option.id() == null && existing != null) {
            return existing;
        }
        return option;
    }

    public List<ClockifyProjectOption> listProjects(String workspaceId) throws IOException, InterruptedException {
        ClockifyClient clockify = client(workspaceId);
        return listAllPages(PROJECT_PAGE_SIZE, (page, out) -> {
            ArrayNode array = arrayNode(
                    clockify.projects().getProjects(workspaceId, new ClockifyPageRequest(page, PROJECT_PAGE_SIZE)),
                    "projects");
            if (array != null) {
                for (JsonNode item : array) {
                    if (!item.path("archived").asBoolean(false)) {
                        out.add(new ClockifyProjectOption(text(item, "id"), text(item, "name")));
                    }
                }
            }
            return sizeOf(array);
        });
    }

    public List<ClockifyUserOption> listUsers(String workspaceId) throws IOException, InterruptedException {
        ClockifyClient clockify = client(workspaceId);
        return listAllPages(USER_PAGE_SIZE, (page, out) -> {
            List<User> users = clockify.users().getUsersOfWorkspace(workspaceId, new ClockifyPageRequest(page, USER_PAGE_SIZE));
            for (User user : users) {
                out.add(new ClockifyUserOption(user.id(), displayName(user), user.email()));
            }
            return users.size();
        });
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
        body.put("amount", amountText(command.amount(), command.roundingMode(), command.amountIsQuantity()));
        if (command.billable() != null) {
            body.put("billable", command.billable());
        }
        putIfPresent(body, "notes", command.notes());
        return body;
    }

    private ObjectNode mileageCategoryBody(BigDecimal priceInCents) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", MILEAGE_CATEGORY_NAME);
        body.put("hasUnitPrice", true);
        body.put("priceInCents", priceInCents.toBigIntegerExact());
        body.put("unit", MILEAGE_UNIT);
        return body;
    }

    private static String amountText(BigDecimal amount, RoundingMode roundingMode, boolean amountIsQuantity) {
        if (amountIsQuantity) {
            return amount.stripTrailingZeros().toPlainString();
        }
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

    private static ClockifyCategoryOption categoryOption(JsonNode item) {
        boolean hasUnitPrice = item != null && item.path("hasUnitPrice").asBoolean(false);
        return new ClockifyCategoryOption(
                text(item, "id"),
                text(item, "name"),
                hasUnitPrice ? "UNIT" : "FLAT",
                text(item, "unit"),
                decimal(item == null ? null : item.get("priceInCents")));
    }

    private static <T> List<T> listAllPages(int pageSize, PageAppender<T> appender)
            throws IOException, InterruptedException {
        List<T> out = new ArrayList<>();
        int page = 1;
        while (true) {
            int sourceCount = appender.append(page, out);
            if (sourceCount < pageSize) {
                return out;
            }
            page++;
        }
    }

    private static ArrayNode arrayNode(JsonNode root, String field) {
        if (root == null) {
            return null;
        }
        JsonNode node = root.isArray() ? root : root.path(field);
        return node instanceof ArrayNode array ? array : null;
    }

    private static int sizeOf(ArrayNode array) {
        return array == null ? 0 : array.size();
    }

    private static String displayName(User user) {
        if (user.name() != null && !user.name().isBlank()) {
            return user.name();
        }
        if (user.email() != null && !user.email().isBlank()) {
            return user.email();
        }
        return user.id();
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

    @FunctionalInterface
    private interface PageAppender<T> {
        int append(int page, List<T> out) throws IOException, InterruptedException;
    }
}
