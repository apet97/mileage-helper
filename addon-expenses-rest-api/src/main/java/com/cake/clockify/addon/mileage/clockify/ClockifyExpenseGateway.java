package com.cake.clockify.addon.mileage.clockify;

import com.cake.clockify.addon.db.service.ClockifyClientFactory;
import com.cake.clockify.client.ClockifyApiException;
import com.cake.clockify.client.ClockifyClient;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.models.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ClockifyExpenseGateway {
    private static final int CATEGORY_PAGE_SIZE = 200;
    private static final int PROJECT_PAGE_SIZE = 200;
    private static final int USER_PAGE_SIZE = 200;
    private static final int MAX_OPTION_PAGES = 100;
    private static final int EXPENSE_PAGE_SIZE = 200;
    /** The expense list endpoint has no working server-side date filter, so we page and filter client-side.
        Bound the scan: 25 * 200 = 5000 expenses examined before signalling truncation (vs. throwing). */
    private static final int EXPENSE_MAX_PAGES = 25;
    private static final String MILEAGE_CATEGORY_NAME = "Mileage";

    private final ClockifyClientFactory clientFactory;
    private final ClockifyExpenseJsonMapper mapper;

    @Autowired
    public ClockifyExpenseGateway(ClockifyClientFactory clientFactory, ObjectMapper objectMapper) {
        this(clientFactory, new ClockifyExpenseJsonMapper(objectMapper));
    }

    ClockifyExpenseGateway(ClockifyClientFactory clientFactory, ClockifyExpenseJsonMapper mapper) {
        this.clientFactory = clientFactory;
        this.mapper = mapper;
    }

    public ClockifyExpenseSnapshot getExpense(String workspaceId, String expenseId) throws IOException, InterruptedException {
        JsonNode node = client(workspaceId).expenses().getExpense(workspaceId, expenseId);
        return mapper.expenseSnapshot(node);
    }

    public JsonNode createFlatExpense(String workspaceId, CreateFlatExpenseCommand command) throws IOException, InterruptedException {
        return client(workspaceId).expenses().createExpense(workspaceId, mapper.createBody(command));
    }

    public JsonNode createFlatExpenseWithReceipt(
            String workspaceId,
            CreateFlatExpenseCommand command,
            String fileName,
            String contentType,
            byte[] fileBytes) throws IOException, InterruptedException {
        return client(workspaceId).expenses().createExpense(workspaceId, mapper.createBody(command), fileName, contentType, fileBytes);
    }

    public JsonNode updateFlatExpense(String workspaceId, String expenseId, UpdateFlatExpenseCommand command)
            throws IOException, InterruptedException {
        return client(workspaceId).expenses().updateExpense(workspaceId, expenseId, mapper.updateBody(command));
    }

    public List<ClockifyCategoryOption> listCategories(String workspaceId) throws IOException, InterruptedException {
        ClockifyClient clockify = client(workspaceId);
        return listAllPages(CATEGORY_PAGE_SIZE, (page, out) -> {
            ArrayNode array = mapper.arrayNode(
                    clockify.expenses().getCategories(workspaceId, new ClockifyPageRequest(page, CATEGORY_PAGE_SIZE)),
                    "categories");
            if (array != null) {
                for (JsonNode item : array) {
                    out.add(mapper.categoryOption(item));
                }
            }
            return mapper.sizeOf(array);
        });
    }

    public ClockifyCategoryOption createOrRepairMileageCategory(String workspaceId, BigDecimal rate)
            throws IOException, InterruptedException {
        BigDecimal priceInCents = rate == null
                ? BigDecimal.ZERO
                : rate.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        ClockifyCategoryOption existing = null;
        try {
            existing = findMileageCategory(workspaceId).orElse(null);
        } catch (ClockifyApiException e) {
            if (!isAuthzFailure(e)) {
                throw e;
            }
        }
        JsonNode response = existing == null
                ? client(workspaceId).expenses().createCategory(workspaceId, mapper.mileageCategoryBody(priceInCents))
                : client(workspaceId).expenses().updateCategory(workspaceId, existing.id(), mapper.mileageCategoryBody(priceInCents));
        ClockifyCategoryOption option = mapper.categoryOption(response);
        if (option.id() == null && existing != null) {
            return existing;
        }
        return option;
    }

    public Optional<ClockifyCategoryOption> findMileageCategory(String workspaceId)
            throws IOException, InterruptedException {
        return listCategories(workspaceId).stream()
                .filter(ClockifyExpenseGateway::isMileageCategory)
                .findFirst();
    }

    public List<ClockifyProjectOption> listProjects(String workspaceId) throws IOException, InterruptedException {
        ClockifyClient clockify = client(workspaceId);
        return listAllPages(PROJECT_PAGE_SIZE, (page, out) -> {
            ArrayNode array = mapper.arrayNode(
                    clockify.projects().getProjects(workspaceId, new ClockifyPageRequest(page, PROJECT_PAGE_SIZE)),
                    "projects");
            if (array != null) {
                for (JsonNode item : array) {
                    if (!item.path("archived").asBoolean(false)) {
                        out.add(mapper.projectOption(item));
                    }
                }
            }
            return mapper.sizeOf(array);
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

    /**
     * Lists all workspace expenses (every category) in the inclusive {@code [from, to]} window, optionally
     * filtered to one user. Clockify exposes no working date filter on the list endpoint, so we page and
     * filter client-side. Category/project names come inline from the list response; {@code total} (cents)
     * is converted to a major-unit {@code amount}. Stops at a short page or, defensively, the page budget
     * (then {@link ClockifyExpenseListResult#truncated()} is true).
     */
    public ClockifyExpenseListResult listExpensesForReport(
            String workspaceId, String userId, LocalDate from, LocalDate to)
            throws IOException, InterruptedException {
        ClockifyClient clockify = client(workspaceId);
        String filterUser = blankToNull(userId);
        List<ClockifyExpenseListItem> out = new ArrayList<>();
        int page = 1;
        while (page <= EXPENSE_MAX_PAGES) {
            ArrayNode rows = mapper.expenseRows(
                    clockify.expenses().getExpenses(workspaceId, filterUser, new ClockifyPageRequest(page, EXPENSE_PAGE_SIZE)));
            int seen = mapper.sizeOf(rows);
            if (rows != null) {
                for (JsonNode item : rows) {
                    ClockifyExpenseListItem mapped = mapper.expenseListItem(item);
                    LocalDate date = mapped.date();
                    if (date == null || date.isBefore(from) || date.isAfter(to)) {
                        continue;
                    }
                    out.add(mapped);
                }
            }
            if (seen < EXPENSE_PAGE_SIZE) {
                return new ClockifyExpenseListResult(out, false);
            }
            page++;
        }
        return new ClockifyExpenseListResult(out, true);
    }

    private ClockifyClient client(String workspaceId) {
        return clientFactory.getClient(workspaceId);
    }

    private static <T> List<T> listAllPages(int pageSize, PageAppender<T> appender)
            throws IOException, InterruptedException {
        List<T> out = new ArrayList<>();
        int page = 1;
        while (page <= MAX_OPTION_PAGES) {
            int sourceCount = appender.append(page, out);
            if (sourceCount < pageSize) {
                return out;
            }
            page++;
        }
        throw new IllegalStateException("Clockify pagination exceeded " + MAX_OPTION_PAGES + " pages");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isAuthzFailure(ClockifyApiException e) {
        return e.statusCode() == 401 || e.statusCode() == 403;
    }

    private static boolean isMileageCategory(ClockifyCategoryOption category) {
        return category != null && MILEAGE_CATEGORY_NAME.equalsIgnoreCase(category.name());
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

    @FunctionalInterface
    private interface PageAppender<T> {
        int append(int page, List<T> out) throws IOException, InterruptedException;
    }
}
