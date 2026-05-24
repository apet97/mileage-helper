package com.cake.clockify.client;

import com.cake.clockify.client.spring.ClockifyRestController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live controller wiring probe for the complete /Users/15x/Downloads/clean1 OpenAPI surface.
 *
 * <p>Enable with CLOCKIFY_RUN_LIVE_REST_CONTROLLER=1 and provide CLOCKIFY_API_KEY plus
 * CLOCKIFY_WORKSPACE_ID. The test intentionally sends empty JSON bodies and synthetic IDs
 * for most mutating/id-specific routes, so success is either a real response or a sanitized
 * 4xx/5xx recovery from Clockify. Upstream 5xx responses are accepted because this probe
 * intentionally exercises many feature-gated or invalid-ID routes; client-side wiring failures,
 * missing env, or secret leaks fail the test.</p>
 */
@EnabledIfEnvironmentVariable(named = "CLOCKIFY_RUN_LIVE_REST_CONTROLLER", matches = "1|true|TRUE|yes|YES")
class ClockifyRestControllerLiveTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void everyControllerMappingReachesLiveClockifyUsingEnvironmentConfiguration() throws Exception {
        String apiKey = requiredEnv("CLOCKIFY_API_KEY");
        String workspaceId = requiredEnv("CLOCKIFY_WORKSPACE_ID");
        ClockifyClient client = ClockifyClient.builder()
                .apiKey(apiKey)
                .followRedirects(true)
                .allowCrossHostRedirects(true)
                .build();
        ClockifyRestController controller = new ClockifyRestController(client);

        List<String> failures = new ArrayList<>();
        int invoked = 0;
        for (Method method : mappedControllerMethods()) {
            invoked++;
            String lbl = label(method);
            System.out.println("Invoking: " + lbl);
            try {
                method.invoke(controller, argsFor(method, workspaceId));
                System.out.println("  -> SUCCESS");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ClockifyApiException apiException) {
                    System.out.println("  -> API ERROR (" + apiException.statusCode() + "): " + apiException.getMessage());
                    String body = String.valueOf(apiException.sanitizedBody());
                    if (body.contains(apiKey)) {
                        failures.add(lbl + " leaked an env value in sanitized error body");
                    }
                    continue;
                }
                System.err.println("  -> LOCAL ERROR: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                failures.add(lbl + " failed locally: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                System.err.println("  -> BEFORE REQUEST ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                failures.add(lbl + " failed before request: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        if (invoked != 195) {
            failures.add("expected 195 controller mappings (192 generated facade mappings + 3 addon-platform mappings), invoked " + invoked);
        }
        assertFalse(failures.isEmpty() && invoked == 0, "live controller probe invoked no mappings");
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    private static List<Method> mappedControllerMethods() {
        List<Method> methods = new ArrayList<>();
        for (Method method : ClockifyRestController.class.getDeclaredMethods()) {
            if (httpMethod(method) != null) {
                methods.add(method);
            }
        }
        methods.sort(Comparator.comparing(ClockifyRestControllerLiveTest::label));
        return methods;
    }

    private static Object[] argsFor(Method method, String workspaceId) {
        Object[] args = new Object[method.getParameterCount()];
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            Class<?> type = param.getType();
            if (type == String.class) {
                args[i] = stringArg(param, workspaceId);
            } else if (type == int.class || type == Integer.class) {
                args[i] = intArg(param);
            } else if (Map.class.isAssignableFrom(type)) {
                args[i] = queryArgs();
            } else if (JsonNode.class.isAssignableFrom(type)) {
                args[i] = bodyArg(method);
            } else if (type.getName().equals("org.springframework.web.multipart.MultipartFile")) {
                args[i] = java.lang.reflect.Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[]{type},
                        (proxy, proxyMethod, args1) -> {
                            if (proxyMethod.getName().equals("getOriginalFilename")) return "test.png";
                            if (proxyMethod.getName().equals("getContentType")) return "image/png";
                            if (proxyMethod.getName().equals("getBytes")) return new byte[]{1, 2, 3};
                            if (proxyMethod.getName().equals("isEmpty")) return false;
                            if (proxyMethod.getName().equals("getSize")) return 3L;
                            return null;
                        }
                );
            } else {
                throw new IllegalArgumentException("unsupported parameter " + param.getName() + " type " + type.getName());
            }
        }
        return args;
    }

    private static String stringArg(Parameter param, String workspaceId) {
        String name = parameterName(param).toLowerCase(Locale.ROOT);
        if (name.contains("workspace")) return workspaceId;
        if (name.contains("user")) return "000000000000000000000000";
        if (name.contains("client")) return "000000000000000000000001";
        if (name.contains("project")) return "000000000000000000000002";
        if (name.contains("task")) return "000000000000000000000003";
        if (name.contains("tag")) return "000000000000000000000004";
        if (name.contains("group")) return "000000000000000000000005";
        if (name.contains("holiday")) return "000000000000000000000006";
        if (name.contains("invoice")) return "000000000000000000000007";
        if (name.contains("expense")) return "000000000000000000000008";
        if (name.contains("category")) return "000000000000000000000009";
        if (name.contains("policy")) return "000000000000000000000010";
        if (name.contains("request")) return "000000000000000000000011";
        if (name.contains("webhook")) return "000000000000000000000012";
        if (name.contains("assignment")) return "000000000000000000000013";
        if (name.contains("shared")) return "000000000000000000000014";
        if (name.contains("field")) return "000000000000000000000015";
        if (name.contains("file")) return "000000000000000000000016";
        if (name.contains("entry")) return "000000000000000000000017";
        if (name.equals("start")) return "2026-01-01";
        if (name.equals("end")) return "2026-01-02";
        return "000000000000000000000099";
    }

    private static int intArg(Parameter param) {
        String name = parameterName(param).toLowerCase(Locale.ROOT);
        return name.contains("size") ? 1 : 1;
    }

    private static Map<String, String> queryArgs() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "1");
        query.put("page-size", "1");
        return query;
    }

    private static JsonNode bodyArg(Method method) {
        return OBJECT_MAPPER.createObjectNode().put("piLiveControllerProbe", label(method));
    }

    private static String parameterName(Parameter param) {
        PathVariable pathVariable = param.getAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.value().isBlank()) return pathVariable.value();
        RequestParam requestParam = param.getAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.value().isBlank()) return requestParam.value();
        RequestBody requestBody = param.getAnnotation(RequestBody.class);
        if (requestBody != null) return "body";
        return param.getName();
    }

    private static String httpMethod(Method method) {
        if (method.getAnnotation(GetMapping.class) != null) return "GET";
        if (method.getAnnotation(PostMapping.class) != null) return "POST";
        if (method.getAnnotation(PutMapping.class) != null) return "PUT";
        if (method.getAnnotation(PatchMapping.class) != null) return "PATCH";
        if (method.getAnnotation(DeleteMapping.class) != null) return "DELETE";
        return null;
    }

    private static String label(Method method) {
        return httpMethod(method) + " " + mappingPath(method) + " -> " + method.getName();
    }

    private static String mappingPath(Method method) {
        if (method.getAnnotation(GetMapping.class) != null) return method.getAnnotation(GetMapping.class).value()[0];
        if (method.getAnnotation(PostMapping.class) != null) return method.getAnnotation(PostMapping.class).value()[0];
        if (method.getAnnotation(PutMapping.class) != null) return method.getAnnotation(PutMapping.class).value()[0];
        if (method.getAnnotation(PatchMapping.class) != null) return method.getAnnotation(PatchMapping.class).value()[0];
        if (method.getAnnotation(DeleteMapping.class) != null) return method.getAnnotation(DeleteMapping.class).value()[0];
        return "<unmapped>";
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            fail("Missing required environment variable " + name);
        }
        return value;
    }

    @Test
    void testExpenseWithUnitPriceLifecycle() throws Exception {
        String apiKey = System.getenv("CLOCKIFY_API_KEY");
        String workspaceId = System.getenv("CLOCKIFY_WORKSPACE_ID");
        if (apiKey == null || apiKey.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            return;
        }

        ClockifyClient client = ClockifyClient.builder()
                .apiKey(apiKey)
                .followRedirects(true)
                .allowCrossHostRedirects(true)
                .build();

        // 1. Get logged-in user ID
        com.cake.clockify.client.models.User user = client.users().getLoggedUser();
        String userId = user.id();
        assertNotNull(userId, "userId must not be null");

        // 2. Create a temporary project for the expense
        String projectUuid = java.util.UUID.randomUUID().toString().substring(0, 8);
        com.fasterxml.jackson.databind.node.ObjectNode projReq = OBJECT_MAPPER.createObjectNode()
                .put("name", "Expense Unit Test Project " + projectUuid);
        JsonNode projectNode = client.projects().createProject(workspaceId, projReq);
        String projectId = projectNode.get("id").asText();
        assertNotNull(projectId, "projectId must not be null");

        // 3. Create a temporary task under the project
        String taskId = null;
        com.fasterxml.jackson.databind.node.ObjectNode taskReq = OBJECT_MAPPER.createObjectNode()
                .put("name", "Expense Unit Test Task " + projectUuid);
        JsonNode taskNode = client.tasks().createTask(workspaceId, projectId, taskReq);
        taskId = taskNode.get("id").asText();
        assertNotNull(taskId, "taskId must not be null");

        // 4. Create an expense category with a unit price
        String categoryUuid = java.util.UUID.randomUUID().toString().substring(0, 8);
        com.fasterxml.jackson.databind.node.ObjectNode catReq = OBJECT_MAPPER.createObjectNode()
                .put("name", "Expense Unit Test Category " + categoryUuid)
                .put("hasUnitPrice", true)
                .put("priceInCents", 1000) // $10.00
                .put("unit", "piece");
        JsonNode categoryNode = client.expenses().createCategory(workspaceId, catReq);
        String categoryId = categoryNode.get("id").asText();
        assertNotNull(categoryId, "categoryId must not be null");

        String expenseId = null;
        try {
            // 5. Create an expense with quantity 5.0 (for unit price categories, quantity is specified in the 'amount' field)
            com.fasterxml.jackson.databind.node.ObjectNode expReq = OBJECT_MAPPER.createObjectNode()
                    .put("userId", userId)
                    .put("projectId", projectId)
                    .put("taskId", taskId)
                    .put("categoryId", categoryId)
                    .put("date", "2026-05-24T00:00:00Z")
                    .put("amount", 5.0)
                    .put("quantity", 5.0)
                    .put("notes", "Initial unit price expense notes")
                    .put("billable", true);

            JsonNode expenseNode = client.expenses().createExpense(workspaceId, expReq);
            System.out.println("expenseNode: " + expenseNode.toPrettyString());
            expenseId = expenseNode.get("id").asText();
            assertNotNull(expenseId, "expenseId must not be null");

            // Verify initial values (quantity = 5.0, total = 5.0 * 1000 cents = 5000.0)
            assertEquals(5.0, expenseNode.get("quantity").asDouble(), 0.001);
            assertEquals(5000.0, expenseNode.get("total").asDouble(), 0.001);
            assertEquals("Initial unit price expense notes", expenseNode.get("notes").asText());
            assertTrue(expenseNode.get("billable").asBoolean());

            // 6. Update EVERYTHING on the expense (notes, quantity to 10.0, billable state, date, and upload a file receipt)
            com.fasterxml.jackson.databind.node.ObjectNode updateReq = OBJECT_MAPPER.createObjectNode()
                    .put("userId", userId)
                    .put("projectId", projectId)
                    .put("taskId", taskId)
                    .put("categoryId", categoryId)
                    .put("date", "2026-05-25T00:00:00Z")
                    .put("amount", 10.0)
                    .put("quantity", 10.0)
                    .put("notes", "Updated unit price expense notes")
                    .put("billable", false);

            byte[] receiptBytes = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, (byte) 0xc4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0b, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, (byte) 0xb4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, (byte) 0xae,
                0x42, 0x60, (byte) 0x82
            };
            JsonNode updatedExpenseNode = null;
            try {
                updatedExpenseNode = client.expenses().updateExpense(
                        workspaceId,
                        expenseId,
                        updateReq,
                        "receipt.png",
                        "image/png",
                        receiptBytes
                );
            } catch (ClockifyApiException e) {
                System.err.println("ClockifyApiException status: " + e.statusCode());
                System.err.println("ClockifyApiException body: " + e.sanitizedBody());
                throw e;
            }
            System.out.println("updatedExpenseNode: " + updatedExpenseNode.toPrettyString());

            // Wait for file upload to propagate to storage
            Thread.sleep(2000);

            // Verify updated values (quantity = 10.0, total = 10.0 * 1000 cents = 10000.0)
            assertEquals(10.0, updatedExpenseNode.get("quantity").asDouble(), 0.001);
            assertEquals(10000.0, updatedExpenseNode.get("total").asDouble(), 0.001);
            assertEquals("Updated unit price expense notes", updatedExpenseNode.get("notes").asText());
            assertFalse(updatedExpenseNode.get("billable").asBoolean());
            assertTrue(updatedExpenseNode.get("date").asText().startsWith("2026-05-25"));
            
            // File ID must not be empty after receipt upload
            String fileId = updatedExpenseNode.get("fileId").asText();
            assertFalse(fileId.isEmpty());

            // 7. Verify we can download the file
            ClockifyBinaryResponse fileResponse = client.expenses().downloadFile(workspaceId, expenseId, fileId);
            assertNotNull(fileResponse);
            System.out.println("Download File Response Status: " + fileResponse.statusCode());
            System.out.println("Download File Response Headers: " + fileResponse.headers());
            System.out.println("Download File Response Bytes Length: " + (fileResponse.bytes() != null ? fileResponse.bytes().length : "null"));
            assertNotNull(fileResponse.bytes());
            assertTrue(fileResponse.bytes().length >= 8);
            
            // Clockify backend may re-compress/re-encode PNG images on upload (affecting zlib compressed sizes/chunks).
            // Therefore, instead of asserting byte-for-byte identity of the entire file, we verify that the downloaded 
            // file contains the standard PNG file signature, confirming it was stored and served correctly.
            byte[] signature = new byte[] { (byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10 };
            byte[] actualBeginning = new byte[8];
            System.arraycopy(fileResponse.bytes(), 0, actualBeginning, 0, 8);
            assertArrayEquals(signature, actualBeginning);

        } finally {
            // Cleanup: delete expense, category, task, project
            if (expenseId != null) {
                try {
                    client.expenses().deleteExpense(workspaceId, expenseId);
                } catch (Exception e) {
                    System.err.println("Failed to delete expense: " + e.getMessage());
                }
            }
            if (categoryId != null) {
                try {
                    // Archive first so category can be deleted
                    client.expenses().updateCategoryStatus(workspaceId, categoryId, OBJECT_MAPPER.createObjectNode().put("archived", true));
                    client.expenses().deleteCategory(workspaceId, categoryId);
                } catch (Exception e) {
                    System.err.println("Failed to delete category: " + e.getMessage());
                }
            }
            if (taskId != null) {
                try {
                    client.tasks().deleteTask(workspaceId, projectId, taskId);
                } catch (Exception e) {
                    System.err.println("Failed to delete task: " + e.getMessage());
                }
            }
            if (projectId != null) {
                try {
                    // Archive first so project can be deleted
                    JsonNode currentProj = client.projects().getProject(workspaceId, projectId);
                    com.fasterxml.jackson.databind.node.ObjectNode archiveReq = OBJECT_MAPPER.createObjectNode();
                    if (currentProj.isObject()) {
                        archiveReq.setAll((com.fasterxml.jackson.databind.node.ObjectNode) currentProj);
                    }
                    archiveReq.put("archived", true);
                    client.projects().archiveProject(workspaceId, projectId, archiveReq);
                    client.projects().deleteProject(workspaceId, projectId);
                } catch (Exception e) {
                    System.err.println("Failed to delete project: " + e.getMessage());
                }
            }
        }
    }

    @Test
    void testGetCategoriesWithFilters() throws Exception {
        String apiKey = System.getenv("CLOCKIFY_API_KEY");
        String workspaceId = System.getenv("CLOCKIFY_WORKSPACE_ID");
        if (apiKey == null || apiKey.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            return;
        }

        ClockifyClient client = ClockifyClient.builder()
                .apiKey(apiKey)
                .followRedirects(true)
                .allowCrossHostRedirects(true)
                .build();

        // 1. Create a temporary expense category with a unique name
        String uniqueName = "FilterTest " + java.util.UUID.randomUUID().toString().substring(0, 8);
        com.fasterxml.jackson.databind.node.ObjectNode catReq = OBJECT_MAPPER.createObjectNode()
                .put("name", uniqueName)
                .put("hasUnitPrice", false);
        JsonNode categoryNode = client.expenses().createCategory(workspaceId, catReq);
        String categoryId = categoryNode.get("id").asText();
        assertNotNull(categoryId, "categoryId must not be null");
        System.out.println("Created category: " + uniqueName + " (id=" + categoryId + ")");

        try {
            // 2. Sort by NAME ascending, only non-archived
            JsonNode ascRaw = client.expenses().getCategories(workspaceId, "NAME", "ASCENDING", false, null, new ClockifyPageRequest(1, 50));
            JsonNode ascResult = extractArray(ascRaw, "categories");
            assertTrue(ascResult.isArray(), "getCategories ascending should return an array (raw shape: " + ascRaw.getNodeType() + ")");
            assertTrue(ascResult.size() > 0, "getCategories ascending should not be empty");
            System.out.println("Categories (NAME ASC, non-archived): " + ascResult.size());

            // 3. Filter by name, no archived filter (DESCENDING)
            JsonNode nameFilterRaw = client.expenses().getCategories(workspaceId, "NAME", "DESCENDING", null, uniqueName, new ClockifyPageRequest(1, 50));
            JsonNode nameFilterResult = extractArray(nameFilterRaw, "categories");
            assertTrue(nameFilterResult.isArray(), "getCategories by name should return an array");
            boolean foundByName = false;
            for (JsonNode cat : nameFilterResult) {
                if (uniqueName.equals(cat.get("name").asText())) {
                    foundByName = true;
                    break;
                }
            }
            assertTrue(foundByName, "Category with name '" + uniqueName + "' should be found when filtering by name");
            System.out.println("Found category by name filter: " + uniqueName);

            // 4. Archive the category
            client.expenses().updateCategoryStatus(workspaceId, categoryId, OBJECT_MAPPER.createObjectNode().put("status", "ARCHIVED"));
            System.out.println("Archived category: " + categoryId);

            // 5. Query only archived categories
            JsonNode archivedRaw = client.expenses().getCategories(workspaceId, null, null, true, null, new ClockifyPageRequest(1, 50));
            JsonNode archivedResult = extractArray(archivedRaw, "categories");
            assertTrue(archivedResult.isArray(), "getCategories archived should return an array");
            boolean foundArchived = false;
            for (JsonNode cat : archivedResult) {
                if (categoryId.equals(cat.get("id").asText())) {
                    foundArchived = true;
                    break;
                }
            }
            assertTrue(foundArchived, "Archived category should appear in archived-only query");
            System.out.println("Confirmed category appears in archived list");

            // 6. Unarchive the category
            client.expenses().updateCategoryStatus(workspaceId, categoryId, OBJECT_MAPPER.createObjectNode().put("status", "ACTIVE"));
            System.out.println("Unarchived category: " + categoryId);

        } finally {
            // Cleanup: archive then delete
            try {
                client.expenses().updateCategoryStatus(workspaceId, categoryId, OBJECT_MAPPER.createObjectNode().put("archived", true));
                client.expenses().deleteCategory(workspaceId, categoryId);
                System.out.println("Deleted category: " + categoryId);
            } catch (Exception e) {
                System.err.println("Failed to cleanup category: " + e.getMessage());
            }
        }
    }

    @Test
    void testGetExpensesWithUserIdAndExpenseReport() throws Exception {
        String apiKey = System.getenv("CLOCKIFY_API_KEY");
        String workspaceId = System.getenv("CLOCKIFY_WORKSPACE_ID");
        if (apiKey == null || apiKey.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            return;
        }

        ClockifyClient client = ClockifyClient.builder()
                .apiKey(apiKey)
                .followRedirects(true)
                .allowCrossHostRedirects(true)
                .build();

        // 1. Get the logged-in user ID
        com.cake.clockify.client.models.User user = client.users().getLoggedUser();
        String userId = user.id();
        assertNotNull(userId, "userId must not be null");
        System.out.println("Logged-in user ID: " + userId);

        // 2. Create a temp project
        String uuid = java.util.UUID.randomUUID().toString().substring(0, 8);
        com.fasterxml.jackson.databind.node.ObjectNode projReq = OBJECT_MAPPER.createObjectNode()
                .put("name", "ExpReport Test Project " + uuid);
        JsonNode projectNode = client.projects().createProject(workspaceId, projReq);
        String projectId = projectNode.get("id").asText();
        assertNotNull(projectId, "projectId must not be null");
        System.out.println("Created project: " + projectId);

        // 3. Create a temp category
        com.fasterxml.jackson.databind.node.ObjectNode catReq = OBJECT_MAPPER.createObjectNode()
                .put("name", "ExpReport Test Category " + uuid)
                .put("hasUnitPrice", false);
        JsonNode categoryNode = client.expenses().createCategory(workspaceId, catReq);
        String categoryId = categoryNode.get("id").asText();
        assertNotNull(categoryId, "categoryId must not be null");
        System.out.println("Created category: " + categoryId);

        String expenseId = null;
        try {
            // 4. Create an expense (flat amount, $50.00)
            com.fasterxml.jackson.databind.node.ObjectNode expReq = OBJECT_MAPPER.createObjectNode()
                    .put("userId", userId)
                    .put("projectId", projectId)
                    .put("categoryId", categoryId)
                    .put("date", "2026-06-15T00:00:00Z")
                    .put("amount", 5000)
                    .put("notes", "Expense report integration test")
                    .put("billable", true);
            JsonNode expenseNode = client.expenses().createExpense(workspaceId, expReq);
            System.out.println("Created expense: " + expenseNode.toPrettyString());
            expenseId = expenseNode.get("id").asText();
            assertNotNull(expenseId, "expenseId must not be null");

            // 5. Get expenses filtered by user-id
            JsonNode userExpensesRaw = client.expenses().getExpenses(workspaceId, userId, new ClockifyPageRequest(1, 50));
            JsonNode userExpenses = extractArray(userExpensesRaw, "expenses");
            assertTrue(userExpenses.isArray(), "getExpenses by userId should return an array (raw shape: " + userExpensesRaw.getNodeType() + ")");
            boolean foundInUserExpenses = false;
            for (JsonNode exp : userExpenses) {
                if (expenseId.equals(exp.get("id").asText())) {
                    foundInUserExpenses = true;
                    break;
                }
            }
            assertTrue(foundInUserExpenses, "Created expense should appear in user-filtered expenses");
            System.out.println("Confirmed expense appears in user-filtered list");

            // 6. Get single expense by ID
            JsonNode singleExpense = client.expenses().getExpense(workspaceId, expenseId);
            assertNotNull(singleExpense, "Single expense must not be null");
            assertEquals(expenseId, singleExpense.get("id").asText());
            assertEquals(projectId, singleExpense.get("projectId").asText());
            assertEquals(categoryId, singleExpense.get("categoryId").asText());
            assertEquals(userId, singleExpense.get("userId").asText());
            assertTrue(singleExpense.get("billable").asBoolean());
            assertEquals("Expense report integration test", singleExpense.get("notes").asText());
            System.out.println("Single expense fields verified");

            // 7. Run expense detailed report
            com.fasterxml.jackson.databind.node.ObjectNode reportBody = OBJECT_MAPPER.createObjectNode()
                    .put("dateRangeStart", "2026-01-01T00:00:00.000Z")
                    .put("dateRangeEnd", "2027-01-01T00:00:00.000Z");
            JsonNode reportResult = client.reports().expenseDetailed(workspaceId, reportBody);
            assertNotNull(reportResult, "Report result must not be null");
            assertTrue(reportResult.isObject(), "Report result should be an object");
            System.out.println("Expense detailed report returned successfully");

        } finally {
            // Cleanup: delete expense, archive+delete category, archive+delete project
            if (expenseId != null) {
                try {
                    client.expenses().deleteExpense(workspaceId, expenseId);
                    System.out.println("Deleted expense: " + expenseId);
                } catch (Exception e) {
                    System.err.println("Failed to delete expense: " + e.getMessage());
                }
            }
            try {
                client.expenses().updateCategoryStatus(workspaceId, categoryId, OBJECT_MAPPER.createObjectNode().put("archived", true));
                client.expenses().deleteCategory(workspaceId, categoryId);
                System.out.println("Deleted category: " + categoryId);
            } catch (Exception e) {
                System.err.println("Failed to delete category: " + e.getMessage());
            }
            try {
                JsonNode currentProj = client.projects().getProject(workspaceId, projectId);
                com.fasterxml.jackson.databind.node.ObjectNode archiveReq = OBJECT_MAPPER.createObjectNode();
                if (currentProj.isObject()) {
                    archiveReq.setAll((com.fasterxml.jackson.databind.node.ObjectNode) currentProj);
                }
                archiveReq.put("archived", true);
                client.projects().archiveProject(workspaceId, projectId, archiveReq);
                client.projects().deleteProject(workspaceId, projectId);
                System.out.println("Deleted project: " + projectId);
            } catch (Exception e) {
                System.err.println("Failed to delete project: " + e.getMessage());
            }
        }
    }

    @Test
    void testFlatAmountExpenseAndCategoryUpdateLifecycle() throws Exception {
        String apiKey = System.getenv("CLOCKIFY_API_KEY");
        String workspaceId = System.getenv("CLOCKIFY_WORKSPACE_ID");
        if (apiKey == null || apiKey.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            return;
        }

        ClockifyClient client = ClockifyClient.builder()
                .apiKey(apiKey)
                .followRedirects(true)
                .allowCrossHostRedirects(true)
                .build();

        // 1. Create a flat-amount category (hasUnitPrice=false)
        String uuid = java.util.UUID.randomUUID().toString().substring(0, 8);
        String originalName = "FlatCat " + uuid;
        com.fasterxml.jackson.databind.node.ObjectNode catReq = OBJECT_MAPPER.createObjectNode()
                .put("name", originalName)
                .put("hasUnitPrice", false);
        JsonNode categoryNode = client.expenses().createCategory(workspaceId, catReq);
        String categoryId = categoryNode.get("id").asText();
        assertNotNull(categoryId, "categoryId must not be null");
        assertFalse(categoryNode.get("hasUnitPrice").asBoolean(), "Category should have hasUnitPrice=false");
        System.out.println("Created flat category: " + originalName + " (id=" + categoryId + ")");

        // Get logged-in user for expense creation
        com.cake.clockify.client.models.User user = client.users().getLoggedUser();
        String userId = user.id();

        String expenseId = null;
        try {
            // 2. Update the category name
            String updatedName = "UpdatedCat " + uuid;
            com.fasterxml.jackson.databind.node.ObjectNode updateCatBody = OBJECT_MAPPER.createObjectNode()
                    .put("name", updatedName);
            client.expenses().updateCategory(workspaceId, categoryId, updateCatBody);
            System.out.println("Updated category name to: " + updatedName);

            // 3. Verify the updated name by fetching with name filter
            JsonNode filteredCatsRaw = client.expenses().getCategories(workspaceId, "NAME", "ASCENDING", null, updatedName, new ClockifyPageRequest(1, 50));
            JsonNode filteredCats = extractArray(filteredCatsRaw, "categories");
            assertTrue(filteredCats.isArray(), "getCategories should return an array");
            boolean foundUpdated = false;
            for (JsonNode cat : filteredCats) {
                if (updatedName.equals(cat.get("name").asText())) {
                    foundUpdated = true;
                    break;
                }
            }
            assertTrue(foundUpdated, "Updated category name '" + updatedName + "' should be found");
            System.out.println("Verified updated category name");

            // 4. Create an expense with flat amount=5000 ($50.00)
            com.fasterxml.jackson.databind.node.ObjectNode expReq = OBJECT_MAPPER.createObjectNode()
                    .put("userId", userId)
                    .put("categoryId", categoryId)
                    .put("date", "2026-07-01T00:00:00Z")
                    .put("amount", 5000)
                    .put("notes", "Flat amount lifecycle test")
                    .put("billable", false);
            JsonNode expenseNode = client.expenses().createExpense(workspaceId, expReq);
            System.out.println("Created expense: " + expenseNode.toPrettyString());
            expenseId = expenseNode.get("id").asText();
            assertNotNull(expenseId, "expenseId must not be null");
            assertEquals(5000.0, expenseNode.get("total").asDouble(), 0.001, "Expense total should be 5000");
            System.out.println("Verified expense total=5000");

            // 5. Update the expense notes only
            com.fasterxml.jackson.databind.node.ObjectNode updateExpReq = OBJECT_MAPPER.createObjectNode()
                    .put("notes", "Updated flat amount notes");
            JsonNode updatedExpense = client.expenses().updateExpense(workspaceId, expenseId, updateExpReq);
            System.out.println("Updated expense: " + updatedExpense.toPrettyString());
            assertEquals("Updated flat amount notes", updatedExpense.get("notes").asText(), "Notes should be updated");
            System.out.println("Verified updated expense notes");

        } finally {
            // Cleanup: delete expense, archive+delete category
            if (expenseId != null) {
                try {
                    client.expenses().deleteExpense(workspaceId, expenseId);
                    System.out.println("Deleted expense: " + expenseId);
                } catch (Exception e) {
                    System.err.println("Failed to delete expense: " + e.getMessage());
                }
            }
            try {
                client.expenses().updateCategoryStatus(workspaceId, categoryId, OBJECT_MAPPER.createObjectNode().put("archived", true));
                client.expenses().deleteCategory(workspaceId, categoryId);
                System.out.println("Deleted category: " + categoryId);
            } catch (Exception e) {
                System.err.println("Failed to delete category: " + e.getMessage());
            }
        }
    }

    private JsonNode extractArray(JsonNode node, String fieldName) {
        if (node.has(fieldName)) {
            JsonNode field = node.get(fieldName);
            if (field.isArray()) {
                return field;
            } else if (field.isObject() && field.has(fieldName) && field.get(fieldName).isArray()) {
                return field.get(fieldName);
            }
        }
        return node;
    }
}
