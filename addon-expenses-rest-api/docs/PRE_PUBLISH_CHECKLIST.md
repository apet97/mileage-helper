# Mileage for Clockify Pre-Publish Checklist

## Current Evidence

Last local/live stabilization pass: 2026-05-27.

- `git diff --check`: passed after the multipart and timezone hardening pass.
- `node --check addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js`: passed.
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
- Hosted Railway deployment `789acdd8-38ef-42f9-9a41-45dded009743` reached
  `SUCCESS`; hosted `/actuator/health`, `/manifest`, and settings asset probes
  passed.
- Live Clockify uninstall/install/settings/create/delete smoke passed after the
  deleted-expense webhook fix. Production audit rows for stale test deletes were
  marked `DELETED` with `deleted_at`, while the current live expense remained
  `CONVERTED`.

## Required Local Gates

- [ ] `git status --short --branch` reviewed.
- [ ] `mvn -pl addon-expenses-rest-api -am clean test` passes.
- [ ] `docker compose -f addon-expenses-rest-api/docker-compose.yml build` passes.
- [ ] Runtime `/manifest` probe passes.
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
