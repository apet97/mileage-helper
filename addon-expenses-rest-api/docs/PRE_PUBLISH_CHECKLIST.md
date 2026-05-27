# Mileage for Clockify Pre-Publish Checklist

## Required Gates Run Now

Run from the repository root before claiming publish readiness:

- [ ] `git status --short --branch` reviewed.
- [ ] `./scripts/verify-publish.sh` passes.
- [ ] If a new Railway deploy was made, `railway deployment list` was used for the current Railway deployment ID.
- [ ] If a new Railway deploy was made, hosted `/actuator/health`, `/manifest`, `/assets/mileage/settings-date.js`, `/assets/mileage/settings.js`, and icon probes passed and the dated evidence was added below.

`./scripts/verify-publish.sh` runs these local publish-safety checks:

- `mvn -pl clockify-rest-client -Dtest=ExpensesClientTest,FilesClientTest test`
- `mvn -pl addon-core -Dtest=ClaimsNormalizerTest test`
- `mvn -pl addon-expenses-rest-api -am -Dtest=MileageSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test`
- Colima-backed `mvn -pl addon-expenses-rest-api -am test`
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

Last local/live stabilization pass: 2026-05-27.

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
  post-delete fetch returned non-success (`400`). A receipt `fileId` was
  observed, but direct receipt download returned `200` with zero bytes, so
  binary receipt content download is not proven by this pass. No API key or
  token is stored in this repo.
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
- `mvn -pl clockify-rest-client -Dtest=ExpensesClientTest,FilesClientTest test`: passed with 15 tests covering receipt expense uploads and shared file upload multipart sanitization.
- `mvn -pl addon-core -Dtest=ClaimsNormalizerTest test`: passed with 11 tests covering Clockify timezone claim aliases.
- `mvn -pl addon-expenses-rest-api -Dtest=MileageSecurityTest test`: passed with 30 security tests.
- Colima-backed `mvn -pl addon-expenses-rest-api -am test`: passed with full reactor `BUILD SUCCESS`:
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
- Hosted Railway deployment for this dated pass reached `SUCCESS`; hosted
  `/actuator/health`, `/manifest`, and settings asset probes passed. Use
  `railway deployment list` for the current Railway deployment ID.
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
