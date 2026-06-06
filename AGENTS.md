# Mileage for Clockify Agent Rules

This is the standalone repository for Mileage for Clockify. It contains the add-on plus the smallest local platform modules needed to build, test, and package it outside the original add-on factory workspace.

## Specialized skill + agents for this repo

| File | Purpose |
|---|---|
| `.claude/skills/mileage-for-clockify-development/SKILL.md` | Project skill — activates on every task. Hard rules, commands, deploy/probe procedure, known production gotchas. |
| `.claude/agents/mileage-deployer.md` | Subagent — publish gate → OCI deploy by default → status monitor → hosted probes → dated evidence block. |
| `.claude/agents/mileage-webhook-smoke.md` | Subagent — live Clockify E2E webhook smoke. Never echoes secrets. |

**Meta-rule (non-negotiable).** Any change that invalidates a Non-Negotiable, Module Map entry, Hosted Verification Snapshot fact, env var, command, migration number, or metric tag MUST update `CLAUDE.md`, this file, AND the three files above in the SAME PR. Skipping the doc sync ships broken guidance to the next agent. The production traps caught during recent sessions (V7→V17 Flyway numbering, `WebhookJobWorkerConfig` `@AutoConfiguration` ordering, lifecycle reinstall cleanup, Clockify multipart `amount` vs `quantity`) all exist because earlier sessions did not update the documents alongside the code. Don't repeat that pattern.

## Start Here

1. Run `git status --short --branch`.
2. Read this file, then `CLAUDE.md`, then [README.md](README.md).
3. For product behavior, use [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md), [addon-expenses-rest-api/endpoints.md](addon-expenses-rest-api/endpoints.md), [addon-expenses-rest-api/webhooks.md](addon-expenses-rest-api/webhooks.md), [clockify-rest-client/docs/endpoint-provenance.md](clockify-rest-client/docs/endpoint-provenance.md), and the implemented tests.

## Architecture Decision: Postgres

The persistence layer stays on PostgreSQL. Asked 2026-05-30 whether to migrate to MongoDB; decision was no. Three load-bearing reasons:

1. **`SELECT … FOR UPDATE SKIP LOCKED`** is the atomic primitive the G1 async worker queue is built on; MongoDB has no clean equivalent that preserves the "row lock survives until commit so claim and process can split across transactions" property.
2. **`BigDecimal ↔ numeric`** keeps financial precision aligned without lossy conversions and supports the no-float hard rule cleanly. Multi-step transactional state transitions on `mileage_conversion` rely on Postgres rollback semantics.
3. **Flyway + `{h-schema}` + JPA** infrastructure is wired throughout; the post-V17 migration numbering rule and the per-test-schema isolation pattern depend on it. A rewrite would touch every repository, entity, and migration for zero functional gain.

Performance is nowhere near Postgres limits: 3.9 ms avg worker poll latency, 334 ms per-job process time (dominated by the Clockify HTTPS roundtrip). Revisit only if Postgres genuinely can't do something.

## Local environment file

Clockify credentials and workspace IDs live in `~/.config/clockify-mileage.env` (mode `600`), sourced from `~/.zshrc`. All five `CLOCKIFY_*` variables (workspace, user, project, base URL, API key for the sacrificial dev workspace `672f9cf4ad6f45299c3e3de2`) are set. The dev workspace auto-resets so the API key is sandbox-grade and a leak self-invalidates — don't ever paste a production key here. `NVD_API_KEY` is the only placeholder remaining (fill in to activate the CI HIGH/CRITICAL gate). The file is private (mode 600) and outside this repo. Never echo values; probe presence with `[ -n "$VAR" ] && echo set || echo MISSING`. Smoke-test at task start: `curl -sS -o /dev/null -w "%{http_code}\n" -H "X-Api-Key: $CLOCKIFY_API_KEY" "$CLOCKIFY_API_BASE_URL/user"` — 200 means good, 401 means the dev workspace reset and the key needs refreshing.

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
13. Native expense conversion must aggressively prevent loops: skip mileage audit markers, output-category expenses, and already-converted expenses before writing back to Clockify. A loop-guard webhook may increment the `SKIPPED` metric, but it must not rewrite an existing successful `mileage_conversion` row away from `CONVERTED`.
14. The Clockify REST client has no default API hosts. Builders and tests must pass explicit backend URLs, add-ons must route from verified token claims or installation context, and reports URLs may only be omitted for clients that do not use reports APIs.
15. Receipt and file uploads must use the shared Clockify client multipart helper. Do not hand-build multipart `Content-Disposition` or `Content-Type` headers; field names must be constrained and filenames/content types sanitized.
16. Webhook handling is async (G1). The `/webhook/**` controller must NOT invoke `AddonWebhookHandler.handle` or any Clockify gateway method on the request thread when a `WebhookJobQueue` bean is wired. The contract is: verify → dedupe → enqueue PENDING → 2xx. The `WebhookJobWorker` (property `mileage.worker.enabled`) is the only place that calls handlers. Admin retry stays synchronous.
17. The worker `claimNext` transaction wraps the `SELECT … FOR UPDATE SKIP LOCKED` and the status flip to CLAIMED in one transaction. Do NOT extend that transaction across the handler dispatch — the Clockify HTTP write must happen outside any DB lock so the row lock does not span a network call.
18. Prometheus counters and gauges may be tagged ONLY by stable, low-cardinality enums (`outcome`, `status`). Never tag with `userId`, `workspaceId`, `expenseId`, token values, or any identifier; Prometheus cardinality explodes and tagged identifiers leak into scrape endpoints. `MileageConversionMetricsTest` enforces this.
19. Do not lower `failBuildOnCVSS` below `7.0` in the OWASP dep-check plugin. Add a documented entry in `dependency-check-suppressions.xml` for verified false positives — never blanket-skip findings to get CI green.
20. `WebhookJobWorkerConfig` MUST remain an `@AutoConfiguration(after = AddonDbAutoConfiguration.class)` with the FQN listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Reverting to `@Configuration` (even with method-level `@ConditionalOnBean`) silently skips the worker beans in production — only the conversion counters appear; the queue gauge and worker timer never register. Verified by the 2026-05-30 deploys `33e2c56c` and `fdf6a328`; only `9d89508d` (with the auto-config ordering) registered the worker beans.
21. New Flyway migrations must be numbered AFTER the highest applied migration in production (currently V20 — the deferred note-charge reconcile column). Do not insert migrations into gaps below the production maximum — Flyway validates strict ordering and crashes boot with `Detected resolved migration not applied to database: N`. The 2026-05-30 deploy `d11e2088` crashed on exactly this when V7 was used; future migrations must continue from the current maximum.
22. Lifecycle `DELETED` cleanup for `AddonWebhookToken` MUST remain a scoped bulk DML delete by `workspace_id + addon_key`. Do not use Spring Data entity deletes or derived repository delete methods for this cleanup; Clockify reinstall races can otherwise stale-delete token entities and log `ObjectOptimisticLockingFailureException` / `StaleObjectStateException` while the lifecycle endpoint still returns 200.

## Module Map

- `addon-expenses-rest-api`: Mileage add-on application, UI, manifest, settings, webhooks, conversions, async webhook worker (`worker/` package), Prometheus metrics (`metrics/` package), printable all-expenses report (`report/` package — `ClockifyExpenseGateway.listExpensesForReport` + `ReportMerger` + `MileageReportRenderer` + `/iframe/report` controller), Dockerfile, compose file (two services — `addon` web pod and `addon-worker`), product migrations through `V20__add_mileage_note_charge_reconciled_at.sql`, and add-on docs.
- `addon-core`: Shared add-on auth, lifecycle routing, manifest controller, filters, security headers, async webhook dispatch (`WebhookController` + `WebhookJobQueue` interface).
- `addon-db`: JPA/Flyway persistence for installation context, encrypted tokens, settings, webhook tokens, webhook events, and the async webhook job queue (`AddonWebhookJob` entity + `AddonWebhookJobClaimService` + `JpaWebhookJobQueue` impl, backing migration `V17__addon_webhook_jobs.sql`; `V18__rename_webhook_job_completed_status.sql` renames the terminal queue status from `CONVERTED` to `COMPLETED`).
- `clockify-rest-client`: Typed Clockify REST client and endpoint-provenance-backed route behavior.
- `addon-testkit`: Test builders and fixtures shared by add-on/platform tests.
- `repo`: Vendored Maven artifacts for the Clockify add-on SDK.

## Current Product Facts

- Product name: `Mileage for Clockify`.
- Manifest strategy: manual schema 1.5 model in `MileageManifestV15`; do not switch to `ClockifyManifest.v1_5Builder()` unless you verify it exists locally.
- Manifest key: `mileage-for-clockify`.
- Main UI: `/iframe/mileage`; settings UI: `/iframe/settings`.
- Main user APIs: `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`.
- Main admin APIs: settings, Mileage category repair, diagnostics, categories, user options (`GET /api/mileage/options/users`), team mileage list/export, conversion list/detail/retry/export under `/api/mileage`.
- Team and Conversions admin views and their CSV exports accept an optional `userId` filter; `GET /api/mileage/options/users` (admin-only, from `gateway.listUsers`) backs the dropdown. The `userId` is used for admin *read* filtering only — never for create.
- The option endpoints degrade gracefully on a Clockify transport failure — HTTP 200 with an empty list + non-blank `warning`, not 500. A cold-start timeout is an `HttpTimeoutException` wrapped by `DefaultClockifyTransport` as a `ClockifyTransportException` (a `RuntimeException`, NOT `IOException`); `ClockifyApiException` (also `RuntimeException`) carries non-2xx. `options/categories` catches `ClockifyApiException` (401/403); `options/projects` and `options/users` catch `InterruptedException`/`IOException`/`ClockifyTransportException`/`ClockifyApiException` and let any OTHER `RuntimeException` propagate as 500 (don't mask a real bug as a transient outage). Backed by `…OptionsResponse.unavailable(warning)`; `settings.js` `loadProjects`/`loadUserOptions`/`loadCategories` surface `data.warning` via `toast(…, "error")`.
- `GET /api/mileage/mine` leaves `userName` blank for own rows instead of echoing the raw `userId`: `MileageConversionListResponse.from(page)` uses `MileageConversionDetailResponse.from(conversion, null, false)` (`userIdFallback=false`). Admin Team/Conversions keep `userIdFallback=true` so an unresolved user still shows the id.
- Both server-rendered pages declare a same-origin favicon (`<link rel="icon" type="image/png" href="/assets/mileage/icon.png">`) in `<head>` (`MileageIframeController.html`, `MileageReportRenderer.render`) so the browser's automatic `/favicon.ico` request stops 404-ing. CSP `img-src 'self'` already permits it.
- Fresh workspaces with no saved settings row default the rate to `0.725` (seeded in `MileageSettingsService.defaults()`). Existing saved rows are unchanged. Intentional interaction: since `getEffectiveSettings` returns `0.725` for a row-less workspace, "Use or Repair Mileage Category" on a brand-new workspace creates/repairs the Clockify Mileage category at `0.725` rather than adopting an existing category's `unitPrice`; the Clockify-`unitPrice` adoption path still fires for a saved row whose rate is null.
- The converted-note template is admin-editable in the Settings UI (`note_template`, textarea id `settings-note-template`, capped at 500 chars). `MileageNoteService` always appends the hidden `[MileageAddon:converted:v1 …]` marker when a custom template omits both the marker and the `Created/converted by Mileage for Clockify.` signature, so loop detection is guaranteed.
- `GET /iframe/report` is a server-rendered printable **expense report** (no PDF library; browser print-to-PDF) listing ALL Clockify expenses in the range; expenses the add-on converted (CONVERTED `mileage_conversion` matched by `expenseId`) render the add-on's reconciled miles/rate/amount + category `Mileage`, everything else renders native Clockify values. Native expenses come from `ClockifyExpenseGateway.listExpensesForReport` (backend `getExpenses`, paged + client-side date filter; rows at `response.expenses.expenses[]`, inline category/project names, `total` cents → major) merged by `ReportMerger`. Admin + no `userId` = all users (adds a User column); admin + `userId` = that user; non-admin = own (foreign `userId` ignored). Degrades to reconciled-mileage-only rows with a banner if the live expense list fails (never 500). `/iframe/**` route (auth via `auth_token` query param, external `/assets/mileage/report.css` + `report.js`, CSP forbids inline). Capped at 1000 rows with a visible truncation notice.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- DB tables: `mileage_workspace_settings`, `mileage_conversion`. `V19__trim_mileage_conversion_audit_surface.sql` removes obsolete `currency`, `raw_event_hash`, and `clockify_request_id` audit columns and removes the unused `FETCHED` status. `V20__add_mileage_note_charge_reconciled_at.sql` adds the nullable `note_charge_reconciled_at` column the deferred add-on-create note-charge reconcile sweeper stamps. Platform tables (in `addon-db`): `addon_installations`, `addon_webhook_tokens`, `addon_workspace_settings`, `addon_webhook_events`, and `addon_webhook_jobs` (G1 async queue, Flyway V17; lifecycle `PENDING → CLAIMED → COMPLETED|FAILED`). Migrations now continue from the highest applied production version to avoid Flyway out-of-order validation failures.
- Mileage create requests intentionally omit `userId`; the backend injects the verified claims user into Clockify create commands and audit rows.
- Mileage create requests intentionally omit `taskId`; the UI lists projects and categories but does not call task APIs. Native expense conversion may still preserve an existing Clockify `taskId` from webhook snapshots.
- Manual mileage expenses default to billable when `billable` is omitted. An explicit `false` still stays non-billable.
- Main-page rate override is hidden and omitted unless workspace settings allow user overrides. Backend calculation still ignores submitted override rates when the setting is off.
- Mileage settings use one `Mileage` unit category with fixed unit `mile` and fixed `HALF_UP` rounding; existing input/output category settings normalize to that single category.
- Setup can adopt an existing Clockify `Mileage` UNIT/mile category and derive the rate from Clockify `unitPrice` cents when no local rate is saved yet. Do not force a new category when the default category is already usable.
- Saving settings (`PUT /api/mileage/settings`) best-effort syncs the Clockify Mileage category's unit price to the saved rate via `MileageSettingsController.syncMileageCategoryPrice` → `gateway.createOrRepairMileageCategory(workspaceId, rate)` (only when a rate and `mileageCategoryId` are present). It is caught and logged on any Clockify failure and never fails the save. This is the primary fix for the rate↔category-price divergence (a unit category forces `total = miles × priceInCents`); for native conversions the note annotation documents the residual integer-cent rounding gap (add-on creates leave it unannotated).
- Generated Clockify notes preserve any user-typed note (prepended above the canonical line, separated by a blank line) and are otherwise exact, e.g. `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.` For **native conversions only**, the canonical line additionally explains the actual Clockify category charge whenever it differs from the addon's calculated amount — Clockify computes the unit-priced category total from the integer-cent category price, which can differ from the addon's higher-precision rate — e.g. `Mileage reimbursement: 12.4 miles x 7.25123 = 89.915252 (Clockify category charge: 89.90). Created/converted by Mileage for Clockify.` The `settings.rate()` keeps full precision (NOT rounded) and the addon's own recorded amounts (Mine/Team/Conversions) are unchanged. The charge comes from `ClockifyExpenseSnapshot.total` (cents) via `MileageConversionService.clockifyCategoryCharge`, applied in the native conversion's single worker-thread `updateFlatExpense`.
- Add-on-created expenses get the `(Clockify category charge: X)` annotation via a DEFERRED reconcile, never synchronously. `MileageApiController.createExpense` writes only the create note (a synchronous second `updateFlatExpense` to the just-created expense races Clockify's `EXPENSE_CREATED` webhook and hangs — PR #4/#5 were reverted after live QA 2026-06-05; it still fires `updateFlatExpense` ONLY for the webhook-reserved-first race to re-mark the persisted conversion id). The off-thread `MileageNoteReconcileWorker` (`@Scheduled`, registered in `WebhookJobWorkerConfig`, gated by `mileage.worker.enabled`, poll env `MILEAGE_WORKER_NOTE_RECONCILE_POLL_DELAY_MS` default 60000) sweeps ADDON_FORM + CONVERTED rows with null `note_charge_reconciled_at` and `convertedAt` between 15 min and 30 s ago (settled, past the webhook race), reads the live Clockify `total`, and on divergence inserts the parenthetical via `MileageNoteService.insertCategoryCharge` + one `updateFlatExpense` (which must pass the live snapshot's full `date` — Clockify's update endpoint rejects a date-only string with HTTP 400), then stamps `note_charge_reconciled_at`. Idempotent; transient Clockify failure leaves the row unstamped to retry. Do NOT move this back onto the create request thread. Fix 1A's category-price sync still bounds the divergence to integer-cent rounding; records stay exact. Note idempotency: a note already containing `MileageNoteService.MARKER_PREFIX` or the `Created/converted by Mileage for Clockify.` signature is returned unchanged (no re-stacking on retry/restore).
- Add-on UI tables and previews display full `calculatedAmount` decimals as the primary amount. Clockify expense writes continue to use the rounded `roundedAmount`.
- Mileage lists and CSV exports filter by `expenseDate`, defaulting to the current US week, Sunday through Saturday.
- User-facing `Mine` and admin `Team` lists/CSVs exclude `DELETED` audit rows. Admin `Conversions` keeps deleted rows visible as audit history.
- Mileage CSV exports emit `user_name` next to `user_id` and `project_name` next to `project_id`. Names are resolved live per export through `ClockifyExpenseGateway.listUsers` / `listProjects`; both helpers short-circuit when the row set contains no IDs of that kind and return an empty map on `IOException`/`RuntimeException`, leaving the name cells blank without failing the export.
- Mileage CSV export buttons are handled through the delegated `handleCsvExport` click handler in `settings.js`. Keep Mine, Team, and Conversions exports on that shared path; a 2026-06-04 live pass caught the Conversions CSV button no-oping when export listeners were attached only per button.
- Expense webhook handlers that need an expense ID accept either `id` or `expenseId` payload shapes. This includes updated/deleted webhooks, which have arrived as full payloads in live Clockify testing.
- Webhook dispatch records/dedupes events, marks processing outcomes internally, and still returns HTTP 2xx after handler or audit-status failures.
- Webhook handling is asynchronous (G1). `/webhook/**` verifies, dedupes, and persists a PENDING row in `addon_webhook_jobs`, then returns 2xx with zero Clockify writes on the request thread. `WebhookJobWorker` (gated by `mileage.worker.enabled`, default `true`) claims rows via `SELECT … FOR UPDATE SKIP LOCKED`, runs `tryStartProcessing` for the loop-prevention guard, and dispatches to the same typed `AddonWebhookHandler` chain as the legacy sync path. A second `@Scheduled` reaper resets CLAIMED rows older than the configured timeout back to PENDING. The controller falls back to synchronous dispatch when no queue bean is wired (legacy tests with mocked DB).
- `WebhookJobWorkerConfig` is an `@AutoConfiguration(after = AddonDbAutoConfiguration.class)` registered via `addon-expenses-rest-api/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. A plain `@Configuration` skips the worker beans in production even with `@ConditionalOnBean` at @Bean method level — the 2026-05-30 deploy chain demonstrated this. The auto-config ordering is the only reliable fix.
- `AddonWebhookToken` lifecycle cleanup uses idempotent bulk DML for `workspace_id + addon_key`; do not route it through JPA entity delete semantics.
- Worker liveness in production is observable via Spring Boot's auto-bound `tasks_scheduled_execution_seconds_count{code_function="pollAndProcess",outcome="SUCCESS",exception="none"}` and `code_function="reapStuckJobs"` counters on `/actuator/prometheus`. A growing count with `exception="none"` proves the poll loop is hitting the database successfully even when the queue is empty (the per-job `mileage_webhook_job_process_seconds_count` stays at zero until a real webhook lands, so it is NOT a standalone liveness signal).
- Docker compose runs two services from the same image (G1): `addon` web pod (`MILEAGE_WORKER_ENABLED=false`) and `addon-worker` (no port mapping, default worker on). Scale workers horizontally with `docker compose up --scale addon-worker=N`.
- Prometheus metrics live behind `/actuator/prometheus`. Counters: `mileage_conversion_outcome_total{outcome=…}` from `MileageConversionMetrics`. Gauge: `mileage_webhook_queue_depth{status=PENDING}` from `WebhookJobMetrics`. Timer: `mileage_webhook_job_process` from `WebhookJobWorker`. HTTP enqueue timing is covered by Spring Boot's auto-bound `http_server_requests_seconds`.
- Hikari pool is sized explicitly in `application.yaml` (G2): `maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=30000` ms; each is env-overridable via `SPRING_DATASOURCE_HIKARI_*`.
- OWASP `dependency-check-maven` 10.0.4 is wired in root `pluginManagement` with `failBuildOnCVSS=7.0` (G4). Suppression registry at `dependency-check-suppressions.xml`. CI runs the gate in the `dep-check` job with `NVD_API_KEY` from secrets and an `actions/cache` step for the NVD data dir; local-only runs are impractical without an NVD key and are deferred to CI.
- Native conversion eligibility skips disabled/incomplete settings, workspace mismatches, output categories, mileage note markers, existing successful conversions, non-input categories, missing/invalid quantity, and locked/finalized expenses. Existing successful conversions stay `CONVERTED`/`CONVERTING` in the audit table when a later loop-guard webhook is skipped; only metrics record the skipped outcome.
- `ClockifyClientFactory` builds per-workspace clients from installed backend/reports URLs. Missing backend URL is fatal; missing reports URL is allowed until a reports request is attempted.
- Clockify token timezone normalization accepts `userTimeZone`, `userTimezone`, `timeZone`, `timezone`, and `tz`; keep frontend timezone alias handling aligned with `ClaimsNormalizer`.
- The settings UI loads `/assets/mileage/settings-date.js` before `/assets/mileage/settings.js`. Keep date presets/default create dates in that helper so Clockify claim timezones stay aligned with backend default ranges.
- After any deploy that touches mileage static assets, probe both `/assets/mileage/settings-date.js` and `/assets/mileage/settings.js`; a single settings asset probe is not enough.
- Receipt uploads in `clockify-rest-client` centralize multipart body construction so Expenses and Files clients share field-name validation, filename sanitization, and content-type fallback behavior.
- Spring multipart limits are pinned in `application.yaml` at 10 MB per file and 12 MB per request, matching the 10 MB cap enforced by `MileageApiController`. `MileageExceptionHandler.handleMaxUploadSize` maps the servlet-level `MaxUploadSizeExceededException` to a 400 `Receipt file exceeds 10 MB` body so the failure mode is identical at both layers.
- The optional `clockify-rest-client` Spring MVC facade and WebClient transport were removed as dead/bloated surfaces. Do not reintroduce global proxy controllers around the typed client.
- Historical pre-Mileage migrations V5/V10 are retained for Flyway validation only; V12 drops their leftover generic tables. Do not add new `temp_addon_expenses*`, `clockify-expenses-api`, `Clockify Expenses API`, or `com.cake.clockify.addon.expenses` references.

## Hosted Verification Snapshot

- Current hosted add-on URL: `https://89-168-93-85.sslip.io`.
- Current hosted manifest URL: `https://89-168-93-85.sslip.io/manifest`.
- Current hosted runtime: OCI VM `mileage-for-clockify-e2`, systemd service `mileage-for-clockify.service`, Java jar at `/opt/mileage-for-clockify/mileage-for-clockify.jar`, Caddy reverse proxy.
- Railway is historical unless explicitly restored; Cloudflared remains the local live-test fallback. Quick-tunnel URLs are ephemeral; paste the printed `/manifest` URL into Clockify after every restart and do not treat a previous `trycloudflare.com` hostname as current truth.
- If Railway is explicitly used again, run `railway deployment list` for that run's deployment ID. Do not treat old deployment IDs in notes, chats, or previous evidence as current truth.
- Dated deployment evidence belongs in the pre-publish checklist after each deploy.
- Pre-deploy hosted recheck, dated 2026-05-27: `/actuator/health` and `/manifest` passed, but `/assets/mileage/settings-date.js` returned `404`, proving production was still serving an older deployment.
- Post-deploy hosted recheck, dated 2026-05-27: `/actuator/health`, `/manifest`, `/assets/mileage/settings-date.js`, `/assets/mileage/settings.js`, `/assets/mileage/icon.png`, and unauthenticated `/iframe/mileage` probes passed. Future deploys that touch static assets must rerun both settings JS asset probes.
- Post-deploy hosted recheck, dated 2026-05-28, Railway deployment `2287245e-a4cf-4bf0-ab0f-fa4d94566b93` at git `3fbe57c`: same six probes passed after shipping CSV `project_name` enrichment and pinning Spring multipart limits. `/iframe/mileage` still returned 401 with the unchanged CSP/HSTS/Permissions-Policy header set.
- Post-deploy hosted recheck, dated 2026-05-30, Railway deployment `9d89508d-9592-45df-b2dc-4b650d38fb10` at git `011d4e8` (closes G1–G4 scale gaps after three production-only fixes during the deploy session: Flyway V17 renumber, worker `@ConditionalOnBean` move, final `@AutoConfiguration(after=AddonDbAutoConfiguration)`). Six baseline probes green; Flyway boot log shows `Migrating schema "public" to version "17 - addon webhook jobs"`. `/actuator/prometheus` exposes the new metric families (`mileage_conversion_outcome_total` for all 9 statuses, `mileage_webhook_queue_depth{status="PENDING"}=0`, `mileage_webhook_job_process_seconds_*` registered with count 0). Worker liveness proven by `tasks_scheduled_execution_seconds_count{code_function="pollAndProcess",outcome="SUCCESS",exception="none"}=977` (~4 Hz, matching `MILEAGE_WORKER_POLL_DELAY_MS=250`) and `code_function="reapStuckJobs"=5` (~1/min) over ~4 min uptime. Avg poll latency 3.9 ms, max 12.8 ms. No PII tags on any `mileage_` metric line.
- Live Clockify E2E webhook smoke, dated 2026-05-30, against deployment `9d89508d` from sacrificial developer workspace `672f9cf4ad6f45299c3e3de2`. One Mileage expense at 12.4 mi × $7.25/mi traversed: Clockify `EXPENSE_CREATED` → 200 OK on `/webhook/**` → controller enqueue → worker claim → `MileageConversionService.convertIfEligible` → Clockify update with the canonical note `"Mileage reimbursement: 12.4 miles x 7.25123 = 89.915252. Created/converted by Mileage for Clockify…"` (`total=8990` cents). The addon's own update triggered a second `EXPENSE_UPDATED` webhook which the loop-prevention guard correctly refused (`outcome="SKIPPED"` with `ALREADY_CONVERTED`). Cleanup delete fired `EXPENSE_DELETED` → `markDeleted` → `outcome="DELETED"`. Deltas: `/webhook/**` POSTs +3, worker timer count +3, `CONVERTED` +1, `SKIPPED` +1 (loop guard), `DELETED` +2. Per-job latency avg 334 ms, max 805 ms (CONVERTED makes two Clockify roundtrips). Queue depth stayed 0; zero exception tags on worker invocations. Sacrificial expenses deleted; post-delete GET returns `400 "Expense doesn't belong to Workspace"`.
- Cloudflared/Docker live E2E, dated 2026-05-31, against dev workspace `69bda6b317a0c5babe34b4ff`: rebuilt local image with the loop-guard fix, tunnel manifest returned schema `1.5` with key `mileage-for-clockify`, health `UP`, worker scheduler exposed `pollAndProcess`/`reapStuckJobs`, and the installed Clockify iframe loaded with no browser console warnings/errors. UI create proved preview/create/Mine/Settings/Conversions/Diagnostics; a race bug where the add-on-created row was overwritten to `WEBHOOK_CREATED/SKIPPED` was fixed so later loop-guard webhooks record `SKIPPED` metrics without mutating the successful `ADDON_FORM/CONVERTED` row. Native Clockify create at 5.6 miles produced `CONVERTED +1`, loop-guard `SKIPPED +1`, worker timer `+2`, queue depth `0`, and a canonical note with `(Clockify category charge: 0.73)`. Cleanup deleted all three sacrificial expenses, `DELETED +3`, worker timer `+3`, queue depth `0`, and post-delete GET returned `400`.
- Clockify multipart-create field convention: for unit-priced expense categories (e.g. Mileage with `hasUnitPrice=true`, `unit="mile"`), the Clockify expense-create API takes the miles value in the `amount` field, NOT `quantity`. Clockify computes `total = amount × unitPrice` and writes the resulting `quantity` back. Sending `quantity=N` silently records `quantity=0` with no error. The addon's `ClockifyExpenseGateway.createBody` already uses `amount` correctly; this note is for future ad-hoc smoke hitting Clockify's API directly.
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

If Testcontainers cannot find Docker on this Mac, force Maven onto Docker Desktop:

```bash
DOCKER_HOST=unix:///Users/15x/.docker/run/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test
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
