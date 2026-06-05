package com.cake.clockify.addon.mileage.clockify;

import com.cake.clockify.addon.db.service.ClockifyClientFactory;
import com.cake.clockify.client.ClockifyApiException;
import com.cake.clockify.client.ClockifyClient;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.domains.ExpensesClient;
import com.cake.clockify.client.domains.ProjectsClient;
import com.cake.clockify.client.domains.UsersClient;
import com.cake.clockify.client.models.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClockifyExpenseGatewayTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ClockifyClientFactory clientFactory;
    private ClockifyClient client;
    private ExpensesClient expensesClient;
    private ProjectsClient projectsClient;
    private UsersClient usersClient;
    private ClockifyExpenseGateway gateway;

    @BeforeEach
    void setUp() {
        clientFactory = mock(ClockifyClientFactory.class);
        client = mock(ClockifyClient.class);
        expensesClient = mock(ExpensesClient.class);
        projectsClient = mock(ProjectsClient.class);
        usersClient = mock(UsersClient.class);
        when(clientFactory.getClient("ws-gateway")).thenReturn(client);
        when(client.expenses()).thenReturn(expensesClient);
        when(client.projects()).thenReturn(projectsClient);
        when(client.users()).thenReturn(usersClient);
        gateway = new ClockifyExpenseGateway(clientFactory, objectMapper);
    }

    @Test
    void fetchExpenseMapsQuantityAndTotalWithBigDecimal() throws Exception {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "exp-1");
        response.put("workspaceId", "ws-gateway");
        response.put("userId", "user-1");
        response.put("date", "2026-05-24T12:00:00Z");
        response.put("projectId", "project-1");
        response.put("taskId", "task-1");
        response.put("categoryId", "cat-unit");
        response.put("notes", "note");
        response.put("quantity", "37.400000");
        response.put("billable", true);
        response.put("fileId", "file-1");
        response.put("total", 2449.70);
        response.put("locked", false);
        when(expensesClient.getExpense("ws-gateway", "exp-1")).thenReturn(response);

        ClockifyExpenseSnapshot snapshot = gateway.getExpense("ws-gateway", "exp-1");

        assertThat(snapshot.quantity()).isEqualByComparingTo(new BigDecimal("37.400000"));
        assertThat(snapshot.total()).isEqualByComparingTo(new BigDecimal("2449.70"));
        assertThat(snapshot.fileId()).isEqualTo("file-1");
        assertThat(snapshot.locked()).isFalse();
    }

    @Test
    void createFlatExpenseSendsOutputCategoryAndAmountAsPlainString() throws Exception {
        when(expensesClient.createExpense(eq("ws-gateway"), any(JsonNode.class))).thenReturn(objectMapper.createObjectNode().put("id", "exp-created"));
        CreateFlatExpenseCommand command = command();

        JsonNode response = gateway.createFlatExpense("ws-gateway", command);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(expensesClient).createExpense(eq("ws-gateway"), body.capture());
        assertThat(body.getValue().path("categoryId").asText()).isEqualTo("cat-flat");
        assertThat(body.getValue().path("amount").asText()).isEqualTo("24.50");
        assertThat(response.path("id").asText()).isEqualTo("exp-created");
    }

    @Test
    void createFlatExpenseCanSendAmountAsUnitQuantityWithoutMoneyRounding() throws Exception {
        when(expensesClient.createExpense(eq("ws-gateway"), any(JsonNode.class))).thenReturn(objectMapper.createObjectNode().put("id", "exp-created"));
        CreateFlatExpenseCommand command = new CreateFlatExpenseCommand(
                "cat-mileage",
                "user-1",
                LocalDate.of(2026, 5, 24),
                null,
                null,
                new BigDecimal("1.234500"),
                true,
                "converted note",
                RoundingMode.HALF_UP,
                true);

        gateway.createFlatExpense("ws-gateway", command);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(expensesClient).createExpense(eq("ws-gateway"), body.capture());
        assertThat(body.getValue().path("amount").asText()).isEqualTo("1.2345");
    }

    @Test
    void createFlatExpenseWithReceiptForwardsFileOnlyToClockify() throws Exception {
        byte[] file = new byte[] {1, 2, 3};
        when(expensesClient.createExpense(eq("ws-gateway"), any(JsonNode.class), eq("receipt.png"), eq("image/png"), eq(file)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-file").put("fileId", "file-1"));

        JsonNode response = gateway.createFlatExpenseWithReceipt("ws-gateway", command(), "receipt.png", "image/png", file);

        assertThat(response.path("fileId").asText()).isEqualTo("file-1");
        verify(expensesClient).createExpense(eq("ws-gateway"), any(JsonNode.class), eq("receipt.png"), eq("image/png"), eq(file));
    }

    @Test
    void updateFlatExpenseSendsSourceIdentityDateBillableCategoryAmountNotesAndChangeFields() throws Exception {
        when(expensesClient.updateExpense(eq("ws-gateway"), eq("exp-1"), any(JsonNode.class)))
                .thenReturn(objectMapper.createObjectNode().put("id", "exp-1"));
        UpdateFlatExpenseCommand command = new UpdateFlatExpenseCommand(
                "cat-flat",
                "user-1",
                "2026-05-24T12:00:00Z",
                "project-1",
                "task-1",
                false,
                new BigDecimal("24.50"),
                "converted",
                RoundingMode.HALF_UP);

        gateway.updateFlatExpense("ws-gateway", "exp-1", command);

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(expensesClient).updateExpense(eq("ws-gateway"), eq("exp-1"), body.capture());
        assertThat(body.getValue().path("categoryId").asText()).isEqualTo("cat-flat");
        assertThat(body.getValue().path("userId").asText()).isEqualTo("user-1");
        assertThat(body.getValue().path("date").asText()).isEqualTo("2026-05-24T12:00:00Z");
        assertThat(body.getValue().path("projectId").asText()).isEqualTo("project-1");
        assertThat(body.getValue().path("taskId").asText()).isEqualTo("task-1");
        assertThat(body.getValue().path("billable").asBoolean()).isFalse();
        assertThat(body.getValue().path("amount").asText()).isEqualTo("24.50");
        assertThat(body.getValue().path("notes").asText()).isEqualTo("converted");
        assertThat(body.getValue().path("changeFields").asText()).isEqualTo("CATEGORY,AMOUNT,NOTES");
    }

    @Test
    void listCategoriesNormalizesUnitAndFlatTypes() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        var categories = root.putArray("categories");
        categories.addObject().put("id", "cat-unit").put("name", "Mileage unit").put("hasUnitPrice", true).put("unit", "mi").put("priceInCents", 1);
        categories.addObject().put("id", "cat-flat").put("name", "Mileage flat").put("hasUnitPrice", false).put("unit", "");
        when(expensesClient.getCategories(eq("ws-gateway"), any(ClockifyPageRequest.class))).thenReturn(root);

        assertThat(gateway.listCategories("ws-gateway"))
                .extracting(ClockifyCategoryOption::type)
                .containsExactly("UNIT", "FLAT");
    }

    @Test
    void listCategoriesReadsBeyondFirstPage() throws Exception {
        when(expensesClient.getCategories(eq("ws-gateway"), eq(new ClockifyPageRequest(1, 200))))
                .thenReturn(categoryPage(0, 200));
        when(expensesClient.getCategories(eq("ws-gateway"), eq(new ClockifyPageRequest(2, 200))))
                .thenReturn(categoryPage(200, 1));

        assertThat(gateway.listCategories("ws-gateway"))
                .hasSize(201)
                .last()
                .isEqualTo(new ClockifyCategoryOption("cat-200", "Category 200", "FLAT", null, null));
    }

    @Test
    void listCategoriesFailsWhenClockifyKeepsReturningFullPages() throws Exception {
        when(expensesClient.getCategories(eq("ws-gateway"), any(ClockifyPageRequest.class)))
                .thenAnswer(invocation -> {
                    ClockifyPageRequest request = invocation.getArgument(1);
                    int count = request.page() > 100 ? 1 : 200;
                    return categoryPage((request.page() - 1) * 200, count);
                });

        assertThatThrownBy(() -> gateway.listCategories("ws-gateway"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Clockify pagination exceeded 100 pages");
    }

    @Test
    void listProjectsSkipsArchivedProjects() throws Exception {
        var projects = objectMapper.createArrayNode();
        projects.addObject().put("id", "project-1").put("name", "Client Visit").put("archived", false);
        projects.addObject().put("id", "project-archived").put("name", "Old Client").put("archived", true);
        when(projectsClient.getProjects(eq("ws-gateway"), any(ClockifyPageRequest.class))).thenReturn(projects);

        assertThat(gateway.listProjects("ws-gateway"))
                .containsExactly(new ClockifyProjectOption("project-1", "Client Visit"));
    }

    @Test
    void listProjectsReadsBeyondFirstPageAndStillSkipsArchivedProjects() throws Exception {
        when(projectsClient.getProjects(eq("ws-gateway"), eq(new ClockifyPageRequest(1, 200))))
                .thenReturn(projectPage(0, 200, false));
        when(projectsClient.getProjects(eq("ws-gateway"), eq(new ClockifyPageRequest(2, 200))))
                .thenReturn(projectPage(200, 1, false));

        assertThat(gateway.listProjects("ws-gateway"))
                .hasSize(201)
                .last()
                .isEqualTo(new ClockifyProjectOption("project-200", "Project 200"));
    }

    @Test
    void listUsersReturnsDisplayNamesAndEmailFallbacks() throws Exception {
        when(usersClient.getUsersOfWorkspace(eq("ws-gateway"), any(ClockifyPageRequest.class)))
                .thenReturn(List.of(
                        new User("user-1", "ada@example.test", "Ada Lovelace"),
                        new User("user-2", "grace@example.test", "")));

        assertThat(gateway.listUsers("ws-gateway"))
                .containsExactly(
                        new ClockifyUserOption("user-1", "Ada Lovelace", "ada@example.test"),
                        new ClockifyUserOption("user-2", "grace@example.test", "grace@example.test"));
    }

    @Test
    void listUsersReadsBeyondFirstPage() throws Exception {
        when(usersClient.getUsersOfWorkspace(eq("ws-gateway"), eq(new ClockifyPageRequest(1, 200))))
                .thenReturn(users(0, 200));
        when(usersClient.getUsersOfWorkspace(eq("ws-gateway"), eq(new ClockifyPageRequest(2, 200))))
                .thenReturn(users(200, 1));

        assertThat(gateway.listUsers("ws-gateway"))
                .hasSize(201)
                .last()
                .isEqualTo(new ClockifyUserOption("user-200", "User 200", "user-200@example.test"));
    }

    @Test
    void createOrRepairMileageCategoryCreatesUnitCategoryWithRoundedCentRate() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("categories");
        when(expensesClient.getCategories(eq("ws-gateway"), any(ClockifyPageRequest.class))).thenReturn(root);
        when(expensesClient.createCategory(eq("ws-gateway"), any(JsonNode.class)))
                .thenReturn(objectMapper.createObjectNode()
                        .put("id", "cat-mileage")
                        .put("name", "Mileage")
                        .put("hasUnitPrice", true)
                        .put("unit", "mile")
                        .put("priceInCents", 73));

        ClockifyCategoryOption option = gateway.createOrRepairMileageCategory("ws-gateway", new BigDecimal("0.725"));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(expensesClient).createCategory(eq("ws-gateway"), body.capture());
        assertThat(body.getValue().path("name").asText()).isEqualTo("Mileage");
        assertThat(body.getValue().path("hasUnitPrice").asBoolean()).isTrue();
        assertThat(body.getValue().path("unit").asText()).isEqualTo("mile");
        assertThat(body.getValue().path("priceInCents").asInt()).isEqualTo(73);
        assertThat(option).isEqualTo(new ClockifyCategoryOption("cat-mileage", "Mileage", "UNIT", "mile", new BigDecimal("73")));
    }

    @Test
    void createOrRepairMileageCategoryRepairsExistingMileageCategory() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("categories")
                .addObject()
                .put("id", "cat-mileage")
                .put("name", "Mileage")
                .put("hasUnitPrice", false)
                .put("unit", "")
                .put("priceInCents", 1);
        when(expensesClient.getCategories(eq("ws-gateway"), any(ClockifyPageRequest.class))).thenReturn(root);
        when(expensesClient.updateCategory(eq("ws-gateway"), eq("cat-mileage"), any(JsonNode.class)))
                .thenReturn(objectMapper.createObjectNode()
                        .put("id", "cat-mileage")
                        .put("name", "Mileage")
                        .put("hasUnitPrice", true)
                        .put("unit", "mile")
                        .put("priceInCents", 66));

        ClockifyCategoryOption option = gateway.createOrRepairMileageCategory("ws-gateway", new BigDecimal("0.655"));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(expensesClient).updateCategory(eq("ws-gateway"), eq("cat-mileage"), body.capture());
        assertThat(body.getValue().path("hasUnitPrice").asBoolean()).isTrue();
        assertThat(body.getValue().path("unit").asText()).isEqualTo("mile");
        assertThat(body.getValue().path("priceInCents").asInt()).isEqualTo(66);
        assertThat(option).isEqualTo(new ClockifyCategoryOption("cat-mileage", "Mileage", "UNIT", "mile", new BigDecimal("66")));
    }

    @Test
    void createOrRepairMileageCategoryAttemptsCreateWhenCategoryLookupIsForbidden() throws Exception {
        when(expensesClient.getCategories(eq("ws-gateway"), any(ClockifyPageRequest.class)))
                .thenThrow(ClockifyApiException.forStatus(403, java.util.Map.of(), "{\"message\":\"Forbidden\"}"));
        when(expensesClient.createCategory(eq("ws-gateway"), any(JsonNode.class)))
                .thenReturn(objectMapper.createObjectNode()
                        .put("id", "cat-mileage")
                        .put("name", "Mileage")
                        .put("hasUnitPrice", true)
                        .put("unit", "mile")
                        .put("priceInCents", 73));

        ClockifyCategoryOption option = gateway.createOrRepairMileageCategory("ws-gateway", new BigDecimal("0.725"));

        verify(expensesClient).createCategory(eq("ws-gateway"), any(JsonNode.class));
        assertThat(option).isEqualTo(new ClockifyCategoryOption("cat-mileage", "Mileage", "UNIT", "mile", new BigDecimal("73")));
    }

    @Test
    void gatewayUsesClockifyClientFactoryForWorkspace() throws Exception {
        when(expensesClient.createExpense(eq("ws-gateway"), any(JsonNode.class))).thenReturn(objectMapper.createObjectNode());

        gateway.createFlatExpense("ws-gateway", command());

        verify(clientFactory).getClient("ws-gateway");
    }

    @Test
    void listExpensesForReportMapsNestedNamesCentsAndFiltersByDate() throws Exception {
        when(expensesClient.getExpenses(eq("ws-gateway"), isNull(), any(ClockifyPageRequest.class)))
                .thenReturn(expensePage(
                        expenseRow("exp-1", "user-1", "2026-05-24T12:00:00Z", 725.0, "cat-mileage", "Mileage", "North Route"),
                        expenseRow("exp-old", "user-1", "2020-01-01T00:00:00Z", 1800.0, "cat-meals", "Meals", null),
                        expenseRow("exp-2", "user-2", "2026-05-25T08:00:00Z", 1800.0, "cat-meals", "Meals", null)));

        ClockifyExpenseListResult result = gateway.listExpensesForReport(
                "ws-gateway", null, LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"));

        assertThat(result.truncated()).isFalse();
        assertThat(result.items()).extracting(ClockifyExpenseListItem::expenseId).containsExactly("exp-1", "exp-2");
        ClockifyExpenseListItem mileage = result.items().get(0);
        assertThat(mileage.amount()).isEqualByComparingTo("7.25"); // 725 cents -> major units
        assertThat(mileage.categoryName()).isEqualTo("Mileage");
        assertThat(mileage.projectName()).isEqualTo("North Route");
        assertThat(mileage.date()).isEqualTo(LocalDate.parse("2026-05-24"));
        assertThat(result.items().get(1).amount()).isEqualByComparingTo("18.00");
        assertThat(result.items().get(1).projectName()).isNull();
    }

    @Test
    void listExpensesForReportPassesTrimmedUserIdWhenPresent() throws Exception {
        when(expensesClient.getExpenses(eq("ws-gateway"), eq("user-2"), any(ClockifyPageRequest.class)))
                .thenReturn(expensePage());

        gateway.listExpensesForReport("ws-gateway", "  user-2 ", LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"));

        verify(expensesClient).getExpenses(eq("ws-gateway"), eq("user-2"), any(ClockifyPageRequest.class));
    }

    private ObjectNode expensePage(ObjectNode... rows) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode expenses = root.putObject("expenses");
        var array = expenses.putArray("expenses");
        for (ObjectNode row : rows) {
            array.add(row);
        }
        expenses.put("count", rows.length);
        return root;
    }

    private ObjectNode expenseRow(String id, String userId, String date, double totalCents,
                                  String categoryId, String categoryName, String projectName) {
        ObjectNode row = objectMapper.createObjectNode();
        row.put("id", id);
        row.put("userId", userId);
        row.put("date", date);
        row.put("total", totalCents);
        row.putObject("category").put("id", categoryId).put("name", categoryName);
        if (projectName != null) {
            row.putObject("project").put("id", "p-" + id).put("name", projectName);
        }
        return row;
    }

    private static CreateFlatExpenseCommand command() {
        return new CreateFlatExpenseCommand(
                "cat-flat",
                "user-1",
                LocalDate.of(2026, 5, 24),
                "project-1",
                "task-1",
                new BigDecimal("24.50"),
                true,
                "converted note",
                RoundingMode.HALF_UP);
    }

    private ObjectNode categoryPage(int start, int count) {
        ObjectNode root = objectMapper.createObjectNode();
        var categories = root.putArray("categories");
        for (int i = start; i < start + count; i++) {
            categories.addObject().put("id", "cat-" + i).put("name", "Category " + i).put("hasUnitPrice", false);
        }
        return root;
    }

    private JsonNode projectPage(int start, int count, boolean archived) {
        var projects = objectMapper.createArrayNode();
        for (int i = start; i < start + count; i++) {
            projects.addObject().put("id", "project-" + i).put("name", "Project " + i).put("archived", archived);
        }
        return projects;
    }

    private static List<User> users(int start, int count) {
        List<User> users = new ArrayList<>();
        for (int i = start; i < start + count; i++) {
            users.add(new User("user-" + i, "user-" + i + "@example.test", "User " + i));
        }
        return users;
    }
}
