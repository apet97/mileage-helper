package com.cake.clockify.addon.mileage.clockify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ClockifyExpenseJsonMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ClockifyExpenseJsonMapper mapper = new ClockifyExpenseJsonMapper(objectMapper);

    @Test
    void createBodyUsesAmountFieldForMileageQuantity() {
        var body = mapper.createBody(new CreateFlatExpenseCommand(
                "cat-mileage",
                "user-1",
                LocalDate.of(2026, 5, 24),
                "project-1",
                null,
                new BigDecimal("12.4000"),
                true,
                "note",
                RoundingMode.HALF_UP,
                true));

        assertThat(body.path("amount").asText()).isEqualTo("12.4");
        assertThat(body.has("quantity")).isFalse();
        assertThat(body.path("date").asText()).isEqualTo("2026-05-24T12:00:00Z");
    }

    @Test
    void updateBodyKeepsAmountConventionAndChangeFields() {
        var body = mapper.updateBody(new UpdateFlatExpenseCommand(
                "cat-mileage",
                "user-1",
                "2026-05-24T12:00:00Z",
                "project-1",
                "task-1",
                false,
                new BigDecimal("24.497"),
                null,
                RoundingMode.HALF_UP,
                true));

        assertThat(body.path("amount").asText()).isEqualTo("24.497");
        assertThat(body.path("notes").asText()).isEmpty();
        assertThat(body.path("changeFields").asText()).isEqualTo("CATEGORY,AMOUNT,NOTES");
        assertThat(body.has("quantity")).isFalse();
    }

    @Test
    void reportExpenseRowMapsNestedNamesAndCentsToMajorAmount() {
        var row = objectMapper.createObjectNode();
        row.put("id", "exp-1");
        row.put("userId", "user-1");
        row.put("date", "2026-05-24T12:00:00Z");
        row.put("total", 8990);
        row.put("currencyCode", "usd");
        row.putObject("category").put("id", "cat-mileage").put("name", "Mileage");
        row.putObject("project").put("id", "project-1").put("name", "North Route");

        ClockifyExpenseListItem item = mapper.expenseListItem(row);

        assertThat(item.expenseId()).isEqualTo("exp-1");
        assertThat(item.date()).isEqualTo(LocalDate.parse("2026-05-24"));
        assertThat(item.categoryId()).isEqualTo("cat-mileage");
        assertThat(item.projectName()).isEqualTo("North Route");
        assertThat(item.amount()).isEqualByComparingTo("89.90");
        assertThat(item.currency()).isEqualTo("USD");
    }

    @Test
    void reportExpenseRowMapsNestedCurrencyCode() {
        var row = objectMapper.createObjectNode();
        row.put("id", "exp-1");
        row.put("userId", "user-1");
        row.put("date", "2026-05-24T12:00:00Z");
        row.put("total", 8990);
        row.putObject("currency").put("code", "eur");

        ClockifyExpenseListItem item = mapper.expenseListItem(row);

        assertThat(item.currency()).isEqualTo("EUR");
    }

    @Test
    void expenseSnapshotMapsWorkflowFieldsWhenPresent() {
        var row = objectMapper.createObjectNode();
        row.put("id", "exp-1");
        row.put("workspaceId", "ws-1");
        row.put("userId", "user-1");
        row.put("date", "2026-05-24T12:00:00Z");
        row.put("categoryId", "cat-mileage");
        row.put("quantity", "12.4");
        row.put("billable", true);
        row.put("locked", true);
        row.put("finalized", true);
        row.put("approvalStatus", "SUBMITTED");
        row.put("invoiced", true);

        ClockifyExpenseSnapshot snapshot = mapper.expenseSnapshot(row);

        assertThat(snapshot.locked()).isTrue();
        assertThat(snapshot.finalized()).isTrue();
        assertThat(snapshot.approvalStatus()).isEqualTo("SUBMITTED");
        assertThat(snapshot.invoiced()).isTrue();
    }
}
