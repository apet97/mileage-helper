# Mileage for Clockify Pre-Publish Checklist

## Required Gates Run Now

Run from the repository root before claiming publish readiness:

- [ ] `git status --short --branch` reviewed.
- [ ] `./scripts/verify-publish.sh` passes.
- [ ] If a new OCI deploy was made, the systemd restart time and fresh `journalctl` error scan were captured.
- [ ] If a new hosted deploy was made, hosted `/actuator/health`, `/manifest`, `/assets/mileage/settings-date.js`, `/assets/mileage/settings.js`, and icon probes passed and the dated evidence was added below.
- [ ] If Railway was explicitly used, `railway deployment list` was used for that run's deployment ID.

`./scripts/verify-publish.sh` runs these local publish-safety checks:

- `mvn -pl clockify-rest-client -Dtest=ExpensesClientTest,FilesClientTest test`
- `mvn -pl addon-core -Dtest=ClaimsNormalizerTest test`
- `mvn -pl addon-expenses-rest-api -am -Dtest=MileageSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test`
- Docker Desktop-backed `mvn -pl addon-expenses-rest-api -am test`
- `git diff --check`
- `node --check addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings-date.js`
- `node --check addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js`
- `node scripts/test-mileage-date-helpers.mjs`
- `gitleaks detect --source . --no-git --redact --verbose`
- `docker compose -f addon-expenses-rest-api/docker-compose.yml build`

## Optional Live Clockify Smoke

Live Clockify smoke is optional and requires local secrets. Never commit or echo API keys/tokens. If not run, final output must say it was skipped.

There is no repo-owned live smoke script. Use only environment variables, stdin, or a local secret store when manually testing a sacrificial Clockify workspace. Do not paste real keys into repo files, docs, screenshots, terminal transcripts intended for docs, or final reports. Live Clockify mutation is historical evidence unless it is rerun for the current deploy.

## Last Evidence Snapshot

Historical only: do not treat this section as current truth unless the listed checks were rerun. Dated deployment evidence belongs here after each deploy.

Last local/live stabilization pass: 2026-06-04.

- 2026-06-04 OCI reinstall cleanup deploy at `https://89-168-93-85.sslip.io`: built `addon-expenses-rest-api/target/mileage-for-clockify-0.1.0-SNAPSHOT.jar`, replaced `/opt/mileage-for-clockify/mileage-for-clockify.jar`, and restarted `mileage-for-clockify.service` at `2026-06-04 03:17:51 UTC`. Boot reached `Started MileageAddonApplication` with Flyway schema `public` current at V19 and no migration needed.
- 2026-06-04 hosted probes: `/actuator/health` returned `200` status `UP`; `/manifest` returned `200`, schema `1.5`, key `mileage-for-clockify`, baseUrl `https://89-168-93-85.sslip.io`; `/assets/mileage/settings-date.js`, `/assets/mileage/settings.js`, and `/assets/mileage/icon.png` returned `200`.
- 2026-06-04 reinstall lifecycle verification: after reinstall, fresh service logs since restart contained `mileage-for-clockify.installed workspace=69bda6b317a0c5babe34b4ff` and no `Lifecycle DELETED handler ... failed`, `ObjectOptimisticLockingFailureException`, or `StaleObjectStateException`.
- 2026-06-04 live UI smoke in the installed Clockify add-on: Settings showed rate `0.73`, `Mileage (UNIT: mile, 0.73/mile)`, enabled settings, and Save Settings remained usable; Mine exercised project selector, receipt chooser cancel, billable checkbox, preview, create, refresh, and CSV. Sacrificial expense `6a20f36ce85c9e6e702953e3` previewed `1.2 miles x 0.73 = 0.876`, created as `CONVERTED`, appeared in Mine and Team with John Owner, exported fresh non-empty `mileage-mine (2).csv` and `mileage-team (3).csv`, then was deleted through Clockify API (`DELETE 200`, post-delete GET `400`). Delete webhook cleanup produced `/webhook/**` POST count `2`, worker process count `2`, `DELETED +1`, `SKIPPED +1`, queue depth `0`, and no fresh service WARN/ERROR lines.
- 2026-06-04 Conversions/Diagnostics browser pass: Conversions loaded and refreshed the sacrificial row under a custom date range with no console warnings/errors, and Diagnostics showed Installation `OK`, Settings `OK`, Native conversion `OK`. Chrome extension control did not produce a fresh browser download event for the Conversions CSV button; do not treat Conversions CSV as proven by this browser pass unless rerun with a confirmed backend `/api/mileage/conversions.csv` hit.
- 2026-06-04 final local verification after the reinstall cleanup/docs patch: `git diff --check` passed; `mvn -pl addon-db -am -Dtest=JpaPersistenceLifecycleHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed with 6 tests; Docker Desktop-backed `DOCKER_HOST=unix:///Users/15x/.docker/run/docker.sock mvn -pl addon-expenses-rest-api -am test` passed with 212 tests.

- Local small-hardening pass on 2026-05-27: `./scripts/verify-publish.sh`
  passed after the settings date helper split and 307/308 redirect hardening.
  This pass did not deploy and did not run live Clockify mutation smoke.
  Final reports for that local-only pass must say live Clockify smoke was
  skipped.
- Follow-up expanded live Clockify API smoke on 2026-05-27: using local
  environment secrets only, direct API probes confirmed the current user,
  workspace list, target workspace, first workspace users page, first projects
  page, and active `Mileage` expense category. The same pass created a marked
  sacrificial Mileage receipt expense, fetched it, verified the marker, updated
  it with the full app-style update payload, deleted it, and confirmed a
  post-delete fetch returned non-success (`400`). Follow-up receipt probes
  created marked sacrificial PNG and valid generated PDF receipts, observed
  `fileId`, downloaded nonzero binary content through
  `GET /v1/workspaces/{workspaceId}/expenses/{expenseId}/files/{fileId}`, then
  deleted both expenses and confirmed post-delete fetches returned non-success
  (`400`). A malformed hand-written PDF fixture returned `200` with zero bytes;
  do not use that fixture as product evidence. No API key or token is stored in
  this repo.
- Pre-deploy hosted split-asset recheck on 2026-05-27: `/actuator/health` and
  `/manifest` returned `200`, but `/assets/mileage/settings-date.js` returned
  `404`, proving production was still serving an older deployment. Do not claim
  the split date-helper hardening is live until a post-deploy probe passes for
  both settings JS assets.
- Post-deploy hosted split-asset recheck on 2026-05-27: a Railway deployment
  reached `SUCCESS`; use `railway deployment list` for the current deployment
  ID instead of treating this dated evidence as current truth. Public probes
  passed for `/actuator/health`, `/manifest`, `/assets/mileage/settings-date.js`,
  `/assets/mileage/settings.js`, `/assets/mileage/icon.png`, and
  unauthenticated `/iframe/mileage` (`401` with no-store behavior).
- Local small-hardening pass on 2026-05-27: `node --check` passed for
  `/assets/mileage/settings-date.js` and `/assets/mileage/settings.js`;
  `node scripts/test-mileage-date-helpers.mjs` passed claim-timezone and invalid
  timezone fallback checks.
- Local small-hardening pass on 2026-05-27: `mvn -pl clockify-rest-client
  -Dtest=TransportRetryAndConfigTest test` passed with focused 307/308
  redirected POST body/content-type regression coverage.
- Public hosted recheck on 2026-05-27: `railway deployment list --limit 1
  --json` showed the latest listed deployment in `SUCCESS` state, created at
  `2026-05-26T22:57:34.506Z`; use `railway deployment list` for the current
  Railway deployment ID.
- Public hosted recheck on 2026-05-27: `/actuator/health` returned `200` with
  `{"status":"UP","groups":["liveness","readiness"]}`.
- Public hosted recheck on 2026-05-27: `/manifest` returned `200`, schema
  `1.5`, key `mileage-for-clockify`, scopes `EXPENSE_READ`, `EXPENSE_WRITE`,
  `USER_READ`, `PROJECT_READ`, `WORKSPACE_READ`, and webhooks
  `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- Public hosted recheck on 2026-05-27: settings JS returned `200` with
  `Content-Type: text/javascript`, `/assets/mileage/icon.png` returned `200`
  with `Content-Type: image/png`, and unauthenticated `/iframe/mileage`
  returned `401` with `Cache-Control: no-store`, CSP, `nosniff`,
  `no-referrer`, and permissions-policy headers.
- Earlier live Clockify mutation smoke on 2026-05-27: using local environment
  secrets only, a direct API smoke created one marked sacrificial expense with
  multipart form fields, fetched it, deleted it, and confirmed a post-delete
  fetch returned non-success (`400`). The smoke used the existing `Mileage`
  category and an existing active project. No API key or token is stored in
  this repo.
- `git diff --check`: passed after the multipart and timezone hardening pass.
- `node --check addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings-date.js`: passed for the local small-hardening pass.
- `node --check addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js`: passed.
- `node scripts/test-mileage-date-helpers.mjs`: passed for the local small-hardening pass.
- `gitleaks detect --source . --no-git --redact --verbose`: passed with no leaks found.
- 2026-05-28 ship + redeploy at git `3fbe57c` (`feat(mileage): enrich CSV exports with project_name and pin multipart limits`): `./scripts/verify-publish.sh` passed end-to-end including Docker/Testcontainers-backed reactor, `node --check` on both settings JS assets, `node scripts/test-mileage-date-helpers.mjs`, `git diff --check`, `gitleaks` (no leaks), and Docker image build.
- 2026-05-28 Railway deployment `2287245e-a4cf-4bf0-ab0f-fa4d94566b93` reached `SUCCESS` at `2026-05-27T23:48:36.784Z` (UTC); previous deployment `63265289-2fd5-4abf-bea3-8c2a16048bb2` is `REMOVED`. Use `railway deployment list` for the current deployment ID instead of treating this dated entry as current truth.
- 2026-05-28 public hosted recheck against `https://mileage-for-clockify-production.up.railway.app`: `/actuator/health` returned `200 {"status":"UP","groups":["liveness","readiness"]}`; `/manifest` returned `200`, schema `1.5`, key `mileage-for-clockify`, baseUrl matched; `/assets/mileage/settings-date.js` and `/assets/mileage/settings.js` returned `200 text/javascript`; `/assets/mileage/icon.png` returned `200 image/png`; unauthenticated `/iframe/mileage` returned `401` with CSP `frame-ancestors https://app.clockify.me https://*.clockify.me`, HSTS `max-age=63072000; includeSubDomains`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, and Permissions-Policy locking camera/microphone/geolocation.
- 2026-05-30 G1–G4 scale-hardening deploy at git `011d4e8`, Railway deployment `9d89508d-9592-45df-b2dc-4b650d38fb10` (`SUCCESS`). Closes the four scale gaps after three production-only fixes during the session: Flyway V7→V17 renumber, `WebhookJobWorkerConfig` `@ConditionalOnBean` moved to `@Bean` method level, final promotion to `@AutoConfiguration(after=AddonDbAutoConfiguration.class)` with `META-INF/spring/...AutoConfiguration.imports` registration. Six baseline probes green; Flyway boot log shows `Migrating schema "public" to version "17 - addon webhook jobs"`. `/actuator/prometheus` exposes `mileage_conversion_outcome_total` for all 9 statuses, `mileage_webhook_queue_depth{status="PENDING"}=0`, `mileage_webhook_job_process_seconds_*`. Worker liveness confirmed via Spring's auto-bound `tasks_scheduled_execution_seconds_count{code_function="pollAndProcess",outcome="SUCCESS",exception="none"}=977` over 4 min (~4 Hz, matching `MILEAGE_WORKER_POLL_DELAY_MS=250`) and `code_function="reapStuckJobs"=5` (~1/min). Avg poll latency 3.9 ms, max 12.8 ms. Zero PII tags on any `mileage_` line.
- 2026-05-30 live Clockify E2E webhook smoke against deployment `9d89508d` from sacrificial developer workspace `672f9cf4ad6f45299c3e3de2`. One Mileage expense at 12.4 mi × $7.25/mi traversed the entire pipeline: Clockify `EXPENSE_CREATED` → 200 on `/webhook/**` → controller enqueue → worker `claimNext` (SKIP LOCKED) → `MileageConversionService.convertIfEligible` → `gateway.updateFlatExpense` → expense rewritten with the canonical note `"Mileage reimbursement: 12.4 miles x 7.25123 = 89.915252. Created/converted by Mileage for Clockify…"` and `total=8990` cents. The addon's own update triggered a second `EXPENSE_UPDATED` webhook which the loop-prevention guard correctly refused (`outcome="SKIPPED"`). Cleanup delete fired `EXPENSE_DELETED` → `markDeleted` → `outcome="DELETED"`. Cumulative test deltas: `/webhook/**` POSTs +3, worker timer count +3, CONVERTED +1, SKIPPED +1 (loop guard), DELETED +2. Per-job latency avg 334 ms, max 805 ms (CONVERTED makes two Clockify roundtrips). Queue depth stayed 0 throughout. Zero `exception` tags on any worker invocation. Sacrificial expenses deleted; post-delete GET returned `400 "Expense doesn't belong to Workspace"`. No secrets persisted.
- 2026-05-31 Cloudflared/Docker live E2E against developer workspace `69bda6b317a0c5babe34b4ff`: tunnel manifest returned `200`, schema `1.5`, key `mileage-for-clockify`; `/actuator/health` returned `UP`; `/actuator/prometheus` exposed worker liveness (`pollAndProcess`, `reapStuckJobs`), queue depth `0`, and all mileage outcome counters. Browser/devtools pass loaded the installed Clockify add-on iframe and swept Mine, Settings, Conversions, and Diagnostics with zero console warnings/errors. UI create found and fixed a race where Clockify's create webhook could overwrite the successful `ADDON_FORM/CONVERTED` audit row as `WEBHOOK_CREATED/SKIPPED`; regression test `MileageConversionServiceTest.addonFormConversionStaysConvertedWhenCreateWebhookRacesAfterCreateResponse` now pins the invariant. Retest created expense `6a1c673e8c295653c18d8e31`, and after the loop-guard webhook the DB row stayed `ADDON_FORM|ADDON_FORM|CONVERTED` with 4.4 miles at rate 0.725. Native Clockify create of expense `6a1c6768da5b62da684af926` at 5.6 miles produced DB row `CONVERTED`, Clockify note `Mileage reimbursement: 5.6 miles x 0.725 = 4.06 (Clockify category charge: 0.73). Created/converted by Mileage for Clockify.`, metrics `CONVERTED +1`, loop-guard `SKIPPED +1`, worker timer `+2`, queue depth `0`. Cleanup deleted native and both UI sacrificial expenses (`DELETE 200`, post-delete GET `400` for all three), metrics `DELETED +3`, worker timer `+3`, queue depth `0`. No secrets persisted.
- 2026-05-30 maintenance pass at git `<pending-commit>`: bumped Spring Boot 3.3.5 → 3.3.13 (LTS patch line, addresses CVE backports through May 2026), OWASP `dependency-check-maven` 10.0.4 → 12.2.2 (fixes the H2 `URL VARCHAR(1000)` overflow on 2026-era CVE entries that crashed the previous CI dep-check job), added the `ALREADY_CONVERTED` loop-guard regression test (`MileageConversionServiceTest.updatedWebhookOnAlreadyConvertedExpenseSkipsToBreakLoop` — pins the production-observed behavior so a future eligibility refactor cannot regress it), explicit Railway env vars (`MILEAGE_WORKER_ENABLED=true`, `MILEAGE_WORKER_POLL_DELAY_MS=250`, `MILEAGE_WORKER_BATCH_SIZE=8`, `MILEAGE_WORKER_STUCK_JOB_TIMEOUT_SECONDS=300`), and a `postgres:16` service block in CI `build-test` so the addon-db integration tests run against a real Postgres instead of crashing on connection-refused. Full reactor: 205 tests, BUILD SUCCESS via Docker/Testcontainers.
- `mvn -pl clockify-rest-client -Dtest=ExpensesClientTest,FilesClientTest test`: passed with 15 tests covering receipt expense uploads and shared file upload multipart sanitization.
- `mvn -pl addon-core -Dtest=ClaimsNormalizerTest test`: passed with 11 tests covering Clockify timezone claim aliases.
- `mvn -pl addon-expenses-rest-api -Dtest=MileageSecurityTest test`: passed with 30 security tests.
- Docker/Testcontainers-backed `mvn -pl addon-expenses-rest-api -am test`: passed with full reactor `BUILD SUCCESS`:
  `addon-core` 76 tests, `clockify-rest-client` 105 tests, `addon-db` 37 tests,
  and `addon-expenses-rest-api` 180 tests.
- `docker compose -f addon-expenses-rest-api/docker-compose.yml build`: passed.
- Dev workspace live receipt smoke via the local `clockify-rest-client`: created, fetched, and deleted a sacrificial Mileage PDF receipt expense; post-delete GET returned non-success. No API key or token is stored in this repo.
- Runtime `/manifest` probe with the compose stack: returned schema `1.5`, key
  `mileage-for-clockify`, scopes `EXPENSE_READ`, `EXPENSE_WRITE`, `USER_READ`,
  `PROJECT_READ`, `WORKSPACE_READ`, and the four expense webhooks
  `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- Runtime `/iframe/mileage` unauthenticated probe: returned `401` with CSP,
  `nosniff`, `no-referrer`, permissions policy, and `Cache-Control: no-store`.
- Runtime `/assets/mileage/icon.png` probe: returned `200` with
  `Content-Type: image/png`.
- Static forbidden-number scan: no `double`, `Double`, `float`, or `Float`
  usage in Java source/tests.
- Static Clockify-host scan: no hardcoded Clockify API hosts in active main
  code; add-on clients require URLs from installation/token context.
- Static stale/dead-code scan: no active hits for removed expense-boilerplate
  names, deleted REST facade types, legacy handoff docs, old live-evidence docs,
  or deprecated webhook alias names.
- Multipart hardening review: receipt/file upload field names are constrained,
  uploaded filenames are reduced to sanitized basenames, unsafe content types
  fall back to `application/octet-stream`, and Clockify token values are never
  written to multipart logs or docs.
- Timezone alias review: server normalization accepts `userTimeZone`,
  `userTimezone`, `timeZone`, `timezone`, and `tz`, matching the settings UI
  fallback behavior.
- Workspace-isolation review: mileage conversion detail and retry paths now use
  workspace-scoped repository lookups; remaining ID-only repository lookups are
  workspace primary-key reads or internal webhook event status updates.
- Flyway history keeps V5/V10 for existing database validation; V12 drops the
  leftover generic tables.
- Historical Railway deployment for this dated pass reached `SUCCESS`; hosted
  `/actuator/health`, `/manifest`, and settings asset probes passed. Use
  `railway deployment list` for that run's deployment ID.
- Historical live Clockify uninstall/install/settings/create/delete smoke passed
  after the deleted-expense webhook fix. Production audit rows for stale test
  deletes were marked `DELETED` with `deleted_at`, while the then-current live
  expense remained `CONVERTED`.

## Required Manual Product Gates

- [ ] Runtime `/manifest` probe passes.
- [ ] Runtime `/assets/mileage/settings-date.js` probe passes after deploys containing the split date helper.
- [ ] Runtime `/assets/mileage/settings.js` probe passes.
- [ ] Runtime `/assets/mileage/icon.png` probe passes.
- [ ] Static secret scan passes.
- [ ] Manifest uses the production `ADDON_BASE_URL`.
- [ ] Installed lifecycle payload with official webhook entries stores manifest event types.
- [ ] Webhook requests require valid `Clockify-Signature` and `Clockify-Webhook-Event-Type`.
- [ ] `/api/mileage/**` requires a verified user token.
- [ ] User mileage creation uses verified token claims for target user identity; create requests and multipart form fields do not carry `userId`.
- [ ] Installation token is not exposed to frontend HTML, JavaScript, logs, docs, screenshots, or test output.
- [ ] Uninstall removes stored installation and webhook secrets.
- [ ] Diagnostics show installation, settings, and native conversion readiness.
- [ ] UI creates mileage without raw or hidden user ID entry.
- [ ] Deleting a Clockify expense marks the audit row `DELETED` and removes it from `Mine`/`Team` refreshes.
- [ ] Active source has no legacy temp-addon schema names, deleted shell probe, or generic expense-boilerplate references outside immutable Flyway history.

## Required Manual Marketplace Gates

- [ ] Test install in a CAKE.com Marketplace testing environment.
- [ ] Test at least two workspaces.
- [ ] Test at least OWNER/ADMIN and MEMBER users.
- [ ] Test sidebar UI.
- [ ] Test settings UI.
- [ ] Test JSON mileage creation.
- [ ] Test receipt mileage creation.
- [ ] Test native/mobile unit expense conversion via `EXPENSE_CREATED`.
- [ ] Test update/delete/restore webhook behavior.
- [ ] Confirm listing icon, screenshots, privacy policy, terms, and support contact are ready in English.

## Known Non-Goals For Pre-Publish

- No production Marketplace submission is performed by this checklist.
- No live Clockify workspace mutation is performed unless the operator explicitly allows sacrificial-workspace testing.
