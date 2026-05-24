# RestController vs typed-client gap analysis

Source OpenAPI: `/Users/15x/Downloads/clean1`.

`ClockifyRestController` is disabled by default and, when explicitly enabled, exposes 191 official clean OpenAPI operations plus two add-on settings prose endpoints. This document tracks clean1 operations that did not yet have a matching typed-client provenance row before the curl gap campaign in `docs/live-evidence/CURL_GAP_CAMPAIGN_2026-05-19.md`.

Summary after canonical path matching:

| Group | Missing typed operations | Curl evidence |
|---|---:|---|
| Balance / time-off balances | 7 | probed |
| Client archive | 1 | probed |
| Entity changes (experimental) | 0 | implemented in `EntityChangesClient`; remains experimental per OpenAPI |
| Expense report | 0 | implemented in `ReportsClient.expenseDetailed`; live probe returned 200 |
| Member profiles | 1 | probed |
| Policy | 6 | probed |
| Project admin/rates/memberships/template | 10 | probed |
| Reports attendance | 0 | implemented in `ReportsClient.attendance`; live minimal-body probe returned 400 so status remains experimental |
| Roles | 2 | probed |
| Shared reports | 5 | probed |
| Task rates | 2 | probed |
| Time entries advanced/bulk/user-scoped | 8 | probed; one accidental blank create was cleaned up with DELETE 204 |
| Time off requests/policies | 18 | probed |
| User groups read details | 2 | probed |
| Users/workspace membership/rates/custom fields | 10 | probed |
| Workspaces create/update/rates/users | 8 | probed |

Status interpretation:

- `200`/`201`: endpoint accepted the probe; if `201` was mutating, cleanup is documented in the curl evidence.
- `400`: route/auth reached; request body/parameters were intentionally minimal or invalid.
- `403`: route/auth reached but plan/permission/workspace state blocked the operation.
- `404`: route/auth reached with a synthetic child id or unavailable feature/entity.
- `405`: method/path family rejected the operation as called; needs per-operation investigation before typed support is marked verified.

Next implementation order for 100% typed coverage:

1. Add provenance + typed methods for remaining low-risk read groups: shared reports and user-group detail reads.
2. Add administrative update methods with caller-supplied `JsonNode` bodies: workspace rates/users, project rates/memberships/template, task rates, client archive, roles, member profiles.
3. Add advanced time-entry methods, with caution: `POST /workspaces/{workspaceId}/user/{userId}/time-entries` accepts `{}` and creates a blank entry, so tests/probes must use lifecycle cleanup.
4. Add time-off policy/request/balance clients from clean1 evidence, marking live behavior experimental where probes returned 400/404 with minimal bodies.

Raw response bodies are intentionally not stored; use the curl evidence note for status/content-type/shape summaries.
