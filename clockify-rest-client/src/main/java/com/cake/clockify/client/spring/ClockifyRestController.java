package com.cake.clockify.client.spring;

import com.cake.clockify.client.ClockifyBinaryResponse;
import com.cake.clockify.client.ClockifyClient;
import com.cake.clockify.client.ClockifyPageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

/**
 * Thin Spring MVC controller facade over the typed {@link ClockifyClient} methods.
 *
 * <p>This class intentionally contains no Clockify business logic. It exposes only
 * endpoints that already exist as typed client methods and delegates request bodies
 * through as {@link JsonNode} while field-level model provenance is still evolving.</p>
 */
@RestController
@ConditionalOnProperty(prefix = "clockify.rest-controller", name = "enabled", havingValue = "true")
@RequestMapping("/clockify")
public class ClockifyRestController {
    private final ClockifyClient clockifyClient;

    public ClockifyRestController(ClockifyClient clockifyClient) {
        this.clockifyClient = Objects.requireNonNull(clockifyClient, "clockifyClient");
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @GetMapping("/user")
    public Object getLoggedUser() throws IOException, InterruptedException {
        return clockifyClient.users().getLoggedUser();
    }

    @GetMapping("/workspaces/{workspaceId}/users")
    public Object getUsersOfWorkspace(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.users().getUsersOfWorkspace(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/users/info")
    public Object filterUsersOfWorkspace(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().filterUsersOfWorkspace(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/member-profile/{userId}")
    public Object getMemberProfile(@PathVariable String workspaceId, @PathVariable String userId) throws IOException, InterruptedException {
        return clockifyClient.users().getMemberProfile(workspaceId, userId);
    }

    @GetMapping("/workspaces/{workspaceId}/users/{userId}/managers")
    public Object getManagersOfUser(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.users().getManagersOfUser(workspaceId, userId, new ClockifyPageRequest(page, pageSize));
    }

    // ── Workspaces ───────────────────────────────────────────────────────────

    @GetMapping("/workspaces")
    public Object getWorkspacesOfUser() throws IOException, InterruptedException {
        return clockifyClient.workspaces().getWorkspacesOfUser();
    }

    @GetMapping("/workspaces/{workspaceId}")
    public Object getWorkspaceOfUser(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.workspaces().getWorkspaceOfUser(workspaceId);
    }

    // ── Clients ──────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/clients")
    public Object getClients(@PathVariable String workspaceId, @RequestParam(defaultValue = "1") int page,
                               @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.clients().getClients(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/clients")
    public Object createClient(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.clients().createClient(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/clients/{clientId}")
    public Object getClient(@PathVariable String workspaceId, @PathVariable String clientId) throws IOException, InterruptedException {
        return clockifyClient.clients().getClient(workspaceId, clientId);
    }

    @PutMapping("/workspaces/{workspaceId}/clients/{clientId}")
    public Object updateClient(@PathVariable String workspaceId, @PathVariable String clientId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.clients().updateClient(workspaceId, clientId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/clients/{clientId}")
    public ResponseEntity<Void> deleteClient(@PathVariable String workspaceId, @PathVariable String clientId) throws IOException, InterruptedException {
        clockifyClient.clients().deleteClient(workspaceId, clientId);
        return ResponseEntity.noContent().build();
    }

    // ── Projects ─────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/projects")
    public Object getProjects(@PathVariable String workspaceId, @RequestParam(defaultValue = "1") int page,
                                @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.projects().getProjects(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/projects")
    public Object createProject(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().createProject(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}")
    public Object getProject(@PathVariable String workspaceId, @PathVariable String projectId) throws IOException, InterruptedException {
        return clockifyClient.projects().getProject(workspaceId, projectId);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}")
    public Object updateProject(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateProject(workspaceId, projectId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable String workspaceId, @PathVariable String projectId) throws IOException, InterruptedException {
        clockifyClient.projects().deleteProject(workspaceId, projectId);
        return ResponseEntity.noContent().build();
    }

    // ── Tags ─────────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/tags")
    public Object getTags(@PathVariable String workspaceId, @RequestParam(defaultValue = "1") int page,
                            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.tags().getTags(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/tags")
    public Object createTag(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.tags().createTag(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/tags/{tagId}")
    public Object getTag(@PathVariable String workspaceId, @PathVariable String tagId) throws IOException, InterruptedException {
        return clockifyClient.tags().getTag(workspaceId, tagId);
    }

    @PutMapping("/workspaces/{workspaceId}/tags/{tagId}")
    public Object updateTag(@PathVariable String workspaceId, @PathVariable String tagId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.tags().updateTag(workspaceId, tagId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/tags/{tagId}")
    public ResponseEntity<Void> deleteTag(@PathVariable String workspaceId, @PathVariable String tagId) throws IOException, InterruptedException {
        clockifyClient.tags().deleteTag(workspaceId, tagId);
        return ResponseEntity.noContent().build();
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks")
    public Object getTasks(@PathVariable String workspaceId, @PathVariable String projectId, @RequestParam(defaultValue = "1") int page,
                             @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.tasks().getTasks(workspaceId, projectId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks")
    public Object createTask(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.tasks().createTask(workspaceId, projectId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}")
    public Object getTask(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String taskId) throws IOException, InterruptedException {
        return clockifyClient.tasks().getTask(workspaceId, projectId, taskId);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}")
    public Object updateTask(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String taskId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.tasks().updateTask(workspaceId, projectId, taskId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String taskId) throws IOException, InterruptedException {
        clockifyClient.tasks().deleteTask(workspaceId, projectId, taskId);
        return ResponseEntity.noContent().build();
    }

    // ── Time Entries ──────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/user/{userId}/time-entries")
    public Object getTimeEntries(@PathVariable String workspaceId, @PathVariable String userId, @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().getTimeEntries(workspaceId, userId, new ClockifyPageRequest(page, pageSize));
    }

    @GetMapping("/workspaces/{workspaceId}/time-entries/status/in-progress")
    public Object getInProgressTimeEntries(@PathVariable String workspaceId, @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().getInProgressTimeEntries(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/time-entries")
    public Object createTimeEntry(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().createTimeEntry(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/time-entries/{timeEntryId}")
    public Object getTimeEntry(@PathVariable String workspaceId, @PathVariable String timeEntryId) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().getTimeEntry(workspaceId, timeEntryId);
    }

    @PutMapping("/workspaces/{workspaceId}/time-entries/{timeEntryId}")
    public Object updateTimeEntry(@PathVariable String workspaceId, @PathVariable String timeEntryId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().updateTimeEntry(workspaceId, timeEntryId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/time-entries/{timeEntryId}")
    public ResponseEntity<Void> deleteTimeEntry(@PathVariable String workspaceId, @PathVariable String timeEntryId) throws IOException, InterruptedException {
        clockifyClient.timeEntries().deleteTimeEntry(workspaceId, timeEntryId);
        return ResponseEntity.noContent().build();
    }

    // ── User Groups ───────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/user-groups")
    public Object getUserGroups(@PathVariable String workspaceId, @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.userGroups().getUserGroups(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/user-groups")
    public Object createUserGroup(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.userGroups().createUserGroup(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/user-groups/{groupId}")
    public Object updateUserGroup(@PathVariable String workspaceId, @PathVariable String groupId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.userGroups().updateUserGroup(workspaceId, groupId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/user-groups/{groupId}")
    public ResponseEntity<Void> deleteUserGroup(@PathVariable String workspaceId, @PathVariable String groupId) throws IOException, InterruptedException {
        clockifyClient.userGroups().deleteUserGroup(workspaceId, groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workspaces/{workspaceId}/user-groups/{groupId}/users")
    public Object addUserToGroup(@PathVariable String workspaceId, @PathVariable String groupId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.userGroups().addUser(workspaceId, groupId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/user-groups/{groupId}/users/{userId}")
    public ResponseEntity<Void> deleteUserFromGroup(@PathVariable String workspaceId, @PathVariable String groupId, @PathVariable String userId) throws IOException, InterruptedException {
        clockifyClient.userGroups().deleteUser(workspaceId, groupId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Holidays ──────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/holidays")
    public Object getHolidays(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.holidays().getHolidays(workspaceId);
    }

    @GetMapping("/workspaces/{workspaceId}/holidays/in-period")
    public Object getHolidaysInPeriod(@PathVariable String workspaceId, @RequestParam String start, @RequestParam String end) throws IOException, InterruptedException {
        return clockifyClient.holidays().getHolidaysInPeriod(workspaceId, start, end);
    }

    @PostMapping("/workspaces/{workspaceId}/holidays")
    public Object createHoliday(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.holidays().createHoliday(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/holidays/{holidayId}")
    public Object updateHoliday(@PathVariable String workspaceId, @PathVariable String holidayId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.holidays().updateHoliday(workspaceId, holidayId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/holidays/{holidayId}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable String workspaceId, @PathVariable String holidayId) throws IOException, InterruptedException {
        clockifyClient.holidays().deleteHoliday(workspaceId, holidayId);
        return ResponseEntity.noContent().build();
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    @PostMapping(value = "/file/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadImage(@RequestParam("file") MultipartFile file) throws IOException, InterruptedException {
        return clockifyClient.files().uploadImage(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes());
    }

    // ── Approvals ─────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/approval-requests")
    public Object getApprovalRequests(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.approvals().getApprovalRequests(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/approval-requests")
    public Object createApprovalRequest(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.approvals().createApprovalRequest(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/approval-requests/resubmit-entries-for-approval")
    public Object resubmitApprovalRequest(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.approvals().resubmitApprovalRequest(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/approval-requests/users/{userId}")
    public Object createApprovalForUser(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.approvals().createApprovalForOther(workspaceId, userId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/approval-requests/users/{userId}/resubmit-entries-for-approval")
    public Object resubmitApprovalRequestForUser(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.approvals().resubmitApprovalRequestForOther(workspaceId, userId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/approval-requests/{approvalRequestId}")
    public Object updateApprovalRequest(@PathVariable String workspaceId, @PathVariable String approvalRequestId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.approvals().updateApprovalStatus(workspaceId, approvalRequestId, body);
    }

    // ── Custom Fields ─────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/custom-fields")
    public Object getWorkspaceCustomFields(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.customFields().getCustomFields(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/custom-fields")
    public Object createWorkspaceCustomField(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.customFields().createCustomField(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/custom-fields/{customFieldId}")
    public Object updateWorkspaceCustomField(@PathVariable String workspaceId, @PathVariable String customFieldId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.customFields().updateCustomField(workspaceId, customFieldId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/custom-fields/{customFieldId}")
    public ResponseEntity<Void> deleteWorkspaceCustomField(@PathVariable String workspaceId, @PathVariable String customFieldId) throws IOException, InterruptedException {
        clockifyClient.customFields().deleteCustomField(workspaceId, customFieldId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/custom-fields")
    public Object getProjectCustomFields(@PathVariable String workspaceId, @PathVariable String projectId) throws IOException, InterruptedException {
        return clockifyClient.customFields().getProjectCustomFields(workspaceId, projectId);
    }

    @PatchMapping("/workspaces/{workspaceId}/projects/{projectId}/custom-fields/{customFieldId}")
    public Object updateProjectCustomField(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String customFieldId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.customFields().updateProjectCustomField(workspaceId, projectId, customFieldId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/projects/{projectId}/custom-fields/{customFieldId}")
    public ResponseEntity<Void> deleteProjectCustomField(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String customFieldId) throws IOException, InterruptedException {
        clockifyClient.customFields().deleteProjectCustomField(workspaceId, projectId, customFieldId);
        return ResponseEntity.noContent().build();
    }

    // ── Entity Changes ────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/entities/created")
    public Object getCreatedEntities(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) throws IOException, InterruptedException {
        return clockifyClient.entityChanges().created(workspaceId, page, limit);
    }

    @GetMapping("/workspaces/{workspaceId}/entities/updated")
    public Object getUpdatedEntities(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) throws IOException, InterruptedException {
        return clockifyClient.entityChanges().updated(workspaceId, page, limit);
    }

    @GetMapping("/workspaces/{workspaceId}/entities/deleted")
    public Object getDeletedEntities(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) throws IOException, InterruptedException {
        return clockifyClient.entityChanges().deleted(workspaceId, page, limit);
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/expenses")
    public Object getExpenses(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.expenses().getExpenses(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/expenses")
    public Object createExpense(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.expenses().createExpense(workspaceId, body);
    }

    @PostMapping(value = "/workspaces/{workspaceId}/expenses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object createExpenseMultipart(
            @PathVariable String workspaceId,
            @RequestPart("body") JsonNode body,
            @RequestPart("file") MultipartFile file) throws IOException, InterruptedException {
        return clockifyClient.expenses().createExpense(workspaceId, body, file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    @GetMapping("/workspaces/{workspaceId}/expenses/categories")
    public Object getExpenseCategories(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.expenses().getCategories(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/expenses/categories")
    public Object createExpenseCategory(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.expenses().createCategory(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/expenses/categories/{categoryId}")
    public Object updateExpenseCategory(@PathVariable String workspaceId, @PathVariable String categoryId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.expenses().updateCategory(workspaceId, categoryId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/expenses/categories/{categoryId}")
    public ResponseEntity<Void> deleteExpenseCategory(@PathVariable String workspaceId, @PathVariable String categoryId) throws IOException, InterruptedException {
        clockifyClient.expenses().deleteCategory(workspaceId, categoryId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/workspaces/{workspaceId}/expenses/categories/{categoryId}/status")
    public Object archiveExpenseCategory(@PathVariable String workspaceId, @PathVariable String categoryId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.expenses().updateCategoryStatus(workspaceId, categoryId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/expenses/{expenseId}")
    public Object getExpense(@PathVariable String workspaceId, @PathVariable String expenseId) throws IOException, InterruptedException {
        return clockifyClient.expenses().getExpense(workspaceId, expenseId);
    }

    @PutMapping("/workspaces/{workspaceId}/expenses/{expenseId}")
    public Object updateExpense(@PathVariable String workspaceId, @PathVariable String expenseId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.expenses().updateExpense(workspaceId, expenseId, body);
    }

    @PutMapping(value = "/workspaces/{workspaceId}/expenses/{expenseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object updateExpenseMultipart(
            @PathVariable String workspaceId,
            @PathVariable String expenseId,
            @RequestPart("body") JsonNode body,
            @RequestPart("file") MultipartFile file) throws IOException, InterruptedException {
        return clockifyClient.expenses().updateExpense(workspaceId, expenseId, body, file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    @DeleteMapping("/workspaces/{workspaceId}/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable String workspaceId, @PathVariable String expenseId) throws IOException, InterruptedException {
        clockifyClient.expenses().deleteExpense(workspaceId, expenseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workspaces/{workspaceId}/expenses/{expenseId}/files/{fileId}")
    public ResponseEntity<byte[]> downloadExpenseReceipt(@PathVariable String workspaceId, @PathVariable String expenseId, @PathVariable String fileId) throws IOException, InterruptedException {
        ClockifyBinaryResponse resp = clockifyClient.expenses().downloadFile(workspaceId, expenseId, fileId);
        HttpHeaders headers = new HttpHeaders();
        String contentType = resp.contentType();
        if (contentType != null && !contentType.isBlank()) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        }
        return ResponseEntity.status(resp.statusCode()).headers(headers).body(resp.bytes());
    }

    // ── Invoices ──────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/invoices")
    public Object getInvoices(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.invoices().getInvoices(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/invoices")
    public Object createInvoice(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().createInvoice(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/invoices/info")
    public Object filterInvoices(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().filterInvoices(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/invoices/settings")
    public Object getInvoiceSettings(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.invoices().getInvoiceSettings(workspaceId);
    }

    @PutMapping("/workspaces/{workspaceId}/invoices/settings")
    public Object updateInvoiceSettings(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().updateInvoiceSettings(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/invoices/{invoiceId}")
    public Object getInvoice(@PathVariable String workspaceId, @PathVariable String invoiceId) throws IOException, InterruptedException {
        return clockifyClient.invoices().getInvoice(workspaceId, invoiceId);
    }

    @PutMapping("/workspaces/{workspaceId}/invoices/{invoiceId}")
    public Object updateInvoice(@PathVariable String workspaceId, @PathVariable String invoiceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().updateInvoice(workspaceId, invoiceId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/invoices/{invoiceId}")
    public ResponseEntity<Void> deleteInvoice(@PathVariable String workspaceId, @PathVariable String invoiceId) throws IOException, InterruptedException {
        clockifyClient.invoices().deleteInvoice(workspaceId, invoiceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/duplicate")
    public Object duplicateInvoice(@PathVariable String workspaceId, @PathVariable String invoiceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().duplicateInvoice(workspaceId, invoiceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/export")
    public ResponseEntity<byte[]> exportInvoice(@PathVariable String workspaceId, @PathVariable String invoiceId) throws IOException, InterruptedException {
        ClockifyBinaryResponse resp = clockifyClient.invoices().exportInvoice(workspaceId, invoiceId);
        HttpHeaders headers = new HttpHeaders();
        String contentType = resp.contentType();
        if (contentType != null && !contentType.isBlank()) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        }
        return ResponseEntity.status(resp.statusCode()).headers(headers).body(resp.bytes());
    }

    @PostMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/items")
    public Object addInvoiceItem(@PathVariable String workspaceId, @PathVariable String invoiceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().addInvoiceItem(workspaceId, invoiceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/items/import")
    public Object importInvoiceItems(@PathVariable String workspaceId, @PathVariable String invoiceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().importInvoiceItems(workspaceId, invoiceId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/items/{order}")
    public ResponseEntity<Void> deleteInvoiceItem(@PathVariable String workspaceId, @PathVariable String invoiceId, @PathVariable String order) throws IOException, InterruptedException {
        clockifyClient.invoices().deleteInvoiceItem(workspaceId, invoiceId, Integer.parseInt(order));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/payments")
    public Object getInvoicePayments(
            @PathVariable String workspaceId,
            @PathVariable String invoiceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.invoices().getInvoicePayments(workspaceId, invoiceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/payments")
    public Object createInvoicePayment(@PathVariable String workspaceId, @PathVariable String invoiceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().createInvoicePayment(workspaceId, invoiceId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/payments/{paymentId}")
    public ResponseEntity<Void> deleteInvoicePayment(@PathVariable String workspaceId, @PathVariable String invoiceId, @PathVariable String paymentId) throws IOException, InterruptedException {
        clockifyClient.invoices().deleteInvoicePayment(workspaceId, invoiceId, paymentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/workspaces/{workspaceId}/invoices/{invoiceId}/status")
    public Object changeInvoiceStatus(@PathVariable String workspaceId, @PathVariable String invoiceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.invoices().changeInvoiceStatus(workspaceId, invoiceId, body);
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    @PostMapping("/workspaces/{workspaceId}/reports/attendance")
    public Object generateAttendanceReport(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.reports().attendance(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/reports/detailed")
    public Object generateDetailedReport(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.reports().detailed(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/reports/expenses/detailed")
    public Object generateExpenseDetailedReport(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.reports().expenseDetailed(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/reports/summary")
    public Object generateSummaryReport(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.reports().summary(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/reports/weekly")
    public Object generateWeeklyReport(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.reports().weekly(workspaceId, body);
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    @PostMapping("/workspaces/{workspaceId}/scheduling/assignments")
    public Object createSchedulingAssignment(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().createAssignment(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/scheduling/assignments/all")
    public Object getAllSchedulingAssignments(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.scheduling().getAllAssignments(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/scheduling/assignments/projects/totals")
    public Object getSchedulingAssignmentsPerProjectTotals(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().getAssignmentsPerProjectTotals(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/scheduling/assignments/projects/totals/{projectId}")
    public Object getProjectSchedulingAssignmentsTotals(@PathVariable String workspaceId, @PathVariable String projectId) throws IOException, InterruptedException {
        return clockifyClient.scheduling().getProjectAssignmentsTotals(workspaceId, projectId);
    }

    @PutMapping("/workspaces/{workspaceId}/scheduling/assignments/publish")
    public Object publishSchedulingAssignments(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().publishAssignments(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/scheduling/assignments/recurring")
    public Object createRecurringAssignment(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().createRecurringAssignment(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/scheduling/assignments/recurring/{assignmentId}")
    public Object replaceRecurringAssignment(@PathVariable String workspaceId, @PathVariable String assignmentId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().replaceRecurringAssignment(workspaceId, assignmentId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/scheduling/assignments/recurring/{assignmentId}")
    public Object updateRecurringAssignment(@PathVariable String workspaceId, @PathVariable String assignmentId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().updateRecurringAssignment(workspaceId, assignmentId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/scheduling/assignments/recurring/{assignmentId}")
    public ResponseEntity<Void> deleteRecurringAssignment(@PathVariable String workspaceId, @PathVariable String assignmentId) throws IOException, InterruptedException {
        clockifyClient.scheduling().deleteRecurringAssignment(workspaceId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/workspaces/{workspaceId}/scheduling/assignments/series/{assignmentId}")
    public Object changeRecurringAssignmentPeriod(@PathVariable String workspaceId, @PathVariable String assignmentId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().changeRecurringPeriod(workspaceId, assignmentId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/scheduling/assignments/user-filter/totals")
    public Object getUsersCapacityTotals(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().getUsersCapacityTotals(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/scheduling/assignments/users/totals")
    public Object getSchedulingAssignmentsUsersTotals(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().getAssignmentsUsersTotals(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/scheduling/assignments/users/{userId}/totals")
    public Object getUserSchedulingCapacityTotal(@PathVariable String workspaceId, @PathVariable String userId) throws IOException, InterruptedException {
        return clockifyClient.scheduling().getUserCapacityTotal(workspaceId, userId);
    }

    @PutMapping("/workspaces/{workspaceId}/scheduling/assignments/{assignmentId}")
    public Object replaceSchedulingAssignment(@PathVariable String workspaceId, @PathVariable String assignmentId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().replaceAssignment(workspaceId, assignmentId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/scheduling/assignments/{assignmentId}")
    public ResponseEntity<Void> deleteSchedulingAssignment(@PathVariable String workspaceId, @PathVariable String assignmentId) throws IOException, InterruptedException {
        clockifyClient.scheduling().deleteAssignment(workspaceId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workspaces/{workspaceId}/scheduling/assignments/{assignmentId}/copy")
    public Object copySchedulingAssignment(@PathVariable String workspaceId, @PathVariable String assignmentId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.scheduling().copyAssignment(workspaceId, assignmentId, body);
    }

    // ── Webhooks ──────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/addons/{addonId}/webhooks")
    public Object getAddonWebhooks(@PathVariable String workspaceId, @PathVariable String addonId) throws IOException, InterruptedException {
        return clockifyClient.webhooks().getAddonWebhooks(workspaceId, addonId);
    }

    @GetMapping("/workspaces/{workspaceId}/webhooks")
    public Object getWebhooks(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.webhooks().getWebhooks(workspaceId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/webhooks")
    public Object createWebhook(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.webhooks().createWebhook(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/webhooks/{webhookId}")
    public Object getWebhook(@PathVariable String workspaceId, @PathVariable String webhookId) throws IOException, InterruptedException {
        return clockifyClient.webhooks().getWebhook(workspaceId, webhookId);
    }

    @PutMapping("/workspaces/{workspaceId}/webhooks/{webhookId}")
    public Object updateWebhook(@PathVariable String workspaceId, @PathVariable String webhookId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.webhooks().updateWebhook(workspaceId, webhookId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/webhooks/{webhookId}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable String workspaceId, @PathVariable String webhookId) throws IOException, InterruptedException {
        clockifyClient.webhooks().deleteWebhook(workspaceId, webhookId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/workspaces/{workspaceId}/webhooks/{webhookId}/token")
    public Object generateWebhookNewToken(@PathVariable String workspaceId, @PathVariable String webhookId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.webhooks().generateNewToken(workspaceId, webhookId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/webhooks/{webhookId}/logs")
    public Object getWebhookLogs(
            @PathVariable String workspaceId,
            @PathVariable String webhookId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page-size", defaultValue = "50") int pageSize) throws IOException, InterruptedException {
        return clockifyClient.webhooks().getWebhookLogs(workspaceId, webhookId, new ClockifyPageRequest(page, pageSize));
    }

    @PostMapping("/workspaces/{workspaceId}/webhooks/{webhookId}/logs")
    public Object filterWebhookLogs(@PathVariable String workspaceId, @PathVariable String webhookId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.webhooks().filterWebhookLogs(workspaceId, webhookId, body);
    }

    // ── Workspaces (extended) ─────────────────────────────────────────────────

    @PostMapping("/workspaces")
    public Object addWorkspace(@RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.workspaces().addWorkspace(body);
    }

    @PutMapping("/workspaces/{workspaceId}")
    public Object updateWorkspace(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.workspaces().updateWorkspace(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/balance")
    public Object getWorkspaceBalance(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.workspaces().getWorkspaceBalance(workspaceId);
    }

    @PatchMapping("/workspaces/{workspaceId}/balance")
    public Object updateWorkspaceBalance(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.workspaces().updateWorkspaceBalance(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/cost-rate")
    public Object updateWorkspaceCostRate(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.workspaces().updateWorkspaceCostRate(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/hourly-rate")
    public Object updateWorkspaceBillableRate(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.workspaces().updateWorkspaceBillableRate(workspaceId, body);
    }

    // ── Clients (extended) ────────────────────────────────────────────────────

    @PutMapping("/workspaces/{workspaceId}/clients/{clientId}/archive")
    public Object archiveClient(@PathVariable String workspaceId, @PathVariable String clientId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.clients().archiveClient(workspaceId, clientId, body);
    }

    // ── Users (extended) ──────────────────────────────────────────────────────

    @PostMapping("/workspaces/{workspaceId}/users")
    public Object addUserToWorkspace(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().addUserToWorkspace(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/member-profile/{userId}")
    public Object replaceMemberProfile(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().replaceMemberProfile(workspaceId, userId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/member-profile/{userId}")
    public Object updateMemberProfile(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().updateMemberProfile(workspaceId, userId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/users/{userId}")
    public Object updateUserStatus(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().updateUserStatus(workspaceId, userId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/users/{userId}/cost-rate")
    public Object updateUserCostRate(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().updateUserCostRate(workspaceId, userId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/users/{userId}/custom-field/{customFieldId}/value")
    public Object updateUserCustomFieldValue(@PathVariable String workspaceId, @PathVariable String userId, @PathVariable String customFieldId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().updateUserCustomFieldValue(workspaceId, userId, customFieldId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/users/{userId}/hourly-rate")
    public Object updateUserHourlyRate(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().updateUserHourlyRate(workspaceId, userId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/users/{userId}/roles")
    public Object giveUserManagerRole(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.users().giveUserManagerRole(workspaceId, userId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/users/{userId}/roles")
    public Object removeUserManagerRole(@PathVariable String workspaceId, @PathVariable String userId) throws IOException, InterruptedException {
        return clockifyClient.users().removeUserManagerRole(workspaceId, userId);
    }

    @GetMapping("/workspaces/{workspaceId}/users/{userId}/time-off/balances")
    public Object getUserTimeOffBalances(@PathVariable String workspaceId, @PathVariable String userId) throws IOException, InterruptedException {
        return clockifyClient.users().getUserTimeOffBalances(workspaceId, userId);
    }

    // ── Projects (extended) ───────────────────────────────────────────────────

    @PostMapping("/workspaces/{workspaceId}/projects/from-template")
    public Object createProjectFromTemplate(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().createProjectFromTemplate(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}/archive")
    public Object archiveProject(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().archiveProject(workspaceId, projectId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}/cost-rate")
    public Object updateProjectCostRate(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateProjectCostRate(workspaceId, projectId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/projects/{projectId}/estimate")
    public Object updateProjectEstimate(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateProjectEstimate(workspaceId, projectId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}/hourly-rate")
    public Object updateProjectBillableRate(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateProjectBillableRate(workspaceId, projectId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/projects/{projectId}/memberships")
    public Object assignProjectUsers(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().assignProjectUsers(workspaceId, projectId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/projects/{projectId}/memberships")
    public Object updateProjectMemberships(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateProjectMemberships(workspaceId, projectId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/cost-rate")
    public Object updateTaskCostRate(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String taskId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateTaskCostRate(workspaceId, projectId, taskId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/hourly-rate")
    public Object updateTaskBillableRate(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String taskId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateTaskBillableRate(workspaceId, projectId, taskId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/projects/{projectId}/template")
    public Object updateProjectTemplate(@PathVariable String workspaceId, @PathVariable String projectId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateProjectTemplate(workspaceId, projectId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}/users/{userId}/cost-rate")
    public Object updateProjectUserCostRate(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateProjectUserCostRate(workspaceId, projectId, userId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectId}/users/{userId}/hourly-rate")
    public Object updateProjectUserHourlyRate(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.projects().updateProjectUserHourlyRate(workspaceId, projectId, userId, body);
    }

    // ── Time Entries (extended) ───────────────────────────────────────────────

    @PatchMapping("/workspaces/{workspaceId}/time-entries/invoiced")
    public Object markTimeEntriesInvoiced(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().markTimeEntriesInvoiced(workspaceId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/time-entries/invoiced/bulk")
    public Object markTimeEntriesInvoicedBulk(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().markTimeEntriesInvoicedBulk(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/user/{userId}/time-entries")
    public Object createTimeEntryForUser(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().createTimeEntryForUser(workspaceId, userId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/user/{userId}/time-entries")
    public Object replaceTimeEntriesForUser(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().replaceTimeEntriesForUser(workspaceId, userId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/user/{userId}/time-entries")
    public Object patchTimeEntriesForUser(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().patchTimeEntriesForUser(workspaceId, userId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/user/{userId}/time-entries")
    public Object deleteManyTimeEntriesForUser(@PathVariable String workspaceId, @PathVariable String userId) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().deleteManyTimeEntriesForUser(workspaceId, userId);
    }

    @PatchMapping("/workspaces/{workspaceId}/user/{userId}/time-entries/stop")
    public Object stopTimerForUser(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().stopTimerForUser(workspaceId, userId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/user/{userId}/time-entries/{timeEntryId}/duplicate")
    public Object duplicateTimeEntry(@PathVariable String workspaceId, @PathVariable String userId, @PathVariable String timeEntryId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeEntries().duplicateTimeEntry(workspaceId, userId, timeEntryId, body);
    }

    // ── User Groups (extended) ────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/user-groups/{groupId}")
    public Object getUserGroup(@PathVariable String workspaceId, @PathVariable String groupId) throws IOException, InterruptedException {
        return clockifyClient.userGroups().getUserGroup(workspaceId, groupId);
    }

    @GetMapping("/workspaces/{workspaceId}/user-groups/{groupId}/users")
    public Object getUserGroupMembers(@PathVariable String workspaceId, @PathVariable String groupId) throws IOException, InterruptedException {
        return clockifyClient.userGroups().getUserGroupMembers(workspaceId, groupId);
    }

    // ── Policies ─────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/policies")
    public Object getWorkspacePolicies(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.policies().getPolicies(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/policies")
    public Object createWorkspacePolicy(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.policies().createPolicy(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/policies/{policyId}")
    public Object getWorkspacePolicy(@PathVariable String workspaceId, @PathVariable String policyId) throws IOException, InterruptedException {
        return clockifyClient.policies().getPolicy(workspaceId, policyId);
    }

    @PutMapping("/workspaces/{workspaceId}/policies/{policyId}")
    public Object updateWorkspacePolicy(@PathVariable String workspaceId, @PathVariable String policyId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.policies().updatePolicy(workspaceId, policyId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/policies/{policyId}")
    public Object deleteWorkspacePolicy(@PathVariable String workspaceId, @PathVariable String policyId) throws IOException, InterruptedException {
        return clockifyClient.policies().deletePolicy(workspaceId, policyId);
    }

    @PatchMapping("/workspaces/{workspaceId}/policies/{policyId}/archive")
    public Object archiveWorkspacePolicy(@PathVariable String workspaceId, @PathVariable String policyId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.policies().archivePolicy(workspaceId, policyId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/policies/{policyId}/requests")
    public Object createPolicyRequest(@PathVariable String workspaceId, @PathVariable String policyId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.policies().createPolicyRequest(workspaceId, policyId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/policies/{policyId}/requests/{requestId}")
    public Object updatePolicyRequest(@PathVariable String workspaceId, @PathVariable String policyId, @PathVariable String requestId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.policies().updatePolicyRequest(workspaceId, policyId, requestId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/policies/{policyId}/requests/{requestId}")
    public Object deletePolicyRequest(@PathVariable String workspaceId, @PathVariable String policyId, @PathVariable String requestId) throws IOException, InterruptedException {
        return clockifyClient.policies().deletePolicyRequest(workspaceId, policyId, requestId);
    }

    // ── Shared Reports ────────────────────────────────────────────────────────

    @GetMapping("/shared-reports/{sharedReportId}")
    public Object getSharedReport(@PathVariable String sharedReportId) throws IOException, InterruptedException {
        return clockifyClient.sharedReports().getSharedReport(sharedReportId);
    }

    @GetMapping("/workspaces/{workspaceId}/shared-reports")
    public Object getWorkspaceSharedReports(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.sharedReports().getWorkspaceSharedReports(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/shared-reports")
    public Object createSharedReport(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.sharedReports().createSharedReport(workspaceId, body);
    }

    @PutMapping("/workspaces/{workspaceId}/shared-reports/{sharedReportId}")
    public Object updateSharedReport(@PathVariable String workspaceId, @PathVariable String sharedReportId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.sharedReports().updateSharedReport(workspaceId, sharedReportId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/shared-reports/{sharedReportId}")
    public Object deleteSharedReport(@PathVariable String workspaceId, @PathVariable String sharedReportId) throws IOException, InterruptedException {
        return clockifyClient.sharedReports().deleteSharedReport(workspaceId, sharedReportId);
    }

    // ── Time Off ──────────────────────────────────────────────────────────────

    @GetMapping("/workspaces/{workspaceId}/time-off/balance/policy/{policyId}")
    public Object getTimeOffBalancesForPolicy(@PathVariable String workspaceId, @PathVariable String policyId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().getBalancesForPolicy(workspaceId, policyId);
    }

    @PatchMapping("/workspaces/{workspaceId}/time-off/balance/policy/{policyId}")
    public Object updateTimeOffBalance(@PathVariable String workspaceId, @PathVariable String policyId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().updateBalance(workspaceId, policyId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/time-off/balance/user/{userId}")
    public Object getTimeOffBalanceForUser(@PathVariable String workspaceId, @PathVariable String userId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().getBalanceForUser(workspaceId, userId);
    }

    @GetMapping("/workspaces/{workspaceId}/time-off/policies")
    public Object getTimeOffPolicies(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().getPolicies(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/time-off/policies")
    public Object createTimeOffPolicy(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().createPolicy(workspaceId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/time-off/policies/{policyId}")
    public Object getTimeOffPolicy(@PathVariable String workspaceId, @PathVariable String policyId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().getPolicy(workspaceId, policyId);
    }

    @PutMapping("/workspaces/{workspaceId}/time-off/policies/{policyId}")
    public Object updateTimeOffPolicy(@PathVariable String workspaceId, @PathVariable String policyId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().updatePolicy(workspaceId, policyId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/time-off/policies/{policyId}")
    public Object changeTimeOffPolicyStatus(@PathVariable String workspaceId, @PathVariable String policyId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().changePolicyStatus(workspaceId, policyId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/time-off/policies/{policyId}")
    public Object deleteTimeOffPolicy(@PathVariable String workspaceId, @PathVariable String policyId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().deletePolicy(workspaceId, policyId);
    }

    @PostMapping("/workspaces/{workspaceId}/time-off/policies/{policyId}/requests")
    public Object createTimeOffRequest(@PathVariable String workspaceId, @PathVariable String policyId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().createRequest(workspaceId, policyId, body);
    }

    @PatchMapping("/workspaces/{workspaceId}/time-off/policies/{policyId}/requests/{requestId}")
    public Object changeTimeOffRequestStatus(@PathVariable String workspaceId, @PathVariable String policyId, @PathVariable String requestId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().changeRequestStatus(workspaceId, policyId, requestId, body);
    }

    @DeleteMapping("/workspaces/{workspaceId}/time-off/policies/{policyId}/requests/{requestId}")
    public Object deleteTimeOffRequest(@PathVariable String workspaceId, @PathVariable String policyId, @PathVariable String requestId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().deleteRequest(workspaceId, policyId, requestId);
    }

    @PostMapping("/workspaces/{workspaceId}/time-off/policies/{policyId}/users/{userId}/requests")
    public Object createTimeOffRequestForUser(@PathVariable String workspaceId, @PathVariable String policyId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().createRequestForUser(workspaceId, policyId, userId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/time-off/requests")
    public Object getTimeOffRequests(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().getRequests(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/time-off/requests")
    public Object filterTimeOffRequests(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().filterRequests(workspaceId, body);
    }

    @PostMapping("/workspaces/{workspaceId}/time-off/requests/users/{userId}")
    public Object filterTimeOffRequestsForUser(@PathVariable String workspaceId, @PathVariable String userId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().filterRequestsForUser(workspaceId, userId, body);
    }

    @GetMapping("/workspaces/{workspaceId}/time-off/requests/{requestId}")
    public Object getTimeOffRequest(@PathVariable String workspaceId, @PathVariable String requestId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().getRequest(workspaceId, requestId);
    }

    @DeleteMapping("/workspaces/{workspaceId}/time-off/requests/{requestId}")
    public Object deleteTimeOffRequestById(@PathVariable String workspaceId, @PathVariable String requestId) throws IOException, InterruptedException {
        return clockifyClient.timeOff().deleteRequestById(workspaceId, requestId);
    }

    @PatchMapping("/workspaces/{workspaceId}/time-off/requests/{requestId}/status")
    public Object updateTimeOffRequestStatus(@PathVariable String workspaceId, @PathVariable String requestId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        return clockifyClient.timeOff().updateRequestStatus(workspaceId, requestId, body);
    }

    // ── Addon Settings ────────────────────────────────────────────────────────

    @GetMapping("/addon/workspaces/{workspaceId}/settings")
    public Object getAddonSettings(@PathVariable String workspaceId) throws IOException, InterruptedException {
        return clockifyClient.addonSettings().getSettings(workspaceId);
    }

    @PatchMapping("/addon/workspaces/{workspaceId}/settings")
    public void updateAddonSettings(@PathVariable String workspaceId, @RequestBody JsonNode body) throws IOException, InterruptedException {
        clockifyClient.addonSettings().updateSettings(workspaceId, body);
    }
}
