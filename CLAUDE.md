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
- Generated Clockify notes are clean and exact, e.g. `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.`
- `EXPENSE_CREATED` and `EXPENSE_RESTORED` handlers accept either full payloads with `id` or reference payloads with `expenseId`.
- Admin APIs: settings, Mileage category repair, diagnostics, category options, team mileage list/export, conversion list/detail/retry/export under `/api/mileage`.
- Add-on previews and mileage tables show full `calculatedAmount` decimals first; Clockify Expenses still receives the rounded `roundedAmount`.
- Mileage lists and CSV exports filter by actual `expenseDate`, defaulting to the current US week, Sunday through Saturday.
- Tables: `mileage_workspace_settings`, `mileage_conversion`.
- Historical pre-Mileage migrations V5/V10 are retained for Flyway validation only; V12 drops their leftover generic tables. New code/docs should not add `temp_addon_expenses*`, `clockify-expenses-api`, `Clockify Expenses API`, or `com.cake.clockify.addon.expenses` references.

## Commands

```bash
# Fast focused add-on reactor
mvn -pl addon-expenses-rest-api -am test

# Clean verification
mvn -pl addon-expenses-rest-api -am clean test

# Docker image
docker compose -f addon-expenses-rest-api/docker-compose.yml build

# Runtime manifest probe, with DB port kept internal if local 5432 is busy
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') up -d
curl -fsS http://localhost:8080/manifest
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') down
```

## Environment

Runtime configuration uses these names:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `ADDON_BASE_URL`
- `ADDON_KEY`
- `ADDON_NAME`
- `ADDON_DESCRIPTION`
- `ADDON_CRYPTO_ACTIVE_KEY_ID`
- `ADDON_CRYPTO_KEY_K1`
- `ADDON_ENABLE_HSTS`
- `PORT`

Live sacrificial Clockify checks may use shell environment variables such as `CLOCKIFY_API_BASE_URL`, `CLOCKIFY_API_KEY`, `CLOCKIFY_WORKSPACE_ID`, `CLOCKIFY_TEST_USER_ID`, and `CLOCKIFY_TEST_PROJECT_ID`. Never print secret values.

Default CORS allows Clockify origins and the origin from `ADDON_BASE_URL`, which keeps local ngrok iframe/API testing working without adding a broad wildcard.

## Hard Rules

- Do not edit `addon-expenses-rest-api/addon-java-sdk/`.
- Do not use `double`, `Double`, `float`, or `Float` for mileage, rate, or money domain values.
- Do not hardcode Clockify API URLs in add-on code.
- Do not expose installation tokens to frontend JavaScript or HTML.
- Do not log tokens, auth headers, receipt bytes, or raw upstream error bodies.
- Preserve workspace isolation in repository methods and service calls.
- Do not trust request-supplied `userId` for user-facing mileage creation; derive the target user from verified Clockify token claims. Do not add `userId` back to the create request DTO, multipart allowlist, iframe form, or frontend payload.
- Do not add a task selector, task options endpoint, `taskId` create field, or `TASK_READ` scope for user-facing mileage creation unless product requirements change and live scope evidence is captured first.
- Do not expose the rate override input on the main page unless `/api/mileage/create-context` reports `allowUserRateOverride=true`.
- Keep `addon-core` and `addon-db` changes narrow; ask before structural platform changes.
- Keep copied Marketplace docs under `addon-expenses-rest-api/MARKETPLACE_OCS/` as source reference material.
- Do not restore deleted live shell probes. Do not add new legacy temp-addon migrations; keep V5/V10 only as immutable Flyway history and use forward migrations for cleanup.

## Verification Expectations

Before claiming pre-publish readiness, complete `addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md` and paste the exact command outputs into the session summary.

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
