package com.cake.clockify.addon.mileage.clockify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;

class ClockifyExpenseJsonMapper {
    private static final String MILEAGE_CATEGORY_NAME = "Mileage";
    private static final String MILEAGE_UNIT = "mile";

    private final ObjectMapper objectMapper;

    ClockifyExpenseJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ClockifyExpenseSnapshot expenseSnapshot(JsonNode node) {
        return new ClockifyExpenseSnapshot(
                text(node, "id"),
                text(node, "workspaceId"),
                text(node, "userId"),
                text(node, "date"),
                text(node, "projectId"),
                text(node, "taskId"),
                text(node, "categoryId"),
                text(node, "notes"),
                decimal(node == null ? null : node.get("quantity")),
                node != null && node.has("billable") && !node.get("billable").isNull() ? node.get("billable").asBoolean() : null,
                text(node, "fileId"),
                decimal(node == null ? null : node.get("total")),
                booleanValue(node, "locked"),
                booleanValue(node, "finalized"),
                firstText(node, "approvalStatus", "approvalState"),
                booleanValue(node, "invoiced"));
    }

    ObjectNode createBody(CreateFlatExpenseCommand command) {
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

    ObjectNode updateBody(UpdateFlatExpenseCommand command) {
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
        return body;
    }

    ObjectNode mileageCategoryBody(BigDecimal priceInCents) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", MILEAGE_CATEGORY_NAME);
        body.put("hasUnitPrice", true);
        body.put("priceInCents", priceInCents.toBigIntegerExact());
        body.put("unit", MILEAGE_UNIT);
        return body;
    }

    ClockifyCategoryOption categoryOption(JsonNode item) {
        boolean hasUnitPrice = item != null && item.path("hasUnitPrice").asBoolean(false);
        return new ClockifyCategoryOption(
                text(item, "id"),
                text(item, "name"),
                hasUnitPrice ? "UNIT" : "FLAT",
                text(item, "unit"),
                decimal(item == null ? null : item.get("priceInCents")));
    }

    ClockifyProjectOption projectOption(JsonNode item) {
        return new ClockifyProjectOption(text(item, "id"), text(item, "name"));
    }

    ClockifyExpenseListItem expenseListItem(JsonNode item) {
        return new ClockifyExpenseListItem(
                text(item, "id"),
                text(item, "userId"),
                parseExpenseDate(text(item, "date")),
                nestedText(item, "project", "name"),
                nestedText(item, "category", "id"),
                nestedText(item, "category", "name"),
                centsToMajor(decimal(item == null ? null : item.get("total"))),
                currencyCode(item));
    }

    ArrayNode arrayNode(JsonNode root, String field) {
        if (root == null) {
            return null;
        }
        JsonNode node = root.isArray() ? root : root.path(field);
        return node instanceof ArrayNode array ? array : null;
    }

    /** Clockify's expense list shape is {@code { "expenses": { "expenses": [...], "count": N }, ... }};
        fall back to {@code { "expenses": [...] }} or a bare array for resilience to shape drift. */
    ArrayNode expenseRows(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode nested = root.path("expenses").path("expenses");
        if (nested instanceof ArrayNode array) {
            return array;
        }
        return arrayNode(root, "expenses");
    }

    int sizeOf(ArrayNode array) {
        return array == null ? 0 : array.size();
    }

    BigDecimal decimal(JsonNode node) {
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

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Boolean booleanValue(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asBoolean() : null;
    }

    private static String nestedText(JsonNode item, String objectField, String key) {
        if (item == null) {
            return null;
        }
        JsonNode object = item.get(objectField);
        return object == null || object.isNull() ? null : text(object, key);
    }

    private static String currencyCode(JsonNode item) {
        String direct = firstText(item, "currency", "currencyCode");
        String nested = nestedText(item, "currency", "code");
        String value = direct == null ? nested : direct;
        return normalizeCurrency(value);
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalDate parseExpenseDate(String value) {
        if (value == null || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal centsToMajor(BigDecimal cents) {
        return cents == null ? BigDecimal.ZERO : cents.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }
}
