# Mileage for Clockify

Mileage for Clockify creates precise mileage reimbursements as real Clockify expenses and converts native/mobile Mileage-category expenses through signed expense webhooks.

## Current Status

This module is the implemented product module inside the standalone repository. The old generic expense API boilerplate has been replaced by Mileage-specific manifest, settings, calculation, conversion, webhook, iframe, and audit code. Historical pre-Mileage migrations remain only for Flyway validation; `V12__drop_legacy_temp_addon_expenses_tables.sql` removes leftover generic tables so the product-owned tables are `mileage_workspace_settings` and `mileage_conversion`.

## Scope

- Settings store only mileage configuration per workspace.
- Settings use one `Mileage` unit category, fixed unit `mile`, fixed `HALF_UP` rounding, and rate override disabled by default.
- Setup can adopt an existing Clockify `Mileage` UNIT/mile category and derive the local rate from Clockify `unitPrice` cents when no rate is saved yet.
- Audit rows provide idempotency, delete/restore state, dry-run records, and conversion failures.
- Clockify remains the source of truth for expenses, receipts, approvals, reports, budgets, and invoices.
- Mileage, rate, and money values are handled with `BigDecimal`.
- Manual mileage creation defaults `billable` to true when omitted and derives the user from verified token claims.
- Receipt uploads use the shared Clockify client multipart helper so Expenses and Files upload paths share field-name validation, filename sanitization, and content-type fallback behavior.
- Rate override on the main page is available only when workspace settings allow it; otherwise the configured workspace rate is used and shown as read-only context.
- Regular users see only `Mine`; admins also see `Team`, `Settings`, `Conversions`, and `Diagnostics`.
- Mileage lists and CSV exports default to this US week, Sunday through Saturday, with presets for custom ranges, this/last month, this/last week, and this/last year.
- Generated Clockify notes are deterministic and exact, for example `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.`
- Add-on previews and mileage tables show full `calculatedAmount` decimals first, with the rounded Clockify expense amount shown as secondary context.
- Mine and Team lists/CSVs exclude audit rows marked `DELETED`; the admin Conversions view/export keeps them as audit history.
- Expense webhooks tolerate both full expense payloads with `id` and reference payloads containing `expenseId`.

## Hosted Test Deployment

- URL: `https://mileage-for-clockify-production.up.railway.app`
- Manifest: `https://mileage-for-clockify-production.up.railway.app/manifest`
- Recorded deployment proof for the 2026-05-27 hardening pass: `789acdd8-38ef-42f9-9a41-45dded009743`
- Verified on 2026-05-27 with health, manifest, and settings asset probes. Clockify reinstall, settings load, mileage create/use, and delete-list behavior were last user-tested on 2026-05-26.
- Dev workspace receipt smoke on 2026-05-27 created, fetched, and deleted a sacrificial Mileage PDF receipt expense through the local `clockify-rest-client`; post-delete GET returned non-success.
- Local hardening review on 2026-05-27 covered multipart receipt/header sanitization, timezone claim alias parity, focused client/security tests, and `gitleaks` proof.

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
