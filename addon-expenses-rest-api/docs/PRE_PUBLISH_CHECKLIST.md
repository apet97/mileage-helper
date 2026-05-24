# Mileage for Clockify Pre-Publish Checklist

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
- [ ] Installation token is not exposed to frontend HTML, JavaScript, logs, docs, screenshots, or test output.
- [ ] Uninstall removes stored installation and webhook secrets.
- [ ] Diagnostics show installation, settings, and native conversion readiness.
- [ ] UI creates mileage without raw user ID entry.

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
