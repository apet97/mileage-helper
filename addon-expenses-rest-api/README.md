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
- The UI date presets and default create date use the Clockify claim timezone through `/assets/mileage/settings-date.js`, falling back to the browser-local date only when the claim timezone is absent or invalid.
- Generated Clockify notes are deterministic and exact, for example `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.`
- Add-on previews and mileage tables show full `calculatedAmount` decimals first, with the rounded Clockify expense amount shown as secondary context.
- Mine and Team lists/CSVs exclude audit rows marked `DELETED`; the admin Conversions view/export keeps them as audit history.
- Expense webhooks tolerate both full expense payloads with `id` and reference payloads containing `expenseId`.

## Hosted Test Deployment

- URL: `https://89-168-93-85.sslip.io`
- Manifest: `https://89-168-93-85.sslip.io/manifest`
- Runtime: OCI VM with `mileage-for-clockify.service` behind Caddy.
- Railway deployment ID: historical only unless Railway is explicitly restored; use `railway deployment list` only for Railway runs.
- Dated deployment evidence belongs in the pre-publish checklist after each deploy; old deployment IDs are historical evidence, not current truth.
- Hosted rechecks, dated 2026-05-27: a pre-deploy check showed health and manifest passing while `/assets/mileage/settings-date.js` returned `404`, proving production was still serving an older deployment; after deploying latest `main`, health, manifest, both settings JS assets, icon, and unauthenticated iframe probes passed. New deploys that include static asset changes must probe both `/assets/mileage/settings-date.js` and `/assets/mileage/settings.js`. Clockify reinstall, settings load, mileage create/use, and delete-list behavior were last user-tested on 2026-05-26.
- Expanded live Clockify API smoke on 2026-05-27 used local secrets only and proved workspace/user/category reads plus sacrificial Mileage receipt expense create, fetch, full update, delete, and post-delete non-success (`400`). Follow-up receipt probes created sacrificial PNG and valid generated PDF receipts, observed `fileId`, downloaded nonzero binary content through the expense file endpoint, then deleted both expenses. A malformed hand-written PDF fixture returned zero bytes and should not be used as product evidence.
- Local hardening review on 2026-05-27 covered multipart receipt/header sanitization, timezone claim alias parity, focused client/security tests, date-helper static asset checks, and `gitleaks` proof.
- Live Clockify smoke is optional and requires local secrets. Never commit or echo API keys/tokens. If not run, final output must say it was skipped.

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
./scripts/verify-publish.sh
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
