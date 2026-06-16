# Claude Code Guide: Mileage for Clockify

This is a standalone Java/Spring Boot Clockify Marketplace add-on repository. The add-on is already implemented; future work should maintain, harden, verify, or extend Mileage for Clockify without replaying the old boilerplate migration.

## Specialized skill + agents for this repo

This repo ships with three sources of agent-facing rules that must stay in sync with the code:

| File | Purpose |
|---|---|
| `.claude/skills/mileage-for-clockify-development/SKILL.md` | Project skill — activates on every task in this repo. Encodes hard rules, commands, the deploy/probe procedure, and known production gotchas (Flyway numbering, `@AutoConfiguration` ordering, idempotent reinstall cleanup, Clockify multipart `amount` vs `quantity`). |
| `.claude/agents/mileage-deployer.md` | Subagent — drives publish gate → OCI deploy by default → status monitor → hosted probes → dated evidence block. Dispatch when the user says "deploy" or "push and verify". |
| `.claude/agents/mileage-webhook-smoke.md` | Subagent — drives the live Clockify E2E webhook smoke. Dispatch when the user says "smoke test the webhook" after a deploy that touched the webhook controller, worker, or conversion service. Never echoes secrets. |

**Meta-rule (non-negotiable).** Any change in this repo that invalidates a Product Fact, Hard Rule, Hosted State entry, env var, command, migration numbering, metric tag, or workflow file MUST update `CLAUDE.md`, `AGENTS.md`, AND the three files above in the SAME PR. If you change behavior that one of these documents describes and you do NOT update the document, the next agent will follow stale guidance and reintroduce a bug we already fixed. Treat the documents as part of the API surface of this repo. Specifically:

- A new Hard Rule, or amendment to an existing one → update `CLAUDE.md` § Hard Rules, `AGENTS.md` § Non-Negotiables, AND `SKILL.md` § "Hard rules — these crash production".
- New module, package responsibility move, or structural refactor → update `CLAUDE.md` § "Main product packages" and `AGENTS.md` § "Module Map".
- New env var, metric family, or Flyway migration → update the Environment / Tables / metric sections in BOTH docs AND the SKILL's "Commands you'll use" if relevant.
- Deploy procedure change (URL, command, probe shape, expected metric) → update `SKILL.md` § "Hosted probe set" AND both agent prompts (`.claude/agents/*.md`).
- Live-smoke prerequisite change (env var renamed, multipart contract changes, addon install URL changes) → update `SKILL.md` § "Live Clockify E2E webhook smoke" AND the `mileage-webhook-smoke` agent's "Prerequisites".

Skip this and you ship broken docs. We know because we've watched future agents re-discover the V7→V17 trap, the `@AutoConfiguration` ordering trap, the lifecycle reinstall cleanup trap, and the `amount` vs `quantity` trap during this session. Each was a production incident or live reinstall warning. Keep the docs current and the next agent skips all four.

## First Steps

```bash
git status --short --branch
mvn -pl addon-expenses-rest-api -am test
```

Read in this order:

1. `AGENTS.md`
2. `README.md`
3. `addon-expenses-rest-api/README.md`
4. `addon-expenses-rest-api/endpoints.md`
5. `addon-expenses-rest-api/webhooks.md`
6. Relevant source and tests before editing

## Current Hardening Checkpoint

Recent product-hardening work touched operational behavior that future agents are likely to trip over. Before changing nearby code, verify these facts against tests and docs:

- Stale CLAIMED webhook jobs are reaped back to PENDING and their paired `addon_webhook_events` rows are reopened from `PROCESSING`; otherwise a worker crash can leave the dedupe guard permanently closed.
- Settings rate validation is centralized in `MileageDecimalPolicy`; settings save can succeed with `warnings` when best-effort Clockify category price sync fails.
- Diagnostics now exposes first-run checklist items and webhook queue health, but no-DB app contexts must still start by returning zeroed operational health.
- Note idempotency trusts the hidden marker or canonical generated mileage line, not public signature text alone.
- `/iframe/report` should degrade only for Clockify/API failures; internal merge/render bugs should remain visible as 500s.
- The Conversions table renders `SKIPPED` rows with plain-language skip reason labels.
- `addon-expenses-rest-api/docs/report-export-scale-spike.md` records why async report/export jobs were deferred.
- Effective-dated rate policies resolve by expense date and audit their rate source/policy identity; user override remains settings-gated.
- Reimbursement packets (`/iframe/reimbursement-packet`, `/api/mileage/reimbursement-packet.csv`) use audit rows as the reimbursement truth and strip `auth_token` through `packet.js`.
- Trip evidence fields are audit-only, capped/validated on create, and never appended to Clockify notes.
- Admin Insights aggregates `mileage_conversion` in-memory and must not introduce identifier-bearing metrics.
- Report currency is best-effort from live Clockify expenses; blank/unknown rows remain visible when a currency filter is active.

## Current Architecture

- `addon-expenses-rest-api` is the product module.
- `addon-core`, `addon-db`, `clockify-rest-client`, and `addon-testkit` are local platform dependencies copied from the add-on factory.
- `repo/` vendors the Clockify add-on SDK Maven artifacts.
- `addon-expenses-rest-api/addon-java-sdk/` is an ignored read-only local SDK clone.

Main product packages:

- `com.cake.clockify.addon.mileage.config`: manual schema 1.5 manifest.
- `com.cake.clockify.addon.mileage.api`: user/admin mileage APIs.
- `com.cake.clockify.addon.mileage.calculation`: `BigDecimal` calculation.
- `com.cake.clockify.addon.mileage.clockify`: Clockify expense gateway.
- `com.cake.clockify.addon.mileage.conversion`: native/mobile webhook conversion.
- `com.cake.clockify.addon.mileage.settings`: workspace settings.
- `com.cake.clockify.addon.mileage.audit`: conversion audit/idempotency.
- `com.cake.clockify.addon.mileage.webhook`: typed expense webhook handlers.
- `com.cake.clockify.addon.mileage.iframe`: server-rendered iframe UI.
- `com.cake.clockify.addon.mileage.worker`: async webhook job worker (G1) — `WebhookJobWorker` polls `addon_webhook_jobs` via `SELECT … FOR UPDATE SKIP LOCKED` and dispatches to the same typed handlers as the sync path. Also hosts `MileageNoteReconcileWorker`, the deferred add-on-create note-charge reconcile sweeper (`@Scheduled`, off the request thread).
- `com.cake.clockify.addon.mileage.metrics`: Micrometer/Prometheus counters and gauges (G3) — conversion outcome counter and queue-depth gauge.
- `com.cake.clockify.addon.mileage.report`: printable all-expenses report — `MileageReportController` (`/iframe/report`) fetches native expenses via `ClockifyExpenseGateway.listExpensesForReport`, merges CONVERTED `mileage_conversion` overrides by `expenseId` in `ReportMerger`, resolves report labels server-side, and renders the CSP-safe `MileageReportRenderer` (`ReportRow` model). Admin no `userId` = all users; non-admin = own; degrades to mileage-only on Clockify failure.
- `com.cake.clockify.addon.mileage.policy`: effective-dated workspace rate policies and rate-source resolution.
- `com.cake.clockify.addon.mileage.workflow`: workflow preflight for locked/finalized blockers and approval/invoiced warnings.
- `com.cake.clockify.addon.mileage.packet`: printable/CSV reimbursement packet built from audit rows.
- `com.cake.clockify.addon.mileage.insights`: admin-only range aggregates for conversion health, exceptions, and top users/projects.

## Product Facts

- Add-on key: `mileage-for-clockify`.
- Manifest schema: `1.5`.
- Minimum plan: `PRO`.
- Scopes: `EXPENSE_READ`, `EXPENSE_WRITE`, `USER_READ`, `PROJECT_READ`, `WORKSPACE_READ`.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- UI routes: `/iframe/mileage`, `/iframe/settings`, `/iframe/report`, `/iframe/reimbursement-packet`.
- User APIs: `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`, `GET /iframe/report` (printable all-expenses report; non-admin gets their own, admin with no `userId` gets all users), `GET /iframe/reimbursement-packet`, `GET /api/mileage/reimbursement-packet.csv`.
- Mileage create requests intentionally omit `userId`; the backend injects the verified claims user into Clockify create commands and audit rows.
- Mileage create requests intentionally omit `taskId`; the UI follows Clockify's regular expense form, does not fetch task options, and does not require `TASK_READ`. Native/mobile conversion may still preserve task IDs from existing Clockify expense snapshots.
- Manual mileage expenses default to billable when `billable` is omitted; explicit `false` remains non-billable.
- Main-page rate override is hidden and omitted unless workspace settings allow overrides. The backend calculation also ignores submitted override rates when the setting is off.
- Rate policies are admin-managed workspace rules under `/api/mileage/rate-policies`. Preview, add-on create, and native conversion resolve by expense date and store `rateSource`, `ratePolicyId`, and `ratePolicyName` on `mileage_conversion`; user overrides are still allowed only when settings permit and are audited as `USER_OVERRIDE`.
- Manual create supports optional audit-only trip evidence: `tripOrigin`, `tripDestination`, `tripPurpose`, `odometerStart`, `odometerEnd`, and `policyExceptionReason`. Text fields are trimmed/capped at 256 chars; odometers are positive decimal values with at most six decimals and `odometerEnd >= odometerStart`. These fields do not affect Clockify notes or mileage calculation, and native conversions leave them blank.
- Mileage settings use one `Mileage` unit category with fixed unit `mile` and fixed `HALF_UP` rounding; existing input/output category settings normalize to that single category.
- Setup can adopt an existing Clockify `Mileage` UNIT/mile category and derive the local rate from Clockify `unitPrice` cents when no local rate is saved yet.
- Saving settings (`PUT /api/mileage/settings`) validates rate with the same decimal bounds as create/preview, then best-effort syncs the Clockify Mileage category's unit price to the saved rate. After persisting, `MileageSettingsController.syncMileageCategoryPrice` reads effective settings and, when a rate and a `mileageCategoryId` are present, calls `gateway.createOrRepairMileageCategory(workspaceId, rate)` so a unit category (which forces `total = miles × priceInCents`) charges the intended amount. It is best-effort and never fails the already-committed save: `InterruptedException` re-interrupts; expected Clockify failures (`IOException`/`ClockifyTransportException`/`ClockifyApiException`) → `log.warn`; any other `RuntimeException` (a real bug, not a Clockify hiccup) → `log.error` with stack, still without failing the save. The response includes `warnings` when this sync fails so the Settings UI can show an error toast even though the save succeeded. This is the primary defense against the rate↔category-price divergence; for native conversions the note annotation documents the residual integer-cent rounding gap (add-on creates leave the residual unannotated — see the add-on-create note fact below).
- Fresh workspaces with no saved settings row default the rate to `0.725` (seeded in `MileageSettingsService.defaults()`). Existing saved rows are unchanged. INTERACTION (intentional): because `getEffectiveSettings` returns `0.725` for a row-less workspace, `MileageSettingsController.createOrRepairMileageCategory` ("Use or Repair Mileage Category") for a brand-new workspace now creates/repairs the Clockify Mileage category at `0.725` instead of adopting an existing Clockify `Mileage` category's `unitPrice`. The Clockify-`unitPrice` adoption path still fires for a *saved row whose rate is null* (legacy rows, or a row saved with only a category). A fresh workspace starting at the standard default rather than guessing from a pre-existing category is the intended product behavior, and the rate is visibly shown in Settings before the admin clicks the button.
- The converted-note template is admin-editable in the Settings UI (`note_template`, textarea id `settings-note-template`, capped at 500 chars; `IllegalArgumentException` → HTTP 400 on overflow). `MileageNoteService` uses the hidden marker or the canonical default `"Mileage reimbursement: ... x ... = ..."` line as idempotency proof. Public signature text alone is not trusted. Custom templates that do not render the canonical default line should include `{{marker}}` or the service appends the marker automatically.
- Generated Clockify notes preserve any user-typed note (prepended above the canonical line, separated by a blank line) and are otherwise exact, e.g. `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.` For **native conversions only**, the canonical line additionally explains the actual Clockify category charge whenever it differs from the addon's calculated amount — Clockify computes the unit-priced category total from the integer-cent category price, which can differ from the addon's higher-precision rate — e.g. `Mileage reimbursement: 12.4 miles x 7.25123 = 89.915252 (Clockify category charge: 89.90). Created/converted by Mileage for Clockify.` The `settings.rate()` keeps full precision (NOT rounded) and the addon's own recorded amounts (Mine/Team/Conversions) are unchanged — only the Clockify note reconciles the two. The charge comes from `ClockifyExpenseSnapshot.total` (cents) via `MileageConversionService.clockifyCategoryCharge`, applied in the native conversion's single worker-thread `updateFlatExpense` (post-webhook).
- Add-on-created expenses get the `(Clockify category charge: X)` annotation via a DEFERRED reconcile, not synchronously. `MileageApiController.createExpense` writes only the create note (no charge reconcile on the request thread — a synchronous second write races Clockify's `EXPENSE_CREATED` webhook and hangs; proven 2026-06-05, after PR #4/#5 reading the create-response/`getExpense` totals were reverted because the second write hung/never persisted and the create stalled ~20s on `saveAndFlush`). `createExpense` still fires `updateFlatExpense` ONLY for the webhook-reserved-first race (to re-mark the persisted conversion id), never to stamp the charge. Instead `MileageNoteReconcileWorker` (worker package, `@Scheduled`, registered in `WebhookJobWorkerConfig`, gated by `mileage.worker.enabled`) polls every `mileage.worker.note-reconcile-poll-delay-ms` (env `MILEAGE_WORKER_NOTE_RECONCILE_POLL_DELAY_MS`, default 60000) for ADDON_FORM + CONVERTED rows whose `note_charge_reconciled_at` is null and whose `convertedAt` is between 15 min and 30 s ago (settled, past the webhook race), reads the live Clockify `total`, and if the charge diverges from the recorded amount inserts the parenthetical via `MileageNoteService.insertCategoryCharge` and issues ONE `updateFlatExpense`, then stamps `note_charge_reconciled_at`. That `updateFlatExpense` MUST pass the live snapshot's full `date` (`yyyy-MM-ddThh:mm:ssZ`), exactly like the native-conversion update — Clockify's expense-update endpoint rejects a date-only string with HTTP 400, so never pass `conversion.getExpenseDate()` there (live QA 2026-06-06 caught the sweeper failing every cycle with `ClockifyValidationException` 400 until it used `snapshot.date()`). Idempotent (skips notes already carrying the parenthetical; a transient Clockify failure leaves the row unstamped to retry next cycle until it ages past 15 min). Do NOT move this back onto the create request thread. Fix 1A (settings-save category-price sync) still bounds the divergence to integer-cent rounding (e.g. recorded 8.99 vs charged 9.05 on a 12.4-mi expense), and the add-on's own records stay exact. Note idempotency: a note already containing `MileageNoteService.MARKER_PREFIX` or the canonical generated mileage line is returned unchanged by `buildConvertedNote` (no re-stacking on retry/restore); public signature text alone is not trusted.
- Mileage CSV export buttons are handled through the delegated `handleCsvExport` click handler in the split settings bundle (`settings-tables.js`, wired by the tiny boot `settings.js`). Keep Mine, Team, and Conversions exports on that shared path; a 2026-06-04 live pass caught the Conversions CSV button no-oping when export listeners were attached only per button.
- Expense webhook handlers that need an expense ID accept either full payloads with `id` or reference payloads with `expenseId`; update/delete webhooks have used both shapes in live testing.
- Webhook dispatch records/dedupes events, marks processing outcomes internally, and still returns HTTP 2xx after handler or audit-status failures.
- Webhook handling is async (G1). The `/webhook/**` controller verifies → dedupes → persists a PENDING row in `addon_webhook_jobs` → returns 2xx. NO Clockify writes happen on the request thread. A `WebhookJobWorker` (gated by `mileage.worker.enabled`, default `true`) claims jobs via `SELECT … FOR UPDATE SKIP LOCKED`, runs `tryStartProcessing` for the loop-prevention guard, then invokes the matching `AddonWebhookHandler`. Two-worker SKIP LOCKED correctness is regression-tested with Testcontainers Postgres in `WebhookJobQueueSkipLockedTest`.
- `WebhookJobWorkerConfig` is an `@AutoConfiguration(after = AddonDbAutoConfiguration.class)` registered via `addon-expenses-rest-api/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. This is NOT a stylistic choice — see the worker liveness rule below. A plain `@Configuration` (even with `@ConditionalOnBean` moved to each `@Bean` method) silently skips the worker beans in production because Spring evaluates the condition before `JpaRepositoriesAutoConfiguration` registers the claim-service bean. Verified the hard way by the 2026-05-30 deploy chain `33e2c56c → fdf6a328 → 9d89508d`: only the third deploy, after the auto-config ordering fix, exposed `mileage_webhook_queue_depth` and `tasks_scheduled_execution_seconds_count{code_function="pollAndProcess"}` on `/actuator/prometheus`.
- `AddonWebhookToken` cleanup during lifecycle `DELETED` uses a scoped bulk DML delete by `workspace_id + addon_key`. Do not replace it with Spring Data entity deletion; Clockify reinstall/delete races can stale-delete an already-changed token row and log `ObjectOptimisticLockingFailureException` / `StaleObjectStateException` even though Clockify receives 200.
- Worker liveness signal in production: Spring Boot auto-binds `tasks_scheduled_execution_seconds_count{code_function="pollAndProcess",outcome="SUCCESS"}` and `code_function="reapStuckJobs"` to `/actuator/prometheus`. Non-zero count with `exception="none"` proves the poll loop is alive and the database query is succeeding end-to-end (the per-job `mileage_webhook_job_process_seconds_count` stays at zero until a real webhook lands, so it is NOT a sufficient liveness check on its own).
- The worker has a second `@Scheduled` reaper that flips CLAIMED rows older than `mileage.worker.stuck-job-timeout-seconds` (default 300) back to PENDING and reopens paired webhook events stuck in `PROCESSING`, so a worker crash mid-process self-heals instead of leaving the event dedupe guard permanently closed.
- When the controller has no `WebhookJobQueue` bean wired (e.g. legacy MockMvc test paths), it falls back to synchronous dispatch under the same 2xx-after-failure contract — preserves the original `addon-core` behavior for tests that mock the DB.
- Admin retry (`MileageConversionService.retry` → `/api/mileage/conversions/{id}/retry`) is intentionally synchronous; it is an authenticated admin click, not a webhook delivery, so the user expects a direct ConversionResult response.
- Native conversion eligibility skips disabled/incomplete settings, workspace mismatches, output categories, mileage note markers, existing successful conversions, non-input categories, missing/invalid quantity, and locked/finalized expenses. Approval/invoiced state is surfaced as workflow warnings instead of blocking conversion. Existing successful conversions stay `CONVERTED`/`CONVERTING` in the audit table when a later loop-guard webhook is skipped; only metrics record the skipped outcome. This protects add-on-created rows from Clockify create/update webhook races. The Conversions table renders `SKIPPED` rows with a plain-language skip reason label derived from `skipReason`.
- Admin APIs: settings, Mileage category repair, rate policies (`/api/mileage/rate-policies`), diagnostics, category options, user options (`GET /api/mileage/options/users`), team mileage list/export, conversion list/detail/retry/export, reimbursement packet export, and insights (`GET /api/mileage/insights`) under `/api/mileage`. Diagnostics returns readiness booleans, warning messages, first-run checklist items, and webhook operational health (`pendingJobs`, `claimedJobs`, `failedJobs`, `oldestPendingAgeSeconds`, `lastCompletedJobAt`); stale pending jobs and failed jobs become warnings.
- Team and Conversions admin views and their CSV exports accept an optional `userId` filter; `GET /api/mileage/options/users` (admin) backs the dropdown. The `userId` is used for admin *read* filtering only and is never trusted for create. The locked CSV-export substrings in the split settings bundle (`MileageSecurityTest`) stay byte-identical because the filter is threaded through `csvPath`/`userFilterQuery`, not the export map literals.
- The three option endpoints degrade gracefully on a Clockify transport failure, returning HTTP 200 with an empty list and a non-blank `warning` instead of 500. A cold-start timeout is an `HttpTimeoutException` that `DefaultClockifyTransport` wraps as a `ClockifyTransportException` (a `RuntimeException`, NOT an `IOException`); `ClockifyApiException` (also a `RuntimeException`) carries non-2xx. So `GET /api/mileage/options/categories` catches `ClockifyApiException` (401/403); `GET /api/mileage/options/projects` and `GET /api/mileage/options/users` catch `InterruptedException`/`IOException`/`ClockifyTransportException`/`ClockifyApiException` and let any OTHER `RuntimeException` propagate as 500 (so a real logic bug is not mislabeled a transient outage). The `…OptionsResponse.unavailable(warning)` factories back this; the frontend `loadCategories`/`loadProjects`/`loadUserOptions` surface `data.warning` via `toast(…, "error")` and render the empty dropdown.
- `GET /api/mileage/mine` leaves `userName` blank for the requester's own rows rather than echoing the raw `userId`. `MileageConversionListResponse.from(page)` (the no-map Mine path) builds rows with `MileageConversionDetailResponse.from(conversion, null, false)` (`userIdFallback=false`). Admin Team/Conversions keep the id fallback (`userIdFallback=true`) so an unresolved user still shows the id.
- Both server-rendered pages declare a same-origin favicon (`<link rel="icon" type="image/png" href="/assets/mileage/icon.png">`) in their `<head>` — `MileageIframeController.html` and `MileageReportRenderer.render` — so the browser's automatic `/favicon.ico` request resolves instead of 404-ing. CSP `img-src 'self'` already permits it; no CSP change.
- `GET /iframe/report` is a server-rendered printable **expense report** (no PDF library; browser print-to-PDF) listing ALL Clockify expenses in the range. Converted mileage rows show the add-on's reconciled values via `ReportMerger`; non-mileage rows show native Clockify category + amount. Native expenses come from `ClockifyExpenseGateway.listExpensesForReport` -> backend `ExpensesClient.getExpenses` (paged + client-side date filter; `total` cents -> major units; currency code best-effort from live expense fields). Scope is driven by `scope=mine|team`: `scope=mine` (or any non-admin) = the requester's own; admin team/no scope + no `userId` = all users; admin + `userId` = that user. Single-user labels are resolved server-side from Clockify users, so report links must not carry client-supplied display names. Amount and Total render money at fixed **2 dp** (`HALF_UP`) and sum visible rounded rows; Miles/Rate keep natural precision. A Currency column appears when live rows carry currency or a currency filter is active; rows with blank/unknown currency remain visible for add-on audit rows. If the live expense list cannot be fetched, the report degrades to reconciled-mileage-only rows with a banner — never 500. `/iframe/**` authenticates via `auth_token`; `report.js` strips it after load. `from`/`to` are required; output is capped at 1000 rows. Do NOT switch the data source to the Reports API (`expenseDetailed`) without a fallback: it needs `reportsUrl`, which an install may lack, whereas `backendUrl` is always present.
- Add-on previews and mileage tables show full `calculatedAmount` decimals first; Clockify Expenses still receives the rounded `roundedAmount`. (The printable report is the exception — see the `/iframe/report` fact: money at 2 dp.)
- Mileage lists and CSV exports filter by actual `expenseDate`, defaulting to the current US week, Sunday through Saturday. The Mine/Team/Conversions tables paginate client-side at `pageSize=50` with a `Showing X–Y of N` count and Prev/Next built by `settings-tables.js renderPager` from the list DTO's `totalElements`/`page`/`totalPages` (the page number rides on `&page=` appended after the locked `pageSize=50" + query` strings); changing the range or user filter resets to page 0.
- UI/UX design system (server-rendered iframe + report + packet; all CSS/JS external, CSP-safe — no inline `<style>`/`<script>`/`style=`/`onclick=`). Honest system font stack (`--font-sans`; the never-loaded `Inter` declaration was dropped and `report.css`/`packet.css` share the same stack). Dark-mode primary buttons use `--on-accent #07232a` ink on `--accent #5fb3bd` (≈6.3:1) — do NOT revert to white-on-teal (2.42:1, WCAG 1.4.3 fail). Focus rings + a dedicated `--field-border` token (≈3:1) satisfy WCAG 1.4.11; `button:disabled` is `cursor: not-allowed` and only a genuinely in-flight `aria-busy="true"` control shows `cursor: progress`. The side nav is an ARIA `role="tablist"` with `role="tab"` buttons (`id="tab-btn-<tab>"`, `aria-controls`, roving `tabindex`, Arrow/Home/End keys) and `role="tabpanel"` sections (`MileageSecurityTest` locks the panel/tab markup); admin nav includes Insights. Toasts: errors are `role="alert"` with a dismiss button and DO NOT auto-vanish; success is `role="status"` and auto-clears after 3.5 s. Tables become a card-per-row layout under 760px via per-cell `data-label` set in `settings-core.js labelRow`. The brand mark, report header, and packet header use the real `/assets/mileage/icon.png` (CSP `img-src 'self'`). Settings shows a live note-template preview, rate policies management, and pre-fills the Rate from the effective `create-context` default with a hint when no row rate is saved (S-1). Section nav icons are inline decorative SVGs (`aria-hidden`, `stroke="currentColor"`).
- The create-form Project picker is a searchable combobox (`<input id="field-project" list="project-options">` + `<datalist>`), not a `<select>`. `settings-create.js loadProjects` sorts options alphabetically and builds a name→id map; `resolveProjectId` maps the typed name back to the project id for `mileagePayload` (unknown text → no project). The backend still receives `projectId`; this did not change scopes or the create contract.
- User-facing `Mine` and admin `Team` lists/CSVs exclude rows marked `DELETED`; admin `Conversions` keeps deleted rows as audit history.
- All three mileage CSV exports emit `user_name` next to `user_id`, `project_name` next to `project_id`, rate source/policy columns, a blank `currency` column for audit rows, and audit-only trip evidence columns. Current header: `expense_id,source,source_label,status,user_id,user_name,project_id,project_name,miles,rate,rate_source,rate_policy_id,rate_policy_name,calculated_amount,expense_amount,currency,rounding_mode,expense_date,updated_at,converted_at,trip_origin,trip_destination,trip_purpose,odometer_start,odometer_end,policy_exception_reason,note_marker`. `user_name` is resolved live via `gateway.listUsers` for admin team/conversions exports and left empty for `mine.csv`; `project_name` is resolved live via `gateway.listProjects` for all three endpoints. When name lookup fails (network/RuntimeException) the helper returns an empty map and rows ship with empty name cells; the IDs remain authoritative.
- Reimbursement packets are printable/CSV audit-row exports for reimbursement review. They support mine/team scope, user/project/status filters, `includeDeleted`, and `exceptionsOnly`; totals include only `CONVERTED` rows so failed/skipped/dry-run rows do not inflate reimbursement amounts. `packet.js` strips `auth_token` after load like `report.js`.
- Admin Insights aggregates `mileage_conversion` by selected date range: converted miles/amounts, failed conversions, missing trip purpose, policy exceptions, status counts, skip reasons, top projects, and top users. It is an application query only and must not create high-cardinality metrics.
- Tables: `mileage_workspace_settings`, `mileage_conversion`, `mileage_rate_policy`. `V19__trim_mileage_conversion_audit_surface.sql` removes obsolete `currency`, `raw_event_hash`, and `clockify_request_id` audit columns and removes the unused `FETCHED` conversion status. Platform tables (in `addon-db`): `addon_installations`, `addon_webhook_tokens`, `addon_workspace_settings`, `addon_webhook_events`, and `addon_webhook_jobs` (G1 async queue — Flyway `V17__addon_webhook_jobs.sql`, indexes on `(status, created_at)`, `(addon_key, workspace_id)`, `event_id`, partial on `claimed_at WHERE status='CLAIMED'`; lifecycle `PENDING → CLAIMED → COMPLETED|FAILED`). `V18__rename_webhook_job_completed_status.sql` migrates old completed queue rows from `CONVERTED` to `COMPLETED`. `V20__add_mileage_note_charge_reconciled_at.sql` adds the nullable `note_charge_reconciled_at` column used by the deferred note-charge reconcile sweeper. `V21__index_mileage_note_reconcile_pending.sql` adds the pending-reconcile partial index. `V22__create_mileage_rate_policies.sql` adds effective-dated policies; `V23__add_mileage_rate_policy_audit_columns.sql` stores `rate_source` and policy identity on audit rows; `V24__add_mileage_trip_evidence.sql` stores optional trip evidence. Until a dated deploy proves it, hosted evidence still shows production applied through V20. Flyway validates in strict order by default, so the next new repo migration after V24 must be V25 or higher.
- Prometheus metrics are scraped from `/actuator/prometheus` (management exposure: `health,info,prometheus`). Counters: `mileage_conversion_outcome_total{outcome=...}` from `MileageConversionMetrics` register the eight current `MileageConversionStatus` values (`RECEIVED`, `DRY_RUN`, `SKIPPED`, `CONVERTING`, `CONVERTED`, `FAILED`, `DELETED`, `RESTORED_IGNORED`). Gauge: `mileage_webhook_queue_depth{status=PENDING}` from `WebhookJobMetrics`. Timer: `mileage_webhook_job_process` for worker dispatch latency. HTTP enqueue latency is covered automatically by Spring Boot's `http_server_requests_seconds` timer.
- METRIC TAGGING RULE: counters and gauges may be tagged ONLY by stable enums (`outcome`, `status`). Never tag with `userId`, `workspaceId`, `expenseId`, token values, or any identifier — Prometheus cardinality explodes and tagged IDs leak into scrape endpoints. `MileageConversionMetricsTest` enforces this.
- Hikari pool is sized explicitly in `application.yaml` (G2): `maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=30000`. All three are env-overridable via `SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE`, `SPRING_DATASOURCE_HIKARI_MIN_IDLE`, `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT`.
- Docker compose runs two services from the same image (G1): `addon` (web pod, `MILEAGE_WORKER_ENABLED=false`) and `addon-worker` (no port mapping, `MILEAGE_WORKER_ENABLED=true`). Scale workers horizontally with `docker compose up --scale addon-worker=N`.
- OWASP `dependency-check-maven` 12.2.2 (G4) is wired in root `pluginManagement` with `failBuildOnCVSS=7.0`, JSON+HTML reports, NVD key from `${env.NVD_API_KEY}`. Suppression registry lives at `dependency-check-suppressions.xml`. CI runs the gate in the `dep-check` job (`.github/workflows/ci.yml`) with NVD data cache; the local-only run is impractical without an NVD API key and is deferred to CI.
- `ClockifyClientFactory` builds per-workspace clients from installed backend/reports URLs. Missing backend URL is fatal; missing reports URL is allowed until a reports request is attempted.
- Clockify token timezone normalization accepts `userTimeZone`, `userTimezone`, `timeZone`, `timezone`, and `tz`; keep this aligned with the settings UI timezone alias handling.
- The settings UI loads the deferred bundle in this order: `/assets/mileage/settings-date.js`, `settings-core.js`, `settings-ranges.js`, `settings-create.js`, `settings-admin.js`, `settings-tables.js`, then the tiny boot `settings.js`. The date helper owns date presets/default dates so frontend ranges use the Clockify claim timezone, with browser-local fallback for invalid timezone claims.
- After static asset deploys, probe every settings JS asset above plus `/assets/mileage/report.css`, `/assets/mileage/report.js`, `/assets/mileage/packet.css`, `/assets/mileage/packet.js`, `/iframe/mileage` unauthenticated, `/iframe/report` unauthenticated, and `/iframe/reimbursement-packet` unauthenticated. Do not treat the old single `settings.js` probe as full proof for the current UI.
- Receipt uploads in `clockify-rest-client` use the shared multipart helper. Expenses and Files clients must not hand-roll multipart headers because field names, filenames, and content types need the same defensive handling.
- Spring multipart limits are pinned to 10 MB file / 12 MB request in `addon-expenses-rest-api/src/main/resources/application.yaml`. `MileageApiController.MAX_RECEIPT_BYTES` (10 MB) remains the friendly-error layer that emits `Receipt file exceeds 10 MB`; `MileageExceptionHandler.handleMaxUploadSize` maps the servlet-level `MaxUploadSizeExceededException` to the same 400 body so requests above the cap never surface as 500.
- Historical pre-Mileage migrations V5/V10 are retained for Flyway validation only; V12 drops their leftover generic tables. New code/docs should not add `temp_addon_expenses*`, `clockify-expenses-api`, `Clockify Expenses API`, or `com.cake.clockify.addon.expenses` references.

## Hosted State

- Current hosted add-on URL: `https://89-168-93-85.sslip.io`.
- Current hosted manifest URL: `https://89-168-93-85.sslip.io/manifest`.
- Current hosted runtime: OCI VM `mileage-for-clockify-e2`, systemd service `mileage-for-clockify.service`, Java jar at `/opt/mileage-for-clockify/mileage-for-clockify.jar`, Caddy reverse proxy.
- Railway is historical unless explicitly restored; Cloudflared remains the local live-test fallback. Quick-tunnel URLs are ephemeral and must be reinstalled after every restart.
- Dated deployment evidence belongs in `addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md`; old deployment IDs and old smoke notes are historical evidence only.
- Current hosted probes must include health, manifest, every settings asset, report/packet assets, icon, unauthenticated iframe/report/packet guards, prometheus metric families, scheduler liveness, and a no-PII metric tag audit.
- Clockify multipart-create quirk for ad-hoc live smokes: unit-priced expense categories take miles in the `amount` multipart field, NOT `quantity`. The product gateway already uses `amount`.

## Commands

```bash
# Repo-local publish safety bundle
./scripts/verify-publish.sh

# Fast focused add-on reactor
mvn -pl addon-expenses-rest-api -am test

# Clean verification
mvn -pl addon-expenses-rest-api -am clean test

# OWASP dependency vulnerability scan (G4) — requires NVD_API_KEY for fast scan;
# without one it stalls on public NVD throttling. CI runs this in the dep-check job.
NVD_API_KEY=… mvn -pl addon-expenses-rest-api -am -DskipTests dependency-check:check

# Docker image (G1 — builds BOTH addon web pod and addon-worker pod)
docker compose -f addon-expenses-rest-api/docker-compose.yml build

# Runtime manifest probe, with DB port kept internal if local 5432 is busy
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') up -d
curl -fsS http://localhost:8080/manifest
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') down
```

If Testcontainers/Docker discovery hangs locally, use the Docker Desktop socket on this Mac:

```bash
DOCKER_HOST=unix:///Users/15x/.docker/run/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test
```

## Environment

Runtime configuration uses these names:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE` (G2 override, default `20`)
- `SPRING_DATASOURCE_HIKARI_MIN_IDLE` (G2 override, default `5`)
- `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` (G2 override, default `30000` ms)
- `ADDON_BASE_URL`
- `ADDON_KEY`
- `ADDON_NAME`
- `ADDON_DESCRIPTION`
- `ADDON_CRYPTO_ACTIVE_KEY_ID`
- `ADDON_CRYPTO_KEY_K1`
- `ADDON_ENABLE_HSTS`
- `MILEAGE_WORKER_ENABLED` (G1; default `true`. Set to `false` on the web pod when running the split web/worker topology)
- `MILEAGE_WORKER_POLL_DELAY_MS` (G1; default `250`)
- `MILEAGE_WORKER_STUCK_JOB_TIMEOUT_SECONDS` (G1; default `300`)
- `MILEAGE_WORKER_BATCH_SIZE` (G1; default `8`)
- `MILEAGE_WORKER_NOTE_RECONCILE_POLL_DELAY_MS` (deferred add-on-create note-charge reconcile poll interval; default `60000`)
- `PORT`

Build-time / CI:

- `NVD_API_KEY` (G4; used by the CI `dep-check` job. Without it, the local OWASP scan stalls on slow NVD throttling — run dep-check in CI).

Live sacrificial Clockify checks may use shell environment variables such as `CLOCKIFY_API_BASE_URL`, `CLOCKIFY_API_KEY`, `CLOCKIFY_WORKSPACE_ID`, `CLOCKIFY_TEST_USER_ID`, and `CLOCKIFY_TEST_PROJECT_ID`. Never print secret values.
Pass live secrets through environment variables, stdin, or a local secret store; never write real keys into repo files, command transcripts intended for docs, or final reports.
Live Clockify smoke is optional and requires local secrets. Never commit or echo API keys/tokens. If not run, final output must say it was skipped.

Default CORS allows Clockify origins and the origin from `ADDON_BASE_URL`, which keeps local Cloudflared or other tunnel iframe/API testing working without adding a broad wildcard.

## Architecture Decision: Postgres (not MongoDB)

Asked 2026-05-30 whether to migrate the persistence layer to MongoDB. Decision: stay on Postgres. Three load-bearing reasons:

1. **`SELECT … FOR UPDATE SKIP LOCKED` has no clean MongoDB equivalent.** The G1 async webhook worker (`AddonWebhookJobRepository.findPendingForUpdateSkipLocked`) depends on it as a single atomic primitive that claims a PENDING row, skips any row another worker has locked, and releases the lock on transaction commit. The two-workers-don't-double-process invariant proven in `WebhookJobQueueSkipLockedTest` is enforced by Postgres row-level lock semantics. MongoDB's `findOneAndUpdate` can simulate a claim but can't give us "the row lock survives until commit so claim+process can split across transactions while we hold the Clockify HTTPS call outside any DB lock".
2. **Financial precision + multi-step state-machine transactions.** `BigDecimal ↔ numeric` is the natural Java↔SQL mapping for the hard rule "no float/double for mileage, rate, money". `MileageConversionService.convertIfEligible` runs the state transitions (`RECEIVED → CONVERTING → CONVERTED|FAILED`) inside one `@Transactional` boundary with clean rollback on `ClockifyApiException`. MongoDB multi-document transactions exist since 4.0 but with stricter constraints (60 s default timeout, write conflicts force aborts) and the Hibernate/JPA Decimal128 story is weaker.
3. **Flyway numbering + `{h-schema}` per-test schema isolation + existing JPA layer.** The queue migrations use `{h-schema}` placeholders so the SKIP LOCKED test runs against a throwaway schema (`mileage_skiplocked`) without polluting `addon_db_test`. MongoDB has no Flyway equivalent with the same boot-time validation that caught the V7→V17 production crash. Switching means rewriting every repository, every native query, every entity, every test fixture for zero functional gain.

Performance reality: worker poll latency is 3.9 ms avg / 12.8 ms max against Railway Postgres; per-job process latency 334 ms avg is the Clockify HTTPS roundtrip, not the DB. We're nowhere near PG limits.

Revisit only if we hit something Postgres genuinely can't do — and we won't, for an add-on of this shape.

## Local environment file

Clockify credentials and workspace IDs are persisted at `~/.config/clockify-mileage.env` (mode `600`) and sourced from `~/.zshrc` so new shell sessions get them automatically. Contents (current session set up 2026-05-30):

- `CLOCKIFY_API_BASE_URL=https://developer.clockify.me/api/v1` — developer environment hosting the sacrificial workspace
- `CLOCKIFY_WORKSPACE_ID=672f9cf4ad6f45299c3e3de2` — sacrificial workspace where the addon is installed
- `CLOCKIFY_TEST_USER_ID=672f9cf4ad6f45299c3e3de1` — the API key's user
- `CLOCKIFY_TEST_PROJECT_ID=68dd9b5ee598361591be848e` — sacrificial project ("01")
- `CLOCKIFY_API_KEY` — set. The Clockify dev workspace auto-resets so a leaked key self-invalidates; the value is sandbox-grade. If a future session needs to refresh: ask the user for a new dev workspace key, swap into the file. Never paste a production-tier Clockify key into this file.
- `NVD_API_KEY` — placeholder, fill in to activate the CI dep-check HIGH/CRITICAL gate. Free from `nvd.nist.gov/developers/request-an-api-key`.

The file is in `~/.config/`, not in this repo. Never commit it. Don't echo any of these values back to the user; probe presence with `[ -n "$VAR" ] && echo set || echo MISSING`. Smoke-test the persisted credentials at the start of any task that needs Clockify: `curl -sS -o /dev/null -w "%{http_code}\n" -H "X-Api-Key: $CLOCKIFY_API_KEY" "$CLOCKIFY_API_BASE_URL/user"` — 200 means good, 401 means the dev workspace reset and the key needs refreshing.

If the dev workspace was reset, the Mileage addon also needs to be re-installed before any live webhook smoke can run — point Clockify dashboard → Apps at `https://89-168-93-85.sslip.io/manifest` unless the live target is an explicitly requested Cloudflared or restored Railway URL.

## Hard Rules

- Do not edit `addon-expenses-rest-api/addon-java-sdk/`.
- Do not use `double`, `Double`, `float`, or `Float` for mileage, rate, or money domain values.
- Do not hardcode Clockify API URLs in add-on code.
- Do not expose installation tokens to frontend JavaScript or HTML.
- Do not log tokens, auth headers, receipt bytes, or raw upstream error bodies.
- Do not hand-build multipart upload headers in individual Clockify clients; use the shared multipart helper and keep unsafe field names rejected.
- Preserve workspace isolation in repository methods and service calls.
- Webhook handlers must acknowledge safely with HTTP 2xx after internal failure recording/logging. Do not let Clockify blindly retry failures that should be retried from the admin/internal path.
- Webhook controller must NOT call `AddonWebhookHandler.handle` or any Clockify gateway method on the request thread when a `WebhookJobQueue` bean is wired. The G1 contract is verify → dedupe → enqueue PENDING → 2xx. Adding sync work back on the request thread reintroduces the timeout-and-retry storm that motivated the queue.
- Worker `claimNext` transaction must wrap the SELECT FOR UPDATE SKIP LOCKED and the status flip to `CLAIMED` in one transaction. Do NOT extend the transaction across the handler dispatch — the Clockify HTTP call must happen outside any DB lock.
- Prometheus counters and gauges may be tagged ONLY by stable, low-cardinality enums (`outcome`, `status`). Do not tag with `userId`, `workspaceId`, `expenseId`, token values, or any identifier; Prometheus cardinality explodes and tagged identifiers leak into scrape endpoints.
- Do not lower `failBuildOnCVSS` below `7.0` in the OWASP dep-check plugin config. Add a documented entry in `dependency-check-suppressions.xml` for verified false positives — never blanket-skip findings to get CI green.
- `WebhookJobWorkerConfig` MUST remain `@AutoConfiguration(after = AddonDbAutoConfiguration.class)` with the FQN listed in `addon-expenses-rest-api/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Reverting to plain `@Configuration` (even with `@ConditionalOnBean` at @Bean method level) breaks production silently — local tests pass, prometheus exposes conversion counters, but the queue-depth gauge and worker timer never register because the condition evaluates before the claim-service bean is registered. The 2026-05-30 deploys `33e2c56c` and `fdf6a328` both reproduced this. The auto-config ordering is the only reliable fix.
- Lifecycle `DELETED` cleanup for `AddonWebhookToken` MUST remain a scoped bulk DML delete by workspace and add-on key. Do not use entity deletes or derived repository delete methods for this cleanup; reinstall races can otherwise throw optimistic-lock/stale-object warnings for token rows that another lifecycle transaction already changed.
- New Flyway migrations must be numbered AFTER the highest migration already present in the repo and after the highest applied production migration. This repo now contains pending product migrations through `V24__add_mileage_trip_evidence.sql`; hosted evidence still proves production only through V20 until a deploy applies V21-V24. Do not insert migrations into gaps below either maximum — Flyway validates strict ordering and will crash boot with `Detected resolved migration not applied to database: N`. The 2026-05-30 deploy `d11e2088` crashed on this exact failure when V7 was used; the next new migration after this branch must be V25 or higher.
- Native expense conversion must aggressively prevent loops: skip mileage audit markers, output-category expenses, and already-converted expenses before writing back to Clockify. Do not let skipped loop-guard webhooks rewrite an existing successful audit row away from `CONVERTED`.
- Do not trust request-supplied `userId` for user-facing mileage creation; derive the target user from verified Clockify token claims. Do not add `userId` back to the create request DTO, multipart allowlist, iframe form, or frontend payload.
- Do not add a task selector, task options endpoint, `taskId` create field, or `TASK_READ` scope for user-facing mileage creation unless product requirements change and live scope evidence is captured first.
- Do not expose the rate override input on the main page unless `/api/mileage/create-context` reports `allowUserRateOverride=true`.
- Keep `addon-core` and `addon-db` changes narrow; ask before structural platform changes.
- Keep copied Marketplace docs under `addon-expenses-rest-api/MARKETPLACE_OCS/` as source reference material.
- Do not restore default Clockify API hosts in `clockify-rest-client`; builders and tests must pass explicit backend URLs, add-ons must route from verified token claims or installation context, and reports URLs may only be omitted for clients that do not use reports APIs.
- Do not restore the deleted `clockify-rest-client` Spring MVC facade or WebClient transport. Keep the typed client thin.
- Do not restore deleted live shell probes. Do not add new legacy temp-addon migrations; keep V5/V10 only as immutable Flyway history and use forward migrations for cleanup.

## Verification Expectations

Before claiming pre-publish readiness, run `./scripts/verify-publish.sh`, complete `addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md`, and paste the exact command outputs into the session summary.

`./scripts/verify-publish.sh` auto-detects Docker Desktop or Colima, checks every mileage settings JS asset, runs `scripts/test-mileage-date-helpers.mjs`, runs `scripts/test-mileage-settings-behavior.mjs`, and executes the same static no-float/no-host guardrails as CI. If a deploy follows, include hosted probes for `/assets/mileage/settings-date.js`, `/assets/mileage/settings-core.js`, `/assets/mileage/settings-ranges.js`, `/assets/mileage/settings-create.js`, `/assets/mileage/settings-admin.js`, `/assets/mileage/settings-tables.js`, `/assets/mileage/settings.js`, `/assets/mileage/report.css`, `/assets/mileage/report.js`, `/assets/mileage/packet.css`, `/assets/mileage/packet.js`, `/assets/mileage/icon.png`, unauthenticated `/iframe/mileage`, unauthenticated `/iframe/report`, unauthenticated `/iframe/reimbursement-packet`, prometheus metric families, and scheduler liveness.

## Maintenance Notes

- Keep future work in maintenance mode: small diffs, focused regression tests, full add-on reactor verification for behavior changes, and hosted manifest/health probes after deployment changes.
- After receipt/upload changes, run the Expenses and Files client tests together so shared multipart behavior is covered on both call paths.

For documentation-only changes:

```bash
rg -n "clockify-expenses-api|Clockify Expenses API|com\\.cake\\.clockify\\.addon\\.expenses|temp_addon_expenses|temp-addon-expenses|API_TEST_|CLOCKIFY_API_KEY:-|test-suite\\.sh" \
  addon-expenses-rest-api/src/main addon-expenses-rest-api/src/test/resources addon-expenses-rest-api/pom.xml \
  -g '!**/target/**' -g '!addon-expenses-rest-api/src/main/resources/db/migration/V5__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V10__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V12__*' \
  -g '!addon-expenses-rest-api/MARKETPLACE_OCS/**'
mvn -pl addon-expenses-rest-api -am test
```

For behavior, manifest, Docker, or security changes, also run the Docker build and manifest probe above.
