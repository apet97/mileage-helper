# Endpoint coverage

Authoritative detailed ledger: `endpoint-provenance.md`.

## First-slice typed coverage

| Domain | Status | Notes |
|---|---|---|
| Users | implemented | current user, workspace users/profile/managers/filter |
| Workspaces | implemented | list/get |
| Clients | implemented | list/create/get/update/delete; delete semantics noted experimental where live rejects |
| Projects | implemented | list/create/get/update/delete; archive-specific route deferred |
| Tasks | implemented | list/create/get/update/delete |
| Tags | implemented | list/create/get/update/delete |
| Time entries | implemented | list, in-progress list, create/get/update/delete; start/stop deferred until separately proven |
| Reports | implemented/experimental | summary/detailed/weekly, expense detailed, and attendance via reports host; attendance remains experimental until valid-body live verification |
| Files | implemented | image upload multipart |
| User groups | implemented | list/create/update/delete/add-user/delete-user |
| Holidays | implemented/experimental | list/create/update/delete; in-period query experimental |
| Invoices | implemented | invoice CRUD/filter/settings/duplicate/export/items/import/payments/status via backend; export uses binary handling |
| Expenses | implemented | expense CRUD, categories CRUD/status, receipt/file download uses binary handling |
| Custom fields | implemented | workspace custom fields and project custom field values/settings |
| Approvals | implemented | approval list/create/resubmit/user variants/status update |
| Webhooks | implemented | workspace/add-on webhooks, CRUD, token rotation/update, delivery logs |
| Entity changes | implemented/experimental | created/updated/deleted via backend with `page` + `limit`; OpenAPI marks group experimental |
| Scheduling | implemented | assignments, recurring assignments, project/user totals, publish/copy/delete |
| Time off | implemented | policies, requests, user requests, balances update/read |
| Shared reports | implemented | list, get data/info, create, update, delete |


## Controller/raw-facade coverage

`ClockifyRestController` is disabled by default. When explicitly enabled with `clockify.rest-controller.enabled=true`, it exposes 191 supported official Spring MVC/raw facade operations plus two add-on settings prose endpoints. This is intentionally separate from typed domain-client coverage.

## Deferred typed coverage

See `docs/unsupported-or-experimental.md`.

Audit logs are unsupported for generated add-ons until an allowed official source documents the route and add-on auth behavior. Live-only or raw-mirror evidence is not enough to scaffold a typed method.

For any next REST-client pass, keep the loop as: official evidence -> provenance row -> typed method or explicit unsupported/experimental marker -> focused test -> docs update -> `mvn test` -> next domain.
