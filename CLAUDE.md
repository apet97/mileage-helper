# Claude Code Guide: Mileage for Clockify

This is a standalone Java/Spring Boot Clockify Marketplace add-on repository. The add-on is already implemented; future work should maintain, harden, verify, or extend Mileage for Clockify without replaying the old boilerplate migration.

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
- `com.cake.clockify.addon.mileage.worker`: async webhook job worker (G1) — polls `addon_webhook_jobs` via `SELECT … FOR UPDATE SKIP LOCKED` and dispatches to the same typed handlers as the sync path.
- `com.cake.clockify.addon.mileage.metrics`: Micrometer/Prometheus counters and gauges (G3) — conversion outcome counter and queue-depth gauge.

## Product Facts

- Add-on key: `mileage-for-clockify`.
- Manifest schema: `1.5`.
- Minimum plan: `PRO`.
- Scopes: `EXPENSE_READ`, `EXPENSE_WRITE`, `USER_READ`, `PROJECT_READ`, `WORKSPACE_READ`.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- UI routes: `/iframe/mileage`, `/iframe/settings`.
- User APIs: `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`.
- Mileage create requests intentionally omit `userId`; the backend injects the verified claims user into Clockify create commands and audit rows.
- Mileage create requests intentionally omit `taskId`; the UI follows Clockify's regular expense form, does not fetch task options, and does not require `TASK_READ`. Native/mobile conversion may still preserve task IDs from existing Clockify expense snapshots.
- Manual mileage expenses default to billable when `billable` is omitted; explicit `false` remains non-billable.
- Main-page rate override is hidden and omitted unless workspace settings allow overrides. The backend calculation also ignores submitted override rates when the setting is off.
- Mileage settings use one `Mileage` unit category with fixed unit `mile` and fixed `HALF_UP` rounding; existing input/output category settings normalize to that single category.
- Setup can adopt an existing Clockify `Mileage` UNIT/mile category and derive the local rate from Clockify `unitPrice` cents when no local rate is saved yet.
- Generated Clockify notes are clean and exact, e.g. `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.`
- Expense webhook handlers that need an expense ID accept either full payloads with `id` or reference payloads with `expenseId`; update/delete webhooks have used both shapes in live testing.
- Webhook dispatch records/dedupes events, marks processing outcomes internally, and still returns HTTP 2xx after handler or audit-status failures.
- Webhook handling is async (G1). The `/webhook/**` controller verifies → dedupes → persists a PENDING row in `addon_webhook_jobs` → returns 2xx. NO Clockify writes happen on the request thread. A `WebhookJobWorker` (gated by `mileage.worker.enabled`, default `true`) claims jobs via `SELECT … FOR UPDATE SKIP LOCKED`, runs `tryStartProcessing` for the loop-prevention guard, then invokes the matching `AddonWebhookHandler`. Two-worker SKIP LOCKED correctness is regression-tested with Testcontainers Postgres in `WebhookJobQueueSkipLockedTest`.
- The worker has a second `@Scheduled` reaper that flips CLAIMED rows older than `mileage.worker.stuck-job-timeout-seconds` (default 300) back to PENDING, so a worker crash mid-process self-heals.
- When the controller has no `WebhookJobQueue` bean wired (e.g. legacy MockMvc test paths), it falls back to synchronous dispatch under the same 2xx-after-failure contract — preserves the original `addon-core` behavior for tests that mock the DB.
- Admin retry (`MileageConversionService.retry` → `/api/mileage/conversions/{id}/retry`) is intentionally synchronous; it is an authenticated admin click, not a webhook delivery, so the user expects a direct ConversionResult response.
- Native conversion eligibility skips disabled/incomplete settings, workspace mismatches, output categories, mileage note markers, existing successful conversions, non-input categories, missing/invalid quantity, and locked/finalized expenses.
- Admin APIs: settings, Mileage category repair, diagnostics, category options, team mileage list/export, conversion list/detail/retry/export under `/api/mileage`.
- Add-on previews and mileage tables show full `calculatedAmount` decimals first; Clockify Expenses still receives the rounded `roundedAmount`.
- Mileage lists and CSV exports filter by actual `expenseDate`, defaulting to the current US week, Sunday through Saturday.
- User-facing `Mine` and admin `Team` lists/CSVs exclude rows marked `DELETED`; admin `Conversions` keeps deleted rows as audit history.
- All three mileage CSV exports emit the 17-column header `expense_id,source,source_label,status,user_id,user_name,project_id,project_name,miles,rate,calculated_amount,expense_amount,rounding_mode,expense_date,updated_at,converted_at,note_marker`. `user_name` is resolved live via `gateway.listUsers` for admin team/conversions exports and left empty for `mine.csv`; `project_name` is resolved live via `gateway.listProjects` for all three endpoints. When name lookup fails (network/RuntimeException) the helper returns an empty map and rows ship with empty name cells; the IDs remain authoritative.
- Tables: `mileage_workspace_settings`, `mileage_conversion`. Platform tables (in `addon-db`): `addon_installations`, `addon_webhook_tokens`, `addon_workspace_settings`, `addon_webhook_events`, and `addon_webhook_jobs` (G1 async queue — Flyway `V17__addon_webhook_jobs.sql`, indexes on `(status, created_at)`, `(addon_key, workspace_id)`, `event_id`, partial on `claimed_at WHERE status='CLAIMED'`). The migration is numbered V17 (not V7) because production already had V10–V16 applied from the mileage module; Flyway validates in strict order by default, so any new `addon-db` platform migration must be numbered after the highest applied mileage migration.
- Prometheus metrics are scraped from `/actuator/prometheus` (management exposure: `health,info,prometheus`). Counters: `mileage_conversion_outcome_total{outcome=CONVERTED|SKIPPED|FAILED|DRY_RUN|DELETED|RESTORED_IGNORED|...}` from `MileageConversionMetrics` (recorded on every return path of `MileageConversionService`). Gauge: `mileage_webhook_queue_depth{status=PENDING}` from `WebhookJobMetrics`. Timer: `mileage_webhook_job_process` for worker dispatch latency. HTTP enqueue latency is covered automatically by Spring Boot's `http_server_requests_seconds` timer.
- METRIC TAGGING RULE: counters and gauges may be tagged ONLY by stable enums (`outcome`, `status`). Never tag with `userId`, `workspaceId`, `expenseId`, token values, or any identifier — Prometheus cardinality explodes and tagged IDs leak into scrape endpoints. `MileageConversionMetricsTest` enforces this.
- Hikari pool is sized explicitly in `application.yaml` (G2): `maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=30000`. All three are env-overridable via `SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE`, `SPRING_DATASOURCE_HIKARI_MIN_IDLE`, `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT`.
- Docker compose runs two services from the same image (G1): `addon` (web pod, `MILEAGE_WORKER_ENABLED=false`) and `addon-worker` (no port mapping, `MILEAGE_WORKER_ENABLED=true`). Scale workers horizontally with `docker compose up --scale addon-worker=N`.
- OWASP `dependency-check-maven` 10.0.4 (G4) is wired in root `pluginManagement` with `failBuildOnCVSS=7.0`, JSON+HTML reports, NVD key from `${env.NVD_API_KEY}`. Suppression registry lives at `dependency-check-suppressions.xml`. CI runs the gate in the `dep-check` job (`.github/workflows/ci.yml`) with NVD data cache; the local-only run is impractical without an NVD API key and is deferred to CI.
- `ClockifyClientFactory` builds per-workspace clients from installed backend/reports URLs. Missing backend URL is fatal; missing reports URL is allowed until a reports request is attempted.
- Clockify token timezone normalization accepts `userTimeZone`, `userTimezone`, `timeZone`, `timezone`, and `tz`; keep this aligned with the settings UI timezone alias handling.
- The settings UI depends on `/assets/mileage/settings-date.js` loading before `/assets/mileage/settings.js`. The helper owns date presets/default dates so frontend ranges use the Clockify claim timezone, with browser-local fallback for invalid timezone claims.
- After static asset deploys, probe both settings JS assets. Do not treat the old single `settings.js` probe as full proof for the current UI.
- Receipt uploads in `clockify-rest-client` use the shared multipart helper. Expenses and Files clients must not hand-roll multipart headers because field names, filenames, and content types need the same defensive handling.
- Spring multipart limits are pinned to 10 MB file / 12 MB request in `addon-expenses-rest-api/src/main/resources/application.yaml`. `MileageApiController.MAX_RECEIPT_BYTES` (10 MB) remains the friendly-error layer that emits `Receipt file exceeds 10 MB`; `MileageExceptionHandler.handleMaxUploadSize` maps the servlet-level `MaxUploadSizeExceededException` to the same 400 body so requests above the cap never surface as 500.
- Historical pre-Mileage migrations V5/V10 are retained for Flyway validation only; V12 drops their leftover generic tables. New code/docs should not add `temp_addon_expenses*`, `clockify-expenses-api`, `Clockify Expenses API`, or `com.cake.clockify.addon.expenses` references.

## Hosted State

- Production test URL: `https://mileage-for-clockify-production.up.railway.app`.
- Production manifest URL: `https://mileage-for-clockify-production.up.railway.app/manifest`.
- Use `railway deployment list` for the current Railway deployment ID. Do not treat old deployment IDs in notes, chats, or previous evidence as current truth.
- Dated deployment evidence belongs in the pre-publish checklist after each deploy.
- Pre-deploy hosted recheck, dated 2026-05-27: `/actuator/health` and `/manifest` passed, but `/assets/mileage/settings-date.js` returned `404`, proving production was still serving an older deployment.
- Post-deploy hosted recheck, dated 2026-05-27: `/actuator/health`, `/manifest`, `/assets/mileage/settings-date.js`, `/assets/mileage/settings.js`, `/assets/mileage/icon.png`, and unauthenticated `/iframe/mileage` probes passed. New deploys must prove both settings JS assets again.
- Post-deploy hosted recheck, dated 2026-05-28, deployment `2287245e-a4cf-4bf0-ab0f-fa4d94566b93` at git `3fbe57c` (`feat(mileage): enrich CSV exports with project_name and pin multipart limits`): same six probes all green; `/iframe/mileage` returned 401 with the same CSP/HSTS/no-store/Permissions-Policy header set.
- Historical live Clockify smoke, dated 2026-05-27: uninstall/install/settings/create/delete passed after the deleted-expense webhook fix. Treat this as historical unless rerun.
- Expanded live Clockify API smoke on 2026-05-27 used local environment secrets only and proved workspace/user/category read probes plus sacrificial Mileage receipt expense create, fetch, full update, delete, and post-delete non-success (`400`). Follow-up receipt probes created sacrificial PNG and valid generated PDF receipts, observed `fileId`, downloaded nonzero binary content through `GET /expenses/{expenseId}/files/{fileId}`, then deleted both expenses. A malformed hand-written PDF fixture returned zero bytes and should not be used as proof of product behavior.
- Local hardening review on 2026-05-27 added shared multipart upload tests, Clockify timezone alias normalization tests, Mileage security tests, date-helper static asset checks, `node --check`, `git diff --check`, and `gitleaks` proof.
- Railway Postgres currently logs a Flyway compatibility warning because the managed database is PostgreSQL 18.4 and the bundled Flyway version officially supports older versions. Boot and migrations still completed.

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

If Testcontainers/Docker discovery hangs locally, use the Colima wiring that passed on this Mac:

```bash
DOCKER_HOST=unix:///Users/15x/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test \
  -Ddocker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy \
  -Ddocker.host=unix:///Users/15x/.colima/default/docker.sock \
  -Dapi.version=1.44
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
- `PORT`

Build-time / CI:

- `NVD_API_KEY` (G4; used by the CI `dep-check` job. Without it, the local OWASP scan stalls on slow NVD throttling — run dep-check in CI).

Live sacrificial Clockify checks may use shell environment variables such as `CLOCKIFY_API_BASE_URL`, `CLOCKIFY_API_KEY`, `CLOCKIFY_WORKSPACE_ID`, `CLOCKIFY_TEST_USER_ID`, and `CLOCKIFY_TEST_PROJECT_ID`. Never print secret values.
Pass live secrets through environment variables, stdin, or a local secret store; never write real keys into repo files, command transcripts intended for docs, or final reports.
Live Clockify smoke is optional and requires local secrets. Never commit or echo API keys/tokens. If not run, final output must say it was skipped.

Default CORS allows Clockify origins and the origin from `ADDON_BASE_URL`, which keeps local ngrok iframe/API testing working without adding a broad wildcard.

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
- Native expense conversion must aggressively prevent loops: skip mileage audit markers, output-category expenses, and already-converted expenses before writing back to Clockify.
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

`./scripts/verify-publish.sh` checks both mileage settings JS assets plus `scripts/test-mileage-date-helpers.mjs`. If a deploy follows, include hosted probes for `/assets/mileage/settings-date.js` and `/assets/mileage/settings.js`.

## Final Hardening History

- `docs/superpowers/plans/2026-05-26-mileage-final-hardening.md` is historical. The final hardening work landed on `main` at `4830230`; do not treat that plan as an active work queue.
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
