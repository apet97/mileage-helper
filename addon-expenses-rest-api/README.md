# Mileage for Clockify

Mileage for Clockify creates precise mileage reimbursements as real Clockify flat expenses and converts native/mobile unit mileage expenses through signed expense webhooks.

## Current Status

This module is the implemented product module inside the standalone repository. The old generic expense API boilerplate has been replaced by Mileage-specific manifest, settings, calculation, conversion, webhook, iframe, and audit code. Historical pre-Mileage migrations remain only for Flyway validation; `V12__drop_legacy_temp_addon_expenses_tables.sql` removes leftover generic tables so the product-owned tables are `mileage_workspace_settings` and `mileage_conversion`.

## Scope

- Settings store only mileage configuration per workspace.
- Audit rows provide idempotency, delete/restore state, dry-run records, and conversion failures.
- Clockify remains the source of truth for expenses, receipts, approvals, reports, budgets, and invoices.
- Mileage, rate, and money values are handled with `BigDecimal`.
- Manual mileage creation defaults `billable` to true when omitted and derives the user from verified token claims.
- Rate override on the main page is available only when workspace settings allow it; otherwise the configured workspace rate is used and shown as read-only context.
- Regular users see only `Mine`; admins also see `Team`, `Settings`, `Conversions`, and `Diagnostics`.
- Add-on previews and mileage tables show full `calculatedAmount` decimals first, with the rounded Clockify expense amount shown as secondary context.
- Native/mobile created and restored expense webhooks tolerate both full expense payloads and reference payloads containing `expenseId`.

## Non-goals

- This add-on does not replace Clockify reports.
- This add-on does not expose a generic expense API explorer.
- This add-on does not store installation tokens in frontend code.

## Environment

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `ADDON_BASE_URL`
- `ADDON_CRYPTO_ACTIVE_KEY_ID`
- `ADDON_CRYPTO_KEY_K1`
- `ADDON_ENABLE_HSTS` defaults to `true`
- `PORT` defaults to `8080`
- `ADDON_KEY` defaults to `mileage-for-clockify`
- `ADDON_NAME` defaults to `Mileage for Clockify`
- `ADDON_DESCRIPTION` defaults to the Mileage product description

## Local Run

From the repository root:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml up --build
```

The app serves `/manifest`, `/iframe/mileage`, `/iframe/settings`, `/healthz`, and `/actuator/health`.

For local tunnel testing, set `ADDON_BASE_URL` to the current ngrok origin before starting the app. Default CORS includes Clockify origins and the `ADDON_BASE_URL` origin so iframe API calls can post back to the same tunnel.

If local Postgres already uses `5432`, keep the compose database internal:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') up -d
```

## Tests

```bash
mvn -pl addon-expenses-rest-api -am test
mvn -pl addon-expenses-rest-api -am clean test
```

## Active Docs

- `endpoints.md` - route list, including the create-page context endpoint.
- `models.md` - DTO/entity map.
- `webhooks.md` - expense webhook payload shapes, conversion behavior, and loop prevention.
- `edge-cases.md` - skip/failure behavior and default create behavior.
- `reports.md` - relationship to native Clockify reports.
- `docs/PRE_PUBLISH_CHECKLIST.md` - current local and manual pre-publish gates.

`mileage-addon-handoff/` is retained as historical design and implementation context. Prefer current source, tests, and the active docs above for maintenance work.
