package com.cake.clockify.client;

import com.cake.clockify.client.domains.ApprovalsClient;
import com.cake.clockify.client.domains.ClientsClient;
import com.cake.clockify.client.domains.CustomFieldsClient;
import com.cake.clockify.client.domains.ExpensesClient;
import com.cake.clockify.client.domains.EntityChangesClient;
import com.cake.clockify.client.domains.FilesClient;
import com.cake.clockify.client.domains.HolidaysClient;
import com.cake.clockify.client.domains.InvoicesClient;
import com.cake.clockify.client.domains.PoliciesClient;
import com.cake.clockify.client.domains.ProjectsClient;
import com.cake.clockify.client.domains.ReportsClient;
import com.cake.clockify.client.domains.SchedulingClient;
import com.cake.clockify.client.domains.SharedReportsClient;
import com.cake.clockify.client.domains.TagsClient;
import com.cake.clockify.client.domains.TasksClient;
import com.cake.clockify.client.domains.TimeEntriesClient;
import com.cake.clockify.client.domains.TimeOffClient;
import com.cake.clockify.client.domains.UserGroupsClient;
import com.cake.clockify.client.domains.UsersClient;
import com.cake.clockify.client.domains.WebhooksClient;
import com.cake.clockify.client.domains.WorkspacesClient;
import com.cake.clockify.client.domains.AddonSettingsClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.cake.clockify.client.auth.ClockifyClaims;

public final class ClockifyClient {
    private final ClockifyClientConfig config;
    private final ClockifyTransport transport;
    private final ClockifyRawClient rawClient;

    /**
     * Creates a ClockifyClient directly from verified ClockifyClaims and an addon/user token.
     * This automatically configures the client to route requests to the correct regional 
     * backend, reports, and time-off (PTO) base URLs for the workspace.
     */
    public static ClockifyClient fromClaims(ClockifyClaims claims, String token) {
        ClockifyClientBuilder builder = builder()
                .addonToken(token)
                .workspaceId(claims.workspaceId());
        
        if (claims.backendUrl() != null && !claims.backendUrl().isBlank()) {
            builder.backendBaseUrl(claims.backendUrl());
        }
        if (claims.reportsUrl() != null && !claims.reportsUrl().isBlank()) {
            builder.reportsBaseUrl(claims.reportsUrl());
        }
        
        return new ClockifyClient(builder.buildConfig());
    }
    private final UsersClient usersClient;
    private final WorkspacesClient workspacesClient;
    private final ClientsClient clientsClient;
    private final ProjectsClient projectsClient;
    private final TagsClient tagsClient;
    private final TasksClient tasksClient;
    private final TimeEntriesClient timeEntriesClient;
    private final UserGroupsClient userGroupsClient;
    private final HolidaysClient holidaysClient;
    private final ReportsClient reportsClient;
    private final FilesClient filesClient;
    private final InvoicesClient invoicesClient;
    private final ExpensesClient expensesClient;
    private final EntityChangesClient entityChangesClient;
    private final CustomFieldsClient customFieldsClient;
    private final TimeOffClient timeOffClient;
    private final SchedulingClient schedulingClient;
    private final ApprovalsClient approvalsClient;
    private final WebhooksClient webhooksClient;
    private final PoliciesClient policiesClient;
    private final SharedReportsClient sharedReportsClient;
    private final AddonSettingsClient addonSettingsClient;

    public ClockifyClient(ClockifyClientConfig config) {
        this(config, new DefaultClockifyTransport(config));
    }

    public ClockifyClient(ClockifyClientConfig config, ClockifyTransport transport) {
        this.config = config;
        this.transport = transport;
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndRegisterModules();
        this.rawClient = new ClockifyRawClient(transport);
        this.usersClient = new UsersClient(transport, objectMapper);
        this.workspacesClient = new WorkspacesClient(transport, objectMapper);
        this.clientsClient = new ClientsClient(transport, objectMapper);
        this.projectsClient = new ProjectsClient(transport, objectMapper);
        this.tagsClient = new TagsClient(transport, objectMapper);
        this.tasksClient = new TasksClient(transport, objectMapper);
        this.timeEntriesClient = new TimeEntriesClient(transport, objectMapper);
        this.userGroupsClient = new UserGroupsClient(transport, objectMapper);
        this.holidaysClient = new HolidaysClient(transport, objectMapper);
        this.reportsClient = new ReportsClient(transport, objectMapper);
        this.filesClient = new FilesClient(transport, objectMapper);
        this.invoicesClient = new InvoicesClient(transport, objectMapper);
        this.expensesClient = new ExpensesClient(transport, objectMapper);
        this.entityChangesClient = new EntityChangesClient(transport, objectMapper);
        this.customFieldsClient = new CustomFieldsClient(transport, objectMapper);
        this.timeOffClient = new TimeOffClient(transport, objectMapper);
        this.schedulingClient = new SchedulingClient(transport, objectMapper);
        this.approvalsClient = new ApprovalsClient(transport, objectMapper);
        this.webhooksClient = new WebhooksClient(transport, objectMapper);
        this.policiesClient = new PoliciesClient(transport, objectMapper);
        this.sharedReportsClient = new SharedReportsClient(transport, objectMapper);
        this.addonSettingsClient = new AddonSettingsClient(transport, objectMapper);
    }

    public static ClockifyClientBuilder builder() {
        return new ClockifyClientBuilder();
    }

    public ClockifyClientConfig config() { return config; }
    public ClockifyTransport transport() { return transport; }
    public ClockifyRawClient raw() { return rawClient; }
    public UsersClient users() { return usersClient; }
    public WorkspacesClient workspaces() { return workspacesClient; }
    public ClientsClient clients() { return clientsClient; }
    public ProjectsClient projects() { return projectsClient; }
    public TagsClient tags() { return tagsClient; }
    public TasksClient tasks() { return tasksClient; }
    public TimeEntriesClient timeEntries() { return timeEntriesClient; }
    public UserGroupsClient userGroups() { return userGroupsClient; }
    public HolidaysClient holidays() { return holidaysClient; }
    public ReportsClient reports() { return reportsClient; }
    public FilesClient files() { return filesClient; }
    public InvoicesClient invoices() { return invoicesClient; }
    public ExpensesClient expenses() { return expensesClient; }
    public EntityChangesClient entityChanges() { return entityChangesClient; }
    public CustomFieldsClient customFields() { return customFieldsClient; }
    public TimeOffClient timeOff() { return timeOffClient; }
    public SchedulingClient scheduling() { return schedulingClient; }
    public ApprovalsClient approvals() { return approvalsClient; }
    public WebhooksClient webhooks() { return webhooksClient; }
    public PoliciesClient policies() { return policiesClient; }
    public SharedReportsClient sharedReports() { return sharedReportsClient; }
    public AddonSettingsClient addonSettings() { return addonSettingsClient; }
}
