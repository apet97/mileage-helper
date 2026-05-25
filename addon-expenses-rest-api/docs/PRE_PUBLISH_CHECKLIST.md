# Mileage for Clockify Pre-Publish Checklist

## Current Local Evidence

Last local hardening pass: 2026-05-25.

- `mvn -pl addon-expenses-rest-api -am test`: passed with 159 product-module tests and full reactor `BUILD SUCCESS`.
- `docker compose -f addon-expenses-rest-api/docker-compose.yml build`: passed.
- Runtime `/manifest` probe: returned key `mileage-for-clockify` and schema `1.5`.
- Runtime `/iframe/mileage` unauthenticated probe: returned `401` with CSP, `nosniff`, `no-referrer`, permissions policy, and `Cache-Control: no-store`.
- Static stale/dead-code scan: no active-source hits for removed expense-boilerplate names, obsolete live shell probes, or legacy temp-addon schema names outside immutable Flyway history.
- Static forbidden-number scan: no `double`, `Double`, `float`, or `Float` usage in Mileage source/tests.
- `gitleaks detect --source . --no-git --redact --verbose`: no leaks found.
- Flyway history keeps V5/V10 for existing database validation; V12 drops the leftover generic tables.
- Git history was rewritten before publish-prep handoff to remove the obsolete live shell probe and its hardcoded API-key fallback.
- Marketplace docs compliance pass reviewed lifecycle, UI component, webhook, authentication, environment/region, development checklist, and publishing/security guidance. User mileage creation now derives target `userId` from verified Clockify token claims instead of request parameters, and the create DTO/UI payload do not carry `userId`.

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
