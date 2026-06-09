---
name: mileage-for-clockify-development
description: Project-specialized knowledge for the Mileage for Clockify add-on. Activates ANY time you are touching this codebase — bug fixes, behavior changes, deployments, doc updates, audits. Encodes the hard rules and the production-only gotchas so you don't have to rediscover them.
---

# Mileage for Clockify — Development Skill

You are working on a standalone Java 21 / Spring Boot 3.3.x add-on for the Clockify Marketplace. The add-on is functionally complete, security-hardened, scale-hardened, and currently runs on OCI at `https://89-168-93-85.sslip.io`; Railway is historical unless explicitly restored. The mode is **maintenance**: small diffs, focused regression tests, full reactor verification for behavior changes, hosted probes after deploys.

## When the user gives you any task in this repo

1. Read `CLAUDE.md`, `AGENTS.md`, and the relevant `README.md` / module docs.
2. Plan the smallest diff that achieves the goal.
3. Run the full reactor before claiming done. Use Docker Desktop wiring on this Mac.
4. After Behavior, Docker, manifest, or migration changes: also run the hosted probe section below.
5. Keep `CLAUDE.md` and `AGENTS.md` current — if a change invalidates a Product Fact, a Hard Rule, or the Hosted State entries, update them in the same PR. The meta-rule in `CLAUDE.md` is non-negotiable.

## Persistence: stay on Postgres

Decision recorded 2026-05-30 (`CLAUDE.md` § "Architecture Decision"). Do not propose a migration to MongoDB. The G1 worker queue depends on `SELECT … FOR UPDATE SKIP LOCKED`, which is a Postgres-specific atomic primitive without a clean equivalent in MongoDB. `BigDecimal ↔ numeric` keeps financial precision aligned, multi-step `@Transactional` state transitions rely on Postgres rollback semantics, and the Flyway + `{h-schema}` per-test schema isolation pattern is wired throughout. Performance is nowhere near PG limits at our workload.

## Local environment file

`~/.config/clockify-mileage.env` (mode 600) is sourced from `~/.zshrc`. All five `CLOCKIFY_*` variables for the sacrificial developer workspace (`672f9cf4ad6f45299c3e3de2`) are set, including the API key — the workspace auto-resets so the key is sandbox-grade and a leak self-invalidates. `NVD_API_KEY` is the only remaining placeholder. Probe with `[ -n "$VAR" ] && echo set || echo MISSING`. Never echo a value. If `/user` returns 401 the dev workspace has reset; ask the user for a fresh key and re-install the addon at `https://89-168-93-85.sslip.io/manifest` unless a different live target was explicitly requested.

## Hard rules — these crash production if you violate them

| Rule | Why |
|---|---|
| No `float` / `double` / `Float` / `Double` for mileage, rate, money values. Use `BigDecimal`. | Money rounding errors compound across many conversions. Tests enforce this with a static rg scan. |
| No hardcoded Clockify API hosts. Use claims `backendUrl` or installation context. | The add-on routes traffic per Clockify region; hardcoding the global host breaks EU customers. |
| Never expose installation tokens to frontend JS/HTML, logs, docs, screenshots, or test output. | A leaked installation token grants workspace-scope access until rotation. |
| Webhook handlers must acknowledge HTTP 2xx even after internal failure (logging the failure first). | Clockify retries 4xx/5xx; blindly retrying internal failures storms us. |
| Webhook controller MUST NOT call `AddonWebhookHandler.handle` or any Clockify gateway method on the request thread when a `WebhookJobQueue` bean is wired. Verify → dedupe → enqueue PENDING → 2xx. | The G1 async contract; reverting reintroduces the timeout-and-retry storm. |
| `WebhookJobWorkerConfig` MUST stay an `@AutoConfiguration(after = AddonDbAutoConfiguration.class)` registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. | Plain `@Configuration` (even with `@ConditionalOnBean` at the `@Bean` method level) silently skips the worker beans in production. The 2026-05-30 deploys `33e2c56c` and `fdf6a328` proved this; only `9d89508d` registered the worker. |
| Lifecycle `DELETED` cleanup for `AddonWebhookToken` MUST stay a scoped bulk DML delete by workspace and add-on key. | Spring Data entity deletes can stale-delete token rows during rapid Clockify uninstall/reinstall and log `ObjectOptimisticLockingFailureException` / `StaleObjectStateException` even though Clockify receives 200. |
| Worker `claimNext` transaction must wrap `SELECT … FOR UPDATE SKIP LOCKED` and the status flip to `CLAIMED` in ONE transaction. Do NOT extend it across the handler dispatch. | The Clockify HTTPS write must happen outside any DB lock so the row lock doesn't span a network call. |
| Prometheus counters/gauges may be tagged ONLY by stable enums (`outcome`, `status`). Never tag with `userId`, `workspaceId`, `expenseId`, token values. | Cardinality explodes; tagged identifiers leak into scrape endpoints. `MileageConversionMetricsTest` enforces this. |
| Do not lower `failBuildOnCVSS` below `7.0` in the OWASP dep-check plugin. Document suppressions; never blanket-skip findings. | The HIGH/CRITICAL gate is the whole point. |
| New Flyway migrations must be numbered AFTER the highest applied production migration (currently V20 — the deferred note-charge reconcile column). | Flyway validates strict ordering and crashes boot with `Detected resolved migration not applied to database: N`. The d11e2088 deploy crashed on this with V7. |
| Native expense conversion must aggressively prevent loops: skip mileage audit markers, output-category expenses, and already-converted expenses before writing back to Clockify. Skipped loop-guard webhooks must not rewrite an existing successful audit row away from `CONVERTED`. | The addon's own update fires another `EXPENSE_UPDATED` webhook; without the guard you'd recursively re-convert, and without audit preservation add-on-created rows can appear as `SKIPPED`. Pinned by `updatedWebhookOnAlreadyConvertedExpenseSkipsToBreakLoop` and `addonFormConversionStaysConvertedWhenCreateWebhookRacesAfterCreateResponse`. |
| User-facing mileage creation must use the verified Clockify user JWT claim. Do not add `userId` back to the create DTO or multipart allowlist. | Anything else lets a user impersonate another user. |
| No task selector / `taskId` / `TASK_READ` scope unless the product requirement changes and live scope evidence is captured first. | Manifest-scope minimization. |
| Keep `addon-core` and `addon-db` changes conservative. ASK before structural changes. | These are shared platform modules; structural drift breaks future add-ons. |

## Commands you'll use

```bash
# Repo-local publish safety bundle (runs the whole gate)
./scripts/verify-publish.sh

# Fast focused reactor (most behavior work)
mvn -pl addon-expenses-rest-api -am test

# Clean reactor (use after dep bumps or weird state)
mvn -pl addon-expenses-rest-api -am clean test

# Full reactor with Docker Desktop Testcontainers — the canonical local CI parity check
DOCKER_HOST=unix:///Users/15x/.docker/run/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test

# Docker image (builds BOTH addon web pod and addon-worker pod)
docker compose -f addon-expenses-rest-api/docker-compose.yml build

# OWASP dep-check (requires NVD_API_KEY for fast scan; CI dep-check job runs it with secret)
NVD_API_KEY=… mvn -pl addon-expenses-rest-api -am -DskipTests dependency-check:check
```

## Test schemas can carry stale Flyway history

If you've ever applied a migration locally that got renamed (like the V7→V17 case), the local Postgres `addon_db_test`, `mileage_test`, and `mileage_skiplocked` schemas inside `addon_test` retain that history and reactor tests crash with `Detected applied migration not resolved locally`. Drop and re-create:

```bash
PSQL=/opt/homebrew/opt/postgresql@16/bin/psql
for s in addon_db_test mileage_test mileage_skiplocked; do
  $PSQL -h localhost -p 5432 -U 15x -d addon_test -c "DROP SCHEMA IF EXISTS $s CASCADE;"
done
```

## Deploy + verify

OCI is the current hosted target. Railway is historical unless explicitly restored, and Cloudflared remains the local live-test fallback.

OCI path:
1. `mvn -pl addon-expenses-rest-api -am package -DskipTests`.
2. Copy `addon-expenses-rest-api/target/mileage-for-clockify-0.1.0-SNAPSHOT.jar` to the OCI host and replace `/opt/mileage-for-clockify/mileage-for-clockify.jar` after taking a timestamped backup.
3. `sudo systemctl restart mileage-for-clockify.service`.
4. Probe `https://89-168-93-85.sslip.io/actuator/health`, `/manifest`, static assets, and prometheus worker lines.
5. For reinstall lifecycle fixes, reinstall the add-on and grep fresh `journalctl -u mileage-for-clockify.service` logs for absence of `Lifecycle DELETED handler ... failed`, `ObjectOptimisticLockingFailureException`, and `StaleObjectStateException`.

Cloudflared dev/live verification:
1. `scripts/dev-tunnel.sh --build`.
2. Paste the printed `https://<random>.trycloudflare.com/manifest` into Clockify. Quick-tunnel URLs are ephemeral; reinstall after every restart.
3. Probe health, manifest, static assets, iframe, prometheus metric families, and worker liveness through the tunnel URL.
4. Run the live Clockify E2E webhook smoke against the installed dev workspace.
5. Append dated evidence to `addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md`.

Railway path if the subscription is restored:
1. Push to `main`.
2. `railway up --service mileage-for-clockify --detach` to trigger the deploy (Railway is not auto-deploy from GitHub on this project).
3. Poll `railway deployment list` until the new id transitions to `SUCCESS` (≈2–3 min build + 30s deploy).
4. Run the hosted probe section below. Any 4xx/5xx on health/manifest, or missing metric family, is a regression.
5. Append a dated evidence block to `addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md`.

For a structured run, dispatch the `mileage-deployer` subagent.

## Hosted probe set (run after every deploy)

```bash
BASE=https://89-168-93-85.sslip.io
curl -sS -w "\n  %{http_code}\n" "$BASE/actuator/health"
curl -sS -w "\n  %{http_code}\n" "$BASE/manifest"
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings-date.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings-core.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings-ranges.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings-create.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings-admin.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings-tables.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings.js"      | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/report.css"       | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/report.js"        | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/icon.png"         | head -3
curl -sS -o /dev/null -D - "$BASE/iframe/mileage"                  | head -10
# /iframe/report is wired + guarded: no auth_token => 401 (a real report needs the iframe's auth_token query param)
curl -sS -o /dev/null -w "  %{http_code}\n" "$BASE/iframe/report"
# G3 metrics — confirm all three families and check for no PII tags
curl -sS "$BASE/actuator/prometheus" | grep -E "^mileage_conversion_outcome_total|^mileage_webhook_queue_depth|^mileage_webhook_job_process_seconds_count"
# Worker liveness — proves the @Scheduled poll loop is hitting the DB
curl -sS "$BASE/actuator/prometheus" | grep -E "^tasks_scheduled_execution_seconds_count.*pollAndProcess.*outcome=\"SUCCESS\""
```

After static asset deploys, live-click Mine, Team, and Conversions CSV buttons in the installed iframe and verify non-empty downloads plus `/api/mileage/*.csv` 200 metrics. CSV export clicks intentionally share the delegated `handleCsvExport` handler in `settings-tables.js`, wired once by boot `settings.js`; do not split them back into per-button listeners without live Conversions CSV proof. Also probe the two report assets (`/assets/mileage/report.css`, `/assets/mileage/report.js`) and confirm `GET /iframe/report` with no `auth_token` returns `401` (route wired + guarded). The Report buttons (Mine + Team) open `/iframe/report` in a new tab via `handleReportClick`. The report now lists ALL Clockify expenses (mileage rows show the add-on's reconciled miles/rate/amount; everything else native). The Team report opens with NO user selected (admin → all users, adds a User column); selecting a user scopes it. Mine is always the requester's own. Native expenses come from `ClockifyExpenseGateway.listExpensesForReport` (backend `getExpenses`, paged, client-side date filter, `total` cents → major); it degrades to reconciled-mileage-only rows with a banner if Clockify is unreachable. After a deploy, open the report with no user selected and confirm both a mileage row (reconciled amount) and a non-mileage row (native amount) appear.

Product notes for this feature set: fresh workspaces default the rate to `0.725` (`MileageSettingsService.defaults()`); the converted-note template is admin-editable in Settings (`settings-note-template`, ≤500 chars) and `MileageNoteService` always appends the hidden loop-safe marker when a custom template omits both the marker and the human signature; Team/Conversions admin views and CSVs take an optional `userId` filter backed by `GET /api/mileage/options/users`.

UI/UX uplift notes (2026-06-06): the printable report renders the **Amount/Total columns as money at 2 dp** (`HALF_UP`) — `MileageReportRendererTest`/`MileageReportControllerTest` lock `25.39`/`18.00`, not full precision; don't "fix" it back. The create-form **Project picker is a searchable `<input list="project-options">` + `<datalist>`** (not a `<select>`); `resolveProjectId` maps the typed name → `projectId`. The iframe shell is a CSP-safe ARIA `role="tablist"` (roving tabindex; `MileageSecurityTest` locks the panel/tab markup), error toasts are `role="alert"` + dismissible (no auto-vanish), and Mine/Team/Conversions tables paginate (`settings-tables.js renderPager`, `&page=` after the locked `pageSize=50" + query` strings) + collapse to mobile cards via `data-label`. Dark-mode primary buttons use `--on-accent #07232a` ink (≈6.3:1) — never white-on-teal (2.42:1 WCAG fail). All styling stays in `settings.css`/`report.css`; do not introduce inline `<style>`/`style=`/`onclick=` (tests assert their absence). Static-asset deploy probes include every `settings*.js` file.

## Live Clockify E2E webhook smoke

Requires:
- The add-on installed in a sacrificial workspace (Clockify dashboard → Apps → install from the current hosted or Cloudflared `/manifest` URL).
- `CLOCKIFY_API_KEY`, `CLOCKIFY_WORKSPACE_ID`, `CLOCKIFY_API_BASE_URL` set in the operator's shell.
- The Clockify expense-create multipart contract: send the miles value in the **`amount`** multipart field, NOT `quantity`. Clockify computes `total = amount × unitPrice` and writes the resulting `quantity` back. Sending `quantity=N` silently records `quantity=0`. The addon's `ClockifyExpenseGateway.createBody` already uses `amount`; this matters for any ad-hoc smoke hitting Clockify directly.
- After a CONVERTED native expense triggers the addon's own update webhook, the loop guard should tick `SKIPPED` metrics without changing the stored conversion row away from `CONVERTED`.
- Add-on-form creates get the `(Clockify category charge: X)` annotation ASYNCHRONOUSLY via `MileageNoteReconcileWorker` (a `@Scheduled` sweeper registered in `WebhookJobWorkerConfig`, gated by `mileage.worker.enabled`, poll env `MILEAGE_WORKER_NOTE_RECONCILE_POLL_DELAY_MS` default 60000), NOT synchronously on the create request. The earlier synchronous reconcile (a second `updateFlatExpense` to the just-created expense, PR #4/#5) was reverted after live QA 2026-06-05 because it races Clockify's `EXPENSE_CREATED` webhook and hung. When you smoke an add-on create with a diverging category price (e.g. rate 0.725 → category 0.73/mile → 12.4 mi charges 9.05 vs recorded 8.99), the created note is INITIALLY clean (`… = 8.99. Created/converted …`); the parenthetical appears within ~30–90 s after the conversion settles (poll default 60 s, settle floor 30 s). So: assert the clean note immediately after create, then re-fetch after ~90 s and assert `(Clockify category charge: …)` is present. A missing parenthetical immediately after create is EXPECTED, not a regression. On the create request itself a `+1 updateFlatExpense` still happens ONLY in the webhook-reserved-first race (re-marking the persisted conversion id), not for the charge. Do NOT reintroduce a synchronous reconcile on the create request thread (it races the `EXPENSE_CREATED` webhook and hangs).
- Saving Settings best-effort calls `gateway.createOrRepairMileageCategory(ws, rate)` to keep the Clockify Mileage category's unit price in step with the saved rate. To verify, save Settings then GET the category and confirm `priceInCents == round(rate × 100)`.

For a structured run, dispatch the `mileage-webhook-smoke` subagent.

## When the user asks "what's left"

The project is in maintenance mode. There is no debt list. There are no scale gaps. There are no open PRs. Production runs `9d89508d` (or later) with the worker polling at ~4 Hz and zero exceptions. Suggest concrete maintenance items only if asked, and frame them as optional.

## What changes to this skill require

Update this `SKILL.md` AND `CLAUDE.md` / `AGENTS.md` whenever any of the following changes:
- A new Hard Rule is added or an existing one is amended.
- The deploy / probe procedure changes (URL, command, expected response shape).
- A new module is added or a structural refactor moves package responsibilities.
- A new metric, env var, or migration is added.
- A new live-smoke prerequisite is introduced.

If you change code that this skill describes and you do NOT update the skill, the next agent will follow stale guidance. The meta-rule in `CLAUDE.md` makes this a hard requirement.
