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

## Current Hardening Checkpoint

Recent product-hardening work changed several operational contracts. Verify these before editing nearby code:

- Stale CLAIMED webhook jobs are reaped back to PENDING and paired webhook events are reopened from `PROCESSING`.
- Settings rate validation is centralized through the same decimal bounds as create/preview; settings save may return `warnings` when Clockify category price sync fails.
- Diagnostics includes setup checklist items plus webhook queue health, while no-DB app contexts must still start with zeroed operational health.
- Note idempotency trusts the hidden marker or canonical generated mileage line, not public signature text alone.
- `/iframe/report` degrades only for Clockify/API failures; internal merge/render bugs remain 500s.
- The Conversions table renders `SKIPPED` rows with plain-language skip reason labels.
- `addon-expenses-rest-api/docs/report-export-scale-spike.md` records why async report/export jobs were deferred.
- Effective-dated rate policies resolve by expense date for preview/create/native conversion; user overrides remain settings-gated and audited as their own rate source.
- Reimbursement packets (`/iframe/reimbursement-packet`, `/api/mileage/reimbursement-packet.csv`) use audit rows as the reimbursement truth and strip `auth_token` with `packet.js`.
- Trip evidence fields are audit-only, capped/validated on create, and are not appended to Clockify notes.
- Admin Insights reads `mileage_conversion` rows in-memory and must not add Prometheus tags.
- Report currency is sourced from live Clockify expenses only; blank/unknown add-on rows remain visible when a currency filter is active.

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
21. New Flyway migrations must be numbered AFTER the highest migration already present in the repo and after the highest applied production migration. Production applied product migrations through `V24__add_mileage_trip_evidence.sql` on the 2026-06-16 OCI deploy at git `a540d30`; the next new migration must be V25 or higher. Do not insert migrations into gaps below either maximum — Flyway validates strict ordering and crashes boot with `Detected resolved migration not applied to database: N`. The 2026-05-30 deploy `d11e2088` crashed on exactly this when V7 was used.
22. Lifecycle `DELETED` cleanup for `AddonWebhookToken` MUST remain a scoped bulk DML delete by `workspace_id + addon_key`. Do not use Spring Data entity deletes or derived repository delete methods for this cleanup; Clockify reinstall races can otherwise stale-delete token entities and log `ObjectOptimisticLockingFailureException` / `StaleObjectStateException` while the lifecycle endpoint still returns 200.

## Module Map

- `addon-expenses-rest-api`: Mileage add-on application, UI, manifest, settings, webhooks, conversions, rate policies (`policy/` package), workflow preflight (`workflow/` package), reimbursement packets (`packet/` package), admin insights (`insights/` package), async webhook worker (`worker/` package), Prometheus metrics (`metrics/` package), printable all-expenses report (`report/` package — `ClockifyExpenseGateway.listExpensesForReport` + `ReportMerger` + `MileageReportRenderer` + `/iframe/report` controller), Dockerfile, compose file (two services — `addon` web pod and `addon-worker`), product migrations through `V24__add_mileage_trip_evidence.sql`, and add-on docs.
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
- Main user APIs: `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`, `GET /iframe/reimbursement-packet`, `GET /api/mileage/reimbursement-packet.csv`.
- Main admin APIs: settings, Mileage category repair, rate policies (`/api/mileage/rate-policies`), diagnostics, categories, user options (`GET /api/mileage/options/users`), team mileage list/export, conversion list/detail/retry/export, reimbursement packet export, and insights (`GET /api/mileage/insights`) under `/api/mileage`. Diagnostics returns readiness booleans, warning messages, first-run checklist items, and webhook operational health (`pendingJobs`, `claimedJobs`, `failedJobs`, `oldestPendingAgeSeconds`, `lastCompletedJobAt`); stale pending jobs and failed jobs become warnings.
- Team and Conversions admin views and their CSV exports accept an optional `userId` filter; `GET /api/mileage/options/users` (admin-only, from `gateway.listUsers`) backs the dropdown. The `userId` is used for admin *read* filtering only — never for create.
- The option endpoints degrade gracefully on a Clockify transport failure — HTTP 200 with an empty list + non-blank `warning`, not 500. A cold-start timeout is an `HttpTimeoutException` wrapped by `DefaultClockifyTransport` as a `ClockifyTransportException` (a `RuntimeException`, NOT `IOException`); `ClockifyApiException` (also `RuntimeException`) carries non-2xx. `options/categories` catches `ClockifyApiException` (401/403); `options/projects` and `options/users` catch `InterruptedException`/`IOException`/`ClockifyTransportException`/`ClockifyApiException` and let any OTHER `RuntimeException` propagate as 500 (don't mask a real bug as a transient outage). Backed by `…OptionsResponse.unavailable(warning)`; split settings bundle functions `loadCategories`/`loadProjects`/`loadUserOptions` surface `data.warning` via `toast(…, "error")`.
- `GET /api/mileage/mine` leaves `userName` blank for own rows instead of echoing the raw `userId`: `MileageConversionListResponse.from(page)` uses `MileageConversionDetailResponse.from(conversion, null, false)` (`userIdFallback=false`). Admin Team/Conversions keep `userIdFallback=true` so an unresolved user still shows the id.
- Both server-rendered pages declare a same-origin favicon (`<link rel="icon" type="image/png" href="/assets/mileage/icon.png">`) in `<head>` (`MileageIframeController.html`, `MileageReportRenderer.render`) so the browser's automatic `/favicon.ico` request stops 404-ing. CSP `img-src 'self'` already permits it.
- Fresh workspaces with no saved settings row default the rate to `0.725` (seeded in `MileageSettingsService.defaults()`). Existing saved rows are unchanged. Intentional interaction: since `getEffectiveSettings` returns `0.725` for a row-less workspace, "Use or Repair Mileage Category" on a brand-new workspace creates/repairs the Clockify Mileage category at `0.725` rather than adopting an existing category's `unitPrice`; the Clockify-`unitPrice` adoption path still fires for a saved row whose rate is null.
- The converted-note template is admin-editable in the Settings UI (`note_template`, textarea id `settings-note-template`, capped at 500 chars). `MileageNoteService` uses the hidden marker or the canonical default `"Mileage reimbursement: ... x ... = ..."` line as idempotency proof. Public signature text alone is not trusted. Custom templates that do not render the canonical default line should include `{{marker}}` or the service appends the marker automatically.
- `GET /iframe/report` is a server-rendered printable **expense report** (no PDF library; browser print-to-PDF) listing ALL Clockify expenses in the range; expenses the add-on converted (CONVERTED `mileage_conversion` matched by `expenseId`) render the add-on's reconciled miles/rate/amount + category `Mileage`, everything else renders native Clockify values. Native expenses come from `ClockifyExpenseGateway.listExpensesForReport` (backend `getExpenses`, paged + client-side date filter, `total` cents → major, currency code best-effort) and merge through `ReportMerger`. Admin + no `userId` = all users; admin + `userId` = that user; non-admin = own. Single-user labels are resolved server-side from Clockify users, so report links must not carry client-supplied display names. Degrades to reconciled-mileage-only rows with a banner if the live expense list fails (never 500). `/iframe/**` authenticates by `auth_token`; `report.js` strips it after load. Amount and Total render money at fixed **2 dp** (`HALF_UP`); Miles/Rate keep natural precision. A Currency column appears when live rows carry currency or a currency filter is active; rows with blank/unknown currency remain visible for add-on audit rows. Capped at 1000 rows with a visible truncation notice.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- DB tables: `mileage_workspace_settings`, `mileage_conversion`, `mileage_rate_policy`. `V19__trim_mileage_conversion_audit_surface.sql` removes obsolete `currency`, `raw_event_hash`, and `clockify_request_id` audit columns and removes the unused `FETCHED` status. `V20__add_mileage_note_charge_reconciled_at.sql` adds the nullable `note_charge_reconciled_at` column the deferred add-on-create note-charge reconcile sweeper stamps. `V21__index_mileage_note_reconcile_pending.sql` adds the pending-reconcile partial index. `V22__create_mileage_rate_policies.sql` adds effective-dated policies; `V23__add_mileage_rate_policy_audit_columns.sql` stores `rate_source` and policy identity on audit rows; `V24__add_mileage_trip_evidence.sql` stores optional trip evidence. Hosted evidence from the 2026-06-16 OCI deploy proves production applied through V24. Platform tables (in `addon-db`): `addon_installations`, `addon_webhook_tokens`, `addon_workspace_settings`, `addon_webhook_events`, and `addon_webhook_jobs` (G1 async queue, Flyway V17; lifecycle `PENDING → CLAIMED → COMPLETED|FAILED`). Migrations now continue from the highest repo and production version to avoid Flyway out-of-order validation failures; the next migration after this branch is V25 or higher.
- Mileage create requests intentionally omit `userId`; the backend injects the verified claims user into Clockify create commands and audit rows.
- Mileage create requests intentionally omit `taskId`; the UI lists projects and categories but does not call task APIs. Native expense conversion may still preserve an existing Clockify `taskId` from webhook snapshots.
- The create-form Project picker is a searchable combobox (`<input id="field-project" list="project-options">` + `<datalist>`), not a `<select>`. `settings-create.js loadProjects` sorts options alphabetically and builds a name→id map; `resolveProjectId` maps the typed name back to `projectId` for `mileagePayload` (unknown text → no project). Scopes and the create contract are unchanged.
- UI/UX design system (CSP-safe — no inline `<style>`/`<script>`/`style=`/`onclick=`; `MileageSecurityTest` enforces this). Honest system font stack (`--font-sans`, shared with `report.css`/`packet.css`; the dead `Inter` declaration was removed). Dark-mode primary buttons use `--on-accent #07232a` on `--accent #5fb3bd` (≈6.3:1) — do NOT revert to white-on-teal (2.42:1, WCAG 1.4.3 fail). A `--field-border` token + accent focus rings satisfy WCAG 1.4.11. `button:disabled` is `cursor: not-allowed`; only `aria-busy="true"` shows `cursor: progress`. Side nav is an ARIA `role="tablist"`/`role="tab"`/`role="tabpanel"` with roving tabindex + Arrow/Home/End keys (panel + tab markup locked by `MileageSecurityTest`); admin nav now includes Insights. Toasts: errors are `role="alert"` + dismissible and persist; success is `role="status"` and auto-clears (3.5 s). Mine/Team/Conversions tables paginate at `pageSize=50` (`renderPager` from DTO `totalElements`/`page`/`totalPages`, `&page=` appended after the locked `pageSize=50" + query` strings) and collapse to card-per-row under 760px via `data-label` cells. Settings has a live note-template preview, rate policies management, and an S-1 Rate pre-fill (effective `create-context` default + hint when no row rate is saved). Brand mark, report header, and packet header use `/assets/mileage/icon.png`.
- Manual mileage expenses default to billable when `billable` is omitted. An explicit `false` still stays non-billable.
- Main-page rate override is hidden and omitted unless workspace settings allow user overrides. Backend calculation still ignores submitted override rates when the setting is off.
- Rate policies are admin-managed, effective-dated workspace rules. Preview, add-on create, and native conversion resolve by expense date and store `rateSource`, `ratePolicyId`, and `ratePolicyName` on `mileage_conversion`; user override remains available only when settings allow it and is audited as `USER_OVERRIDE`.
- Manual create supports optional audit-only trip evidence: `tripOrigin`, `tripDestination`, `tripPurpose`, `odometerStart`, `odometerEnd`, and `policyExceptionReason`. Text fields are trimmed/capped at 256 chars, odometers are positive decimal values with at most six decimals, and `odometerEnd` must be greater than or equal to `odometerStart`. These values never change Clockify notes or mileage calculation.
- Mileage settings use one `Mileage` unit category with fixed unit `mile` and fixed `HALF_UP` rounding; existing input/output category settings normalize to that single category.
- Setup can adopt an existing Clockify `Mileage` UNIT/mile category and derive the rate from Clockify `unitPrice` cents when no local rate is saved yet. Do not force a new category when the default category is already usable.
- Saving settings (`PUT /api/mileage/settings`) validates rate with the same decimal bounds as create/preview, then best-effort syncs the Clockify Mileage category's unit price to the saved rate via `MileageSettingsController.syncMileageCategoryPrice` → `gateway.createOrRepairMileageCategory(workspaceId, rate)` (only when a rate and `mileageCategoryId` are present). It is caught and logged on any Clockify failure and never fails the save; the response includes `warnings` so the Settings UI can toast the sync failure even though the save succeeded. This is the primary fix for the rate↔category-price divergence (a unit category forces `total = miles × priceInCents`); for native conversions the note annotation documents the residual integer-cent rounding gap (add-on creates leave it unannotated).
- Generated Clockify notes preserve any user-typed note (prepended above the canonical line, separated by a blank line) and are otherwise exact, e.g. `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.` For **native conversions only**, the canonical line additionally explains the actual Clockify category charge whenever it differs from the addon's calculated amount — Clockify computes the unit-priced category total from the integer-cent category price, which can differ from the addon's higher-precision rate — e.g. `Mileage reimbursement: 12.4 miles x 7.25123 = 89.915252 (Clockify category charge: 89.90). Created/converted by Mileage for Clockify.` The `settings.rate()` keeps full precision (NOT rounded) and the addon's own recorded amounts (Mine/Team/Conversions) are unchanged. The charge comes from `ClockifyExpenseSnapshot.total` (cents) via `MileageConversionService.clockifyCategoryCharge`, applied in the native conversion's single worker-thread `updateFlatExpense`.
- Add-on-created expenses get the `(Clockify category charge: X)` annotation via a DEFERRED reconcile, never synchronously. `MileageApiController.createExpense` writes only the create note (a synchronous second `updateFlatExpense` to the just-created expense races Clockify's `EXPENSE_CREATED` webhook and hangs — PR #4/#5 were reverted after live QA 2026-06-05; it still fires `updateFlatExpense` ONLY for the webhook-reserved-first race to re-mark the persisted conversion id). The off-thread `MileageNoteReconcileWorker` (`@Scheduled`, registered in `WebhookJobWorkerConfig`, gated by `mileage.worker.enabled`, poll env `MILEAGE_WORKER_NOTE_RECONCILE_POLL_DELAY_MS` default 60000) sweeps ADDON_FORM + CONVERTED rows with null `note_charge_reconciled_at` and `convertedAt` between 15 min and 30 s ago (settled, past the webhook race), reads the live Clockify `total`, and on divergence inserts the parenthetical via `MileageNoteService.insertCategoryCharge` + one `updateFlatExpense` (which must pass the live snapshot's full `date` — Clockify's update endpoint rejects a date-only string with HTTP 400), then stamps `note_charge_reconciled_at`. Idempotent; transient Clockify failure leaves the row unstamped to retry. Do NOT move this back onto the create request thread. Fix 1A's category-price sync still bounds the divergence to integer-cent rounding; records stay exact. Note idempotency: a note already containing `MileageNoteService.MARKER_PREFIX` or the canonical generated mileage line is returned unchanged (no re-stacking on retry/restore); public signature text alone is not trusted.
- Add-on UI tables and previews display full `calculatedAmount` decimals as the primary amount. Clockify expense writes continue to use the rounded `roundedAmount`. The printable `/iframe/report` is the exception: it renders money at 2 dp.
- Mileage lists and CSV exports filter by `expenseDate`, defaulting to the current US week, Sunday through Saturday.
- User-facing `Mine` and admin `Team` lists/CSVs exclude `DELETED` audit rows. Admin `Conversions` keeps deleted rows visible as audit history.
- Mileage CSV exports emit `user_name` next to `user_id`, `project_name` next to `project_id`, rate policy audit columns, a blank `currency` column for audit rows, and trip evidence columns. Names are resolved live per export through `ClockifyExpenseGateway.listUsers` / `listProjects`; both helpers short-circuit when the row set contains no IDs of that kind and return an empty map on `IOException`/`RuntimeException`, leaving the name cells blank without failing the export.
- Reimbursement packets are printable/CSV audit-row exports for reimbursement review. They support mine/team scope, user/project/status filters, `includeDeleted`, and `exceptionsOnly`; totals include only `CONVERTED` rows so failed/skipped/dry-run rows do not inflate reimbursement amounts.
- Admin Insights aggregates `mileage_conversion` by selected date range: converted miles/amounts, failed conversions, missing trip purpose, policy exceptions, status counts, skip reasons, top projects, and top users. It is an application query only and must not create high-cardinality metrics.
- Mileage CSV export buttons are handled through the delegated `handleCsvExport` click handler in the split settings bundle (`settings-tables.js`, wired by boot `settings.js`). Keep Mine, Team, and Conversions exports on that shared path; a 2026-06-04 live pass caught the Conversions CSV button no-oping when export listeners were attached only per button.
- Expense webhook handlers that need an expense ID accept either `id` or `expenseId` payload shapes. This includes updated/deleted webhooks, which have arrived as full payloads in live Clockify testing.
- Webhook dispatch records/dedupes events, marks processing outcomes internally, and still returns HTTP 2xx after handler or audit-status failures.
- Webhook handling is asynchronous (G1). `/webhook/**` verifies, dedupes, and persists a PENDING row in `addon_webhook_jobs`, then returns 2xx with zero Clockify writes on the request thread. `WebhookJobWorker` (gated by `mileage.worker.enabled`, default `true`) claims rows via `SELECT … FOR UPDATE SKIP LOCKED`, runs `tryStartProcessing` for the loop-prevention guard, and dispatches to the same typed `AddonWebhookHandler` chain as the legacy sync path. A second `@Scheduled` reaper resets CLAIMED rows older than the configured timeout back to PENDING and reopens paired webhook events stuck in `PROCESSING`. The controller falls back to synchronous dispatch when no queue bean is wired (legacy tests with mocked DB).
- `WebhookJobWorkerConfig` is an `@AutoConfiguration(after = AddonDbAutoConfiguration.class)` registered via `addon-expenses-rest-api/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. A plain `@Configuration` skips the worker beans in production even with `@ConditionalOnBean` at @Bean method level — the 2026-05-30 deploy chain demonstrated this. The auto-config ordering is the only reliable fix.
- `AddonWebhookToken` lifecycle cleanup uses idempotent bulk DML for `workspace_id + addon_key`; do not route it through JPA entity delete semantics.
- Worker liveness in production is observable via Spring Boot's auto-bound `tasks_scheduled_execution_seconds_count{code_function="pollAndProcess",outcome="SUCCESS",exception="none"}` and `code_function="reapStuckJobs"` counters on `/actuator/prometheus`. A growing count with `exception="none"` proves the poll loop is hitting the database successfully even when the queue is empty (the per-job `mileage_webhook_job_process_seconds_count` stays at zero until a real webhook lands, so it is NOT a standalone liveness signal).
- Docker compose runs two services from the same image (G1): `addon` web pod (`MILEAGE_WORKER_ENABLED=false`) and `addon-worker` (no port mapping, default worker on). Scale workers horizontally with `docker compose up --scale addon-worker=N`.
- Prometheus metrics live behind `/actuator/prometheus`. Counters: `mileage_conversion_outcome_total{outcome=…}` from `MileageConversionMetrics` register the eight current `MileageConversionStatus` values (`RECEIVED`, `DRY_RUN`, `SKIPPED`, `CONVERTING`, `CONVERTED`, `FAILED`, `DELETED`, `RESTORED_IGNORED`). Gauge: `mileage_webhook_queue_depth{status=PENDING}` from `WebhookJobMetrics`. Timer: `mileage_webhook_job_process` from `WebhookJobWorker`. HTTP enqueue timing is covered by Spring Boot's auto-bound `http_server_requests_seconds`.
- Hikari pool is sized explicitly in `application.yaml` (G2): `maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=30000` ms; each is env-overridable via `SPRING_DATASOURCE_HIKARI_*`.
- OWASP `dependency-check-maven` 12.2.2 is wired in root `pluginManagement` with `failBuildOnCVSS=7.0` (G4). Suppression registry at `dependency-check-suppressions.xml`. CI runs the gate in the `dep-check` job with `NVD_API_KEY` from secrets and an `actions/cache` step for the NVD data dir; local-only runs are impractical without an NVD key and are deferred to CI.
- Native conversion eligibility skips disabled/incomplete settings, workspace mismatches, output categories, mileage note markers, existing successful conversions, non-input categories, missing/invalid quantity, and locked/finalized expenses. Approval/invoiced state is surfaced as workflow warnings instead of blocking conversion. Existing successful conversions stay `CONVERTED`/`CONVERTING` in the audit table when a later loop-guard webhook is skipped; only metrics record the skipped outcome. The Conversions table renders `SKIPPED` rows with a plain-language skip reason label derived from `skipReason`.
- `ClockifyClientFactory` builds per-workspace clients from installed backend/reports URLs. Missing backend URL is fatal; missing reports URL is allowed until a reports request is attempted.
- Clockify token timezone normalization accepts `userTimeZone`, `userTimezone`, `timeZone`, `timezone`, and `tz`; keep frontend timezone alias handling aligned with `ClaimsNormalizer`.
- The settings UI loads `/assets/mileage/settings-date.js`, `settings-core.js`, `settings-ranges.js`, `settings-create.js`, `settings-admin.js`, `settings-tables.js`, then boot `settings.js`. Keep date presets/default create dates in the date helper so Clockify claim timezones stay aligned with backend default ranges.
- After any deploy that touches mileage static assets, report rendering, or packet rendering, probe every `/assets/mileage/settings*.js` asset, `/assets/mileage/report.css`, `/assets/mileage/report.js`, `/assets/mileage/packet.css`, `/assets/mileage/packet.js`, `/iframe/mileage` unauthenticated, `/iframe/report` unauthenticated, and `/iframe/reimbursement-packet` unauthenticated; a single settings asset probe is not enough.
- Receipt uploads in `clockify-rest-client` centralize multipart body construction so Expenses and Files clients share field-name validation, filename sanitization, and content-type fallback behavior.
- Spring multipart limits are pinned in `application.yaml` at 10 MB per file and 12 MB per request, matching the 10 MB cap enforced by `MileageApiController`. `MileageExceptionHandler.handleMaxUploadSize` maps the servlet-level `MaxUploadSizeExceededException` to a 400 `Receipt file exceeds 10 MB` body so the failure mode is identical at both layers.
- The optional `clockify-rest-client` Spring MVC facade and WebClient transport were removed as dead/bloated surfaces. Do not reintroduce global proxy controllers around the typed client.
- Historical pre-Mileage migrations V5/V10 are retained for Flyway validation only; V12 drops their leftover generic tables. Do not add new `temp_addon_expenses*`, `clockify-expenses-api`, `Clockify Expenses API`, or `com.cake.clockify.addon.expenses` references.

## Hosted Verification Snapshot

- Current hosted add-on URL: `https://89-168-93-85.sslip.io`.
- Current hosted manifest URL: `https://89-168-93-85.sslip.io/manifest`.
- Current hosted runtime: OCI VM `mileage-for-clockify-e2`, systemd service `mileage-for-clockify.service`, Java jar at `/opt/mileage-for-clockify/mileage-for-clockify.jar`, Caddy reverse proxy.
- Railway is historical unless explicitly restored; Cloudflared remains the local live-test fallback. Quick-tunnel URLs are ephemeral and must be reinstalled after every restart.
- Dated deployment evidence belongs in the pre-publish checklist after each deploy. Old deployment IDs and old smoke notes are historical evidence only.
- Current hosted probes must include health, manifest, every settings asset, report/packet assets, icon, unauthenticated iframe/report/packet guards, prometheus metric families, scheduler liveness, and a no-PII metric tag audit.
- Clockify multipart-create quirk for ad-hoc live smokes: unit-priced expense categories take miles in the `amount` multipart field, NOT `quantity`. The product gateway already uses `amount`.
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
