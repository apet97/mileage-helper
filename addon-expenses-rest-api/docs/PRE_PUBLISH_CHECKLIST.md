# Mileage for Clockify Pre-Publish Checklist

## Current Local Evidence

Last local hardening pass: 2026-05-26.

- `mvn -pl addon-expenses-rest-api -am clean test`: passed with full reactor `BUILD SUCCESS`:
  `addon-core` 71 tests, `clockify-rest-client` 101 tests, `addon-db` 37 tests,
  and `addon-expenses-rest-api` 162 tests.
- `docker compose -f addon-expenses-rest-api/docker-compose.yml build`: passed.
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
- Workspace-isolation review: mileage conversion detail and retry paths now use
  workspace-scoped repository lookups; remaining ID-only repository lookups are
  workspace primary-key reads or internal webhook event status updates.
- `gitleaks detect --source . --no-git --redact --verbose`: no leaks found.
- Flyway history keeps V5/V10 for existing database validation; V12 drops the
  leftover generic tables.

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
