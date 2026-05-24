# REST controller OpenAPI coverage

Source OpenAPI: official scraped OpenAPI under `dev-docs/official-docs/02-openapi-and-events/`

The Spring MVC `ClockifyRestController` is disabled by default and exposes supported controller mappings only when `clockify.rest-controller.enabled=true`:

- Paths: 126
- Official operations: 191
- Add-on prose endpoints: 2
- Controller mappings: 193
- Duplicate mappings: 0
- Missing OpenAPI operations: 0

Coverage mode:

- Existing hand-written typed client methods are still used for the earlier provenanced domains.
- The remaining OpenAPI operations are exposed as a thin raw-facade layer that delegates to `ClockifyRawClient`.
- Generated raw-facade methods do not add Clockify business logic or inferred request/response models.
- JSON operations forward path variables, query parameters, and optional JSON bodies.
- Binary operations route through `ClockifyRawClient.sendBinary`.
- Base URL families are selected by route family:
  - backend API routes -> `BACKEND`
  - reports/shared-report routes -> `REPORTS`
- Audit logs are intentionally not exposed; live-only/raw-mirror evidence is not enough to generate supported add-on methods.
- Raw user-token exchange is intentionally not exposed by the facade; keep token exchange server-side through `UsersClient.exchangeUserToken`.

Go MCP reference used:

- `/Users/15x/Downloads/WORKING/addons-me/goclmcp/AGENTS.md`
- `/Users/15x/Downloads/WORKING/addons-me/goclmcp/internal/tools/raw_allowlist.go`
- `/Users/15x/Downloads/WORKING/addons-me/goclmcp/internal/tools/reports_api.go`
- `/Users/15x/Downloads/WORKING/addons-me/goclmcp/internal/tools/invoices.go`

Important caveat: full typed Java domain coverage is still smaller than controller coverage. The controller now covers the OpenAPI surface as a REST/raw facade; adding fully typed Java request/response models for each domain remains separate work and still requires endpoint provenance rows and evidence.
