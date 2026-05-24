# Curl gap campaign — clean1 operations not yet in typed provenance

Date: 2026-05-19

Scope: live curl probes for clean1 OpenAPI operations that were exposed by the Spring `ClockifyRestController` but did not yet have a matching typed-client provenance row at campaign start.

Secrets: `X-Api-Key` was sent from `.env` and never written to this file. Response bodies are summarized by status/content type/shape/keys only; raw bodies are not stored.

Mutation safety: mutating endpoints were probed with `{}` or synthetic child IDs unless they are report/audit read-like search endpoints. This validates route/auth/error behavior without creating durable entities; create/update success flows still need per-domain sacrificial lifecycle probes before marking verified.

Current-user lookup: GET /user returned 404; user id was used only in URLs and is not printed here.

Total candidate gaps probed: 78

Cleanup note: one probe (`POST /workspaces/{workspaceId}/user/{userId}/time-entries` with `{}`) unexpectedly created a blank time entry (`201`). A follow-up redacted curl lookup found one blank candidate and `DELETE /workspaces/{workspaceId}/time-entries/{timeEntryId}` returned `204`; no raw entry payload or id is stored here.

## Balance

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| GET | `/workspaces/{workspaceId}/balance` | `getWorkspacesWorkspaceIdBalance` | read | 404 | application/json |  | object | code,message | read-like |
| PATCH | `/workspaces/{workspaceId}/balance` | `patchWorkspacesWorkspaceIdBalance` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| GET | `/workspaces/{workspaceId}/time-off/balance/policy/{policyId}` | `getBalancesForPolicy` | read | 200 | application/json | true | object | balances,count | read-like |
| PATCH | `/workspaces/{workspaceId}/time-off/balance/policy/{policyId}` | `updateBalance` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| GET | `/workspaces/{workspaceId}/time-off/balance/user/{userId}` | `getBalanceForUser` | read | 200 | application/json | false | non-json | <redacted truncated/non-json body; raw body not stored> | read-like |
| GET | `/workspaces/{workspaceId}/time-off/requests` | `getWorkspacesWorkspaceIdTimeOffRequests` | read | 405 | application/json |  | object | code,message | read-like |
| GET | `/workspaces/{workspaceId}/users/{userId}/time-off/balances` | `getWorkspacesWorkspaceIdUsersUserIdTimeOffBalances` | read | 404 | application/json |  | object | code,message | read-like |

## Client

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| PUT | `/workspaces/{workspaceId}/clients/{clientId}/archive` | `putWorkspacesWorkspaceIdClientsClientIdArchive` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Entity changes (Experimental)

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| GET | `/workspaces/{workspaceId}/entities/created` | `getCreatedEntityInfo` | read | 400 | application/json |  | object | code,message | read-like |
| GET | `/workspaces/{workspaceId}/entities/deleted` | `getDeletedEntityInfo` | read | 400 | application/json |  | object | code,message | read-like |
| GET | `/workspaces/{workspaceId}/entities/updated` | `getUpdatedEntityInfo` | read | 400 | application/json |  | object | code,message | read-like |

## Expense Report

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| POST | `/workspaces/{workspaceId}/reports/expenses/detailed` | `generateDetailedReportV1` | minimal-body | 200 | application/json | true | object | customFields,expenses,exportFields,reportName,totals | read-like |

## Member profiles

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| PATCH | `/workspaces/{workspaceId}/member-profile/{userId}` | `updateMemberProfile` | minimal-body | 200 | application/json |  | non-json | <redacted truncated/non-json body; raw body not stored> | minimal invalid body or synthetic child IDs |

## Policy

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| GET | `/workspaces/{workspaceId}/policies` | `getWorkspacesWorkspaceIdPolicies` | read | 404 | application/json |  | object | code,message | read-like |
| POST | `/workspaces/{workspaceId}/policies` | `postWorkspacesWorkspaceIdPolicies` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| GET | `/workspaces/{workspaceId}/policies/{policyId}` | `getWorkspacesWorkspaceIdPoliciesPolicyId` | read | 404 | application/json |  | object | code,message | read-like |
| PUT | `/workspaces/{workspaceId}/policies/{policyId}` | `putWorkspacesWorkspaceIdPoliciesPolicyId` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| DELETE | `/workspaces/{workspaceId}/policies/{policyId}` | `deleteWorkspacesWorkspaceIdPoliciesPolicyId` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/policies/{policyId}/archive` | `patchWorkspacesWorkspaceIdPoliciesPolicyIdArchive` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Project

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| PUT | `/workspaces/{workspaceId}/projects/{projectId}/archive` | `putWorkspacesWorkspaceIdProjectsProjectIdArchive` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/projects/{projectId}/cost-rate` | `putWorkspacesWorkspaceIdProjectsProjectIdCostRate` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/projects/{projectId}/hourly-rate` | `putWorkspacesWorkspaceIdProjectsProjectIdHourlyRate` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Projects

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| POST | `/workspaces/{workspaceId}/projects/from-template` | `createProjectFromTemplate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/projects/{projectId}/estimate` | `updateProjectEstimate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| POST | `/workspaces/{workspaceId}/projects/{projectId}/memberships` | `assignOrRemoveProjectUsers` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/projects/{projectId}/memberships` | `updateProjectMemberships` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/projects/{projectId}/template` | `updateProjectTemplate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/projects/{projectId}/users/{userId}/cost-rate` | `updateProjectUserCostRate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/projects/{projectId}/users/{userId}/hourly-rate` | `updateProjectUserHourlyRate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Reports

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| POST | `/workspaces/{workspaceId}/reports/attendance` | `generateAttendanceReport` | minimal-body | 400 | application/json |  | object | code,message | read-like |

## Roles

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| POST | `/workspaces/{workspaceId}/users/{userId}/roles` | `giveUserManagerRole` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| DELETE | `/workspaces/{workspaceId}/users/{userId}/roles` | `removeUserManagerRole` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## SharedReport

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| GET | `/shared-reports/{sharedReportId}` | `getSharedReportsSharedReportId` | read | 404 | application/json |  | object | code,message | read-like |
| GET | `/workspaces/{workspaceId}/shared-reports` | `getWorkspacesWorkspaceIdSharedReports` | read | 404 | application/json |  | object | code,message | read-like |
| POST | `/workspaces/{workspaceId}/shared-reports` | `postWorkspacesWorkspaceIdSharedReports` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/shared-reports/{sharedReportId}` | `putWorkspacesWorkspaceIdSharedReportsSharedReportId` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| DELETE | `/workspaces/{workspaceId}/shared-reports/{sharedReportId}` | `deleteWorkspacesWorkspaceIdSharedReportsSharedReportId` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Tasks

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| PUT | `/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/cost-rate` | `updateTaskCostRate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/hourly-rate` | `updateTaskBillableRate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Time Entries

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| PATCH | `/workspaces/{workspaceId}/time-entries/invoiced` | `patchWorkspacesWorkspaceIdTimeEntriesInvoiced` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/user/{userId}/time-entries` | `putWorkspacesWorkspaceIdUserUserIdTimeEntries` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/user/{userId}/time-entries` | `patchWorkspacesWorkspaceIdUserUserIdTimeEntries` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| POST | `/workspaces/{workspaceId}/user/{userId}/time-entries/{timeEntryId}/duplicate` | `postWorkspacesWorkspaceIdUserUserIdTimeEntriesTimeEntryIdDuplicate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Time Off

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| POST | `/workspaces/{workspaceId}/policies/{policyId}/requests` | `postWorkspacesWorkspaceIdPoliciesPolicyIdRequests` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/policies/{policyId}/requests/{requestId}` | `patchWorkspacesWorkspaceIdPoliciesPolicyIdRequestsRequestId` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| DELETE | `/workspaces/{workspaceId}/policies/{policyId}/requests/{requestId}` | `deleteWorkspacesWorkspaceIdPoliciesPolicyIdRequestsRequestId` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| POST | `/workspaces/{workspaceId}/time-off/policies/{policyId}/requests` | `createTimeOffRequest` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/time-off/policies/{policyId}/requests/{requestId}` | `changeTimeOffRequestStatus` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| DELETE | `/workspaces/{workspaceId}/time-off/policies/{policyId}/requests/{requestId}` | `deleteTimeOffRequest` | minimal-body | 403 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| POST | `/workspaces/{workspaceId}/time-off/policies/{policyId}/users/{userId}/requests` | `createTimeOffRequestForUser` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| POST | `/workspaces/{workspaceId}/time-off/requests` | `getAllTimeOffRequestsOnWorkspace` | minimal-body | 200 | application/json | true | object | count,requests | minimal invalid body or synthetic child IDs |

## Time Off Policies

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| GET | `/workspaces/{workspaceId}/time-off/policies` | `getTimeOffPolicies` | read | 200 | application/json | false | non-json | <redacted truncated/non-json body; raw body not stored> | read-like |
| POST | `/workspaces/{workspaceId}/time-off/policies` | `createTimeOffPolicy` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| GET | `/workspaces/{workspaceId}/time-off/policies/{policyId}` | `getTimeOffPolicy` | read | 404 | application/json |  | object | code,message | read-like |
| PUT | `/workspaces/{workspaceId}/time-off/policies/{policyId}` | `updateTimeOffPolicy` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/time-off/policies/{policyId}` | `changeTimeOffPolicyStatus` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| DELETE | `/workspaces/{workspaceId}/time-off/policies/{policyId}` | `deleteTimeOffPolicy` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Time entry

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| DELETE | `/workspaces/{workspaceId}/user/{userId}/time-entries` | `deleteMany` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## TimeEntry

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| PATCH | `/workspaces/{workspaceId}/time-entries/invoiced/bulk` | `patchWorkspacesWorkspaceIdTimeEntriesInvoicedBulk` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| POST | `/workspaces/{workspaceId}/user/{userId}/time-entries` | `postWorkspacesWorkspaceIdUserUserIdTimeEntries` | minimal-body | 201 | application/json |  | object | billable,customFieldValues,description,id,isLocked,kioskId,projectId,tagIds | accidental create from `{}`; cleaned up with DELETE 204 immediately after campaign |
| PATCH | `/workspaces/{workspaceId}/user/{userId}/time-entries/stop` | `patchWorkspacesWorkspaceIdUserUserIdTimeEntriesStop` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## TimeOff

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| POST | `/workspaces/{workspaceId}/time-off/requests/users/{userId}` | `postWorkspacesWorkspaceIdTimeOffRequestsUsersUserId` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| GET | `/workspaces/{workspaceId}/time-off/requests/{requestId}` | `getWorkspacesWorkspaceIdTimeOffRequestsRequestId` | read | 404 | application/json |  | object | code,message | read-like |
| DELETE | `/workspaces/{workspaceId}/time-off/requests/{requestId}` | `deleteWorkspacesWorkspaceIdTimeOffRequestsRequestId` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PATCH | `/workspaces/{workspaceId}/time-off/requests/{requestId}/status` | `patchWorkspacesWorkspaceIdTimeOffRequestsRequestIdStatus` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## User

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| PUT | `/workspaces/{workspaceId}/member-profile/{userId}` | `putWorkspacesWorkspaceIdMemberProfileUserId` | minimal-body | 405 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## User Groups

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| GET | `/workspaces/{workspaceId}/user-groups/{groupId}` | `getWorkspacesWorkspaceIdUserGroupsGroupId` | read | 405 | application/json |  | object | code,message | read-like |
| GET | `/workspaces/{workspaceId}/user-groups/{groupId}/users` | `getWorkspacesWorkspaceIdUserGroupsGroupIdUsers` | read | 405 | application/json |  | object | code,message | read-like |

## Users

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| GET | `/user` | `getCurrentUser` | read | 200 | application/json |  | object | activeWorkspace,customFields,defaultWorkspace,email,id,memberships,name,profilePicture | read-like |
| PUT | `/workspaces/{workspaceId}/users/{userId}/custom-field/{customFieldId}/value` | `updateUserCustomFieldValue` | minimal-body | 404 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |

## Workspaces

| Method | Path | operationId | Probe | Status | Content-Type | Last-Page | Shape | Keys/summary | Safety |
|---|---|---|---|---:|---|---|---|---|---|
| POST | `/workspaces` | `addWorkspace` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}` | `putWorkspacesWorkspaceId` | minimal-body | 405 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/cost-rate` | `updateWorkspaceCostRate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/hourly-rate` | `updateWorkspaceBillableRate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| POST | `/workspaces/{workspaceId}/users` | `addUserToWorkspace` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/users/{userId}` | `updateUserStatus` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/users/{userId}/cost-rate` | `updateUserCostRate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
| PUT | `/workspaces/{workspaceId}/users/{userId}/hourly-rate` | `updateUserHourlyRate` | minimal-body | 400 | application/json |  | object | code,message | minimal invalid body or synthetic child IDs |
