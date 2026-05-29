# Mileage for Clockify Agent Rules

This is the standalone repository for Mileage for Clockify. It contains the add-on plus the smallest local platform modules needed to build, test, and package it outside the original add-on factory workspace.

## Start Here

1. Run `git status --short --branch`.
2. Read this file, then `CLAUDE.md`, then [README.md](README.md).
3. For product behavior, use [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md), [addon-expenses-rest-api/endpoints.md](addon-expenses-rest-api/endpoints.md), [addon-expenses-rest-api/webhooks.md](addon-expenses-rest-api/webhooks.md), [clockify-rest-client/docs/endpoint-provenance.md](clockify-rest-client/docs/endpoint-provenance.md), and the implemented tests.

## Non-Negotiables

1. Do not guess Clockify API shapes. Prefer typed client tests, endpoint provenance docs, and live sacrificial-workspace evidence only when explicitly permitted.
2. Never edit or rely on committing `addon-expenses-rest-api/addon-java-sdk/`; it is a read-only ignored local SDK clone.
3. Keep `addon-core`, `addon-db`, `clockify-rest-client`, and `addon-testkit` changes conservative. Stop and confirm before structural platform changes.
4. Use Java 21 `record` DTOs when adding new DTOs unless an existing local pattern clearly differs.
5. All mileage, rate, and money values must use `BigDecimal` or SQL `numeric`. Never use floating point for those domain values.
6. Never hardcode Clockify API hosts in add-on code. Use token or installation context through the platform/client services.
7. Never expose installation tokens to frontend code, logs, docs, screenshots, or test output.
8. Preserve workspace isolation in every repository query, service method, webhook path, and Clockify API call.
9. User-facing mileage creation must use the verified user ID from Clockify token claims, not a frontend or request-supplied `userId`. Do not add `userId` back to the create request DTO, multipart allowlist, iframe form, or frontend payload.
10. User-facing mileage creation follows Clockify's regular expense form shape and does not require or fetch tasks. Do not add a task selector, task options endpoint, `taskId` create-field, or `TASK_READ` manifest scope unless the product requirement changes and live scope evidence is captured first.
11. Main-page rate override is settings-gated. Keep `/api/mileage/create-context`, server-side rate override enforcement, and frontend visibility in sync.
12. Webhook handlers must acknowledge safely with HTTP 2xx after internal failure recording/logging. Do not let Clockify blindly retry failures that should be retried from the admin/internal path.
13. Native expense conversion must aggressively prevent loops: skip mileage audit markers, output-category expenses, and already-converted expenses before writing back to Clockify.
14. The Clockify REST client has no default API hosts. Builders and tests must pass explicit backend URLs, add-ons must route from verified token claims or installation context, and reports URLs may only be omitted for clients that do not use reports APIs.
15. Receipt and file uploads must use the shared Clockify client multipart helper. Do not hand-build multipart `Content-Disposition` or `Content-Type` headers; field names must be constrained and filenames/content types sanitized.
16. Webhook handling is async (G1). The `/webhook/**` controller must NOT invoke `AddonWebhookHandler.handle` or any Clockify gateway method on the request thread when a `WebhookJobQueue` bean is wired. The contract is: verify → dedupe → enqueue PENDING → 2xx. The `WebhookJobWorker` (Spring profile / property `mileage.worker.enabled`) is the only place that calls handlers. Admin retry stays synchronous.
17. The worker `claimNext` transaction wraps the `SELECT … FOR UPDATE SKIP LOCKED` and the status flip to CLAIMED in one transaction. Do NOT extend that transaction across the handler dispatch — the Clockify HTTP write must happen outside any DB lock so the row lock does not span a network call.
18. Prometheus counters and gauges may be tagged ONLY by stable, low-cardinality enums (`outcome`, `status`). Never tag with `userId`, `workspaceId`, `expenseId`, token values, or any identifier; Prometheus cardinality explodes and tagged identifiers leak into scrape endpoints. `MileageConversionMetricsTest` enforces this.
19. Do not lower `failBuildOnCVSS` below `7.0` in the OWASP dep-check plugin. Add a documented entry in `dependency-check-suppressions.xml` for verified false positives — never blanket-skip findings to get CI green.

## Module Map

- `addon-expenses-rest-api`: Mileage add-on application, UI, manifest, settings, webhooks, conversions, async webhook worker (`worker/` package), Prometheus metrics (`metrics/` package), Dockerfile, compose file (two services — `addon` web pod and `addon-worker`), and add-on docs.
- `addon-core`: Shared add-on auth, lifecycle routing, manifest controller, filters, security headers, async webhook dispatch (`WebhookController` + `WebhookJobQueue` interface).
- `addon-db`: JPA/Flyway persistence for installation context, encrypted tokens, settings, webhook tokens, webhook events, and the async webhook job queue (`AddonWebhookJob` entity + `AddonWebhookJobClaimService` + `JpaWebhookJobQueue` impl, backing migration `V17__addon_webhook_jobs.sql` — numbered V17 to land after the existing applied V10–V16 mileage migrations).
- `clockify-rest-client`: Typed Clockify REST client and endpoint-provenance-backed route behavior.
- `addon-testkit`: Test builders and fixtures shared by add-on/platform tests.
- `repo`: Vendored Maven artifacts for the Clockify add-on SDK.

## Current Product Facts

- Product name: `Mileage for Clockify`.
- Manifest strategy: manual schema 1.5 model in `MileageManifestV15`; do not switch to `ClockifyManifest.v1_5Builder()` unless you verify it exists locally.
- Manifest key: `mileage-for-clockify`.
- Main UI: `/iframe/mileage`; settings UI: `/iframe/settings`.
- Main user APIs: `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`.
- Main admin APIs: settings, Mileage category repair, diagnostics, categories, team mileage list/export, conversion list/detail/retry/export under `/api/mileage`.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- DB tables: `mileage_workspace_settings`, `mileage_conversion`. Platform tables (in `addon-db`): `addon_installations`, `addon_webhook_tokens`, `addon_workspace_settings`, `addon_webhook_events`, and `addon_webhook_jobs` (G1 async queue, Flyway V17). Platform `addon-db` migrations now start with V1–V6 and skip to V17 because production already had V10–V16 applied from the mileage module — new platform tables must be numbered after the highest applied mileage migration to avoid Flyway out-of-order validation failures.
- Mileage create requests intentionally omit `userId`; the backend injects the verified claims user into Clockify create commands and audit rows.
- Mileage create requests intentionally omit `taskId`; the UI lists projects and categories but does not call task APIs. Native expense conversion may still preserve an existing Clockify `taskId` from webhook snapshots.
- Manual mileage expenses default to billable when `billable` is omitted. An explicit `false` still stays non-billable.
- Main-page rate override is hidden and omitted unless workspace settings allow user overrides. Backend calculation still ignores submitted override rates when the setting is off.
- Mileage settings use one `Mileage` unit category with fixed unit `mile` and fixed `HALF_UP` rounding; existing input/output category settings normalize to that single category.
- Setup can adopt an existing Clockify `Mileage` UNIT/mile category and derive the rate from Clockify `unitPrice` cents when no local rate is saved yet. Do not force a new category when the default category is already usable.
- Generated Clockify notes are clean and exact, e.g. `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.`
- Add-on UI tables and previews display full `calculatedAmount` decimals as the primary amount. Clockify expense writes continue to use the rounded `roundedAmount`.
- Mileage lists and CSV exports filter by `expenseDate`, defaulting to the current US week, Sunday through Saturday.
- User-facing `Mine` and admin `Team` lists/CSVs exclude `DELETED` audit rows. Admin `Conversions` keeps deleted rows visible as audit history.
- Mileage CSV exports emit `user_name` next to `user_id` and `project_name` next to `project_id`. Names are resolved live per export through `ClockifyExpenseGateway.listUsers` / `listProjects`; both helpers short-circuit when the row set contains no IDs of that kind and return an empty map on `IOException`/`RuntimeException`, leaving the name cells blank without failing the export.
- Expense webhook handlers that need an expense ID accept either `id` or `expenseId` payload shapes. This includes updated/deleted webhooks, which have arrived as full payloads in live Clockify testing.
- Webhook dispatch records/dedupes events, marks processing outcomes internally, and still returns HTTP 2xx after handler or audit-status failures.
- Webhook handling is asynchronous (G1). `/webhook/**` verifies, dedupes, and persists a PENDING row in `addon_webhook_jobs`, then returns 2xx with zero Clockify writes on the request thread. `WebhookJobWorker` (gated by `mileage.worker.enabled`, default `true`) claims rows via `SELECT … FOR UPDATE SKIP LOCKED`, runs `tryStartProcessing` for the loop-prevention guard, and dispatches to the same typed `AddonWebhookHandler` chain as the legacy sync path. A second `@Scheduled` reaper resets CLAIMED rows older than the configured timeout back to PENDING. The controller falls back to synchronous dispatch when no queue bean is wired (legacy tests with mocked DB).
- Docker compose runs two services from the same image (G1): `addon` web pod (`MILEAGE_WORKER_ENABLED=false`) and `addon-worker` (no port mapping, default worker on). Scale workers horizontally with `docker compose up --scale addon-worker=N`.
- Prometheus metrics live behind `/actuator/prometheus`. Counters: `mileage_conversion_outcome_total{outcome=…}` from `MileageConversionMetrics`. Gauge: `mileage_webhook_queue_depth{status=PENDING}` from `WebhookJobMetrics`. Timer: `mileage_webhook_job_process` from `WebhookJobWorker`. HTTP enqueue timing is covered by Spring Boot's auto-bound `http_server_requests_seconds`.
- Hikari pool is sized explicitly in `application.yaml` (G2): `maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=30000` ms; each is env-overridable via `SPRING_DATASOURCE_HIKARI_*`.
- OWASP `dependency-check-maven` 10.0.4 is wired in root `pluginManagement` with `failBuildOnCVSS=7.0` (G4). Suppression registry at `dependency-check-suppressions.xml`. CI runs the gate in the `dep-check` job with `NVD_API_KEY` from secrets and an `actions/cache` step for the NVD data dir; local-only runs are impractical without an NVD key and are deferred to CI.
- Native conversion eligibility skips disabled/incomplete settings, workspace mismatches, output categories, mileage note markers, existing successful conversions, non-input categories, missing/invalid quantity, and locked/finalized expenses.
- `ClockifyClientFactory` builds per-workspace clients from installed backend/reports URLs. Missing backend URL is fatal; missing reports URL is allowed until a reports request is attempted.
- Clockify token timezone normalization accepts `userTimeZone`, `userTimezone`, `timeZone`, `timezone`, and `tz`; keep frontend timezone alias handling aligned with `ClaimsNormalizer`.
- The settings UI loads `/assets/mileage/settings-date.js` before `/assets/mileage/settings.js`. Keep date presets/default create dates in that helper so Clockify claim timezones stay aligned with backend default ranges.
- After any deploy that touches mileage static assets, probe both `/assets/mileage/settings-date.js` and `/assets/mileage/settings.js`; a single settings asset probe is not enough.
- Receipt uploads in `clockify-rest-client` centralize multipart body construction so Expenses and Files clients share field-name validation, filename sanitization, and content-type fallback behavior.
- Spring multipart limits are pinned in `application.yaml` at 10 MB per file and 12 MB per request, matching the 10 MB cap enforced by `MileageApiController`. `MileageExceptionHandler.handleMaxUploadSize` maps the servlet-level `MaxUploadSizeExceededException` to a 400 `Receipt file exceeds 10 MB` body so the failure mode is identical at both layers.
- The optional `clockify-rest-client` Spring MVC facade and WebClient transport were removed as dead/bloated surfaces. Do not reintroduce global proxy controllers around the typed client.
- Historical pre-Mileage migrations V5/V10 are retained for Flyway validation only; V12 drops their leftover generic tables. Do not add new `temp_addon_expenses*`, `clockify-expenses-api`, `Clockify Expenses API`, or `com.cake.clockify.addon.expenses` references.

## Hosted Verification Snapshot

- Current hosted add-on URL: `https://mileage-for-clockify-production.up.railway.app`.
- Current hosted manifest URL: `https://mileage-for-clockify-production.up.railway.app/manifest`.
- Use `railway deployment list` for the current Railway deployment ID. Do not treat old deployment IDs in notes, chats, or previous evidence as current truth.
- Dated deployment evidence belongs in the pre-publish checklist after each deploy.
- Pre-deploy hosted recheck, dated 2026-05-27: `/actuator/health` and `/manifest` passed, but `/assets/mileage/settings-date.js` returned `404`, proving production was still serving an older deployment.
- Post-deploy hosted recheck, dated 2026-05-27: `/actuator/health`, `/manifest`, `/assets/mileage/settings-date.js`, `/assets/mileage/settings.js`, `/assets/mileage/icon.png`, and unauthenticated `/iframe/mileage` probes passed. Future deploys that touch static assets must rerun both settings JS asset probes.
- Post-deploy hosted recheck, dated 2026-05-28, Railway deployment `2287245e-a4cf-4bf0-ab0f-fa4d94566b93` at git `3fbe57c`: same six probes passed after shipping CSV `project_name` enrichment and pinning Spring multipart limits. `/iframe/mileage` still returned 401 with the unchanged CSP/HSTS/Permissions-Policy header set.
- Historical live Clockify smoke, dated 2026-05-27: uninstall/install/settings/create/delete passed after the deleted-expense webhook fix. Treat this as historical unless rerun.
- Expanded live Clockify API smoke on 2026-05-27 used local environment secrets only and proved workspace/user/category read probes plus sacrificial Mileage receipt expense create, fetch, full update, delete, and post-delete non-success (`400`). Follow-up receipt probes created sacrificial PNG and valid generated PDF receipts, observed `fileId`, downloaded nonzero binary content through `GET /expenses/{expenseId}/files/{fileId}`, then deleted both expenses. A malformed hand-written PDF fixture returned zero bytes and should not be used as proof of product behavior. Never persist dev API keys in docs, logs, or commits.
- Local hardening review on 2026-05-27 covered multipart receipt/header sanitization, shared file-upload behavior, server/frontend timezone alias parity, date-helper static asset verification, and secret-scan proof.
- Live Clockify smoke is optional and requires local secrets. Never commit or echo API keys/tokens. If not run, final output must say it was skipped.

## Commands

Run from the repository root.

```bash
./scripts/verify-publish.sh
mvn -pl addon-expenses-rest-api -am test
mvn -pl addon-expenses-rest-api -am clean test
docker compose -f addon-expenses-rest-api/docker-compose.yml build
```

If Testcontainers cannot find Docker on this Mac, force Maven onto Colima:

```bash
DOCKER_HOST=unix:///Users/15x/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test \
  -Ddocker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy \
  -Ddocker.host=unix:///Users/15x/.colima/default/docker.sock \
  -Dapi.version=1.44
```

Before Marketplace submission, also complete [addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md](addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md).

Use this stale/dead-code scan after documentation or migration cleanup:

```bash
rg -n "clockify-expenses-api|Clockify Expenses API|com\\.cake\\.clockify\\.addon\\.expenses|temp_addon_expenses|temp-addon-expenses|API_TEST_|CLOCKIFY_API_KEY:-|test-suite\\.sh" \
  addon-expenses-rest-api/src/main addon-expenses-rest-api/src/test/resources addon-expenses-rest-api/pom.xml \
  -g '!**/target/**' -g '!addon-expenses-rest-api/src/main/resources/db/migration/V5__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V10__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V12__*' \
  -g '!addon-expenses-rest-api/MARKETPLACE_OCS/**'
```

If local port `5432` is already in use, keep Postgres internal while running the Docker stack:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') up -d
curl -fsS http://localhost:8080/manifest
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') down
```

## Editing Guidance

- Use small, focused diffs.
- Use `apply_patch` for manual edits.
- Do not commit unless explicitly asked.
- Do not weaken tests to make verification pass.
- After functional changes, run the focused test first, then `mvn -pl addon-expenses-rest-api -am test`.
- After manifest, Docker, or runtime config changes, also run the Docker build and `/manifest` probe.
