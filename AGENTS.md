# Mileage for Clockify Agent Rules

This is the standalone repository for Mileage for Clockify. It contains the add-on plus the smallest local platform modules needed to build, test, and package it outside the original add-on factory workspace.

## Start Here

1. Run `git status --short --branch`.
2. Read this file, then `CLAUDE.md`, then [README.md](README.md).
3. For product behavior, use [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md), [addon-expenses-rest-api/endpoints.md](addon-expenses-rest-api/endpoints.md), [addon-expenses-rest-api/webhooks.md](addon-expenses-rest-api/webhooks.md), and the implemented tests before relying on old handoff docs.
4. Treat `addon-expenses-rest-api/mileage-addon-handoff/` and `addon-expenses-rest-api/docs/IMPLEMENTATION_PLAN_MILEAGE_FOR_CLOCKIFY_DETAILED.md` as historical implementation records unless a task explicitly says to resume or audit them.

## Non-Negotiables

1. Do not guess Clockify API shapes. Prefer typed client tests, local live-evidence docs, and live sacrificial-workspace evidence only when explicitly permitted.
2. Never edit or rely on committing `addon-expenses-rest-api/addon-java-sdk/`; it is a read-only ignored local SDK clone.
3. Keep `addon-core`, `addon-db`, `clockify-rest-client`, and `addon-testkit` changes conservative. Stop and confirm before structural platform changes.
4. Use Java 21 `record` DTOs when adding new DTOs unless an existing local pattern clearly differs.
5. All mileage, rate, and money values must use `BigDecimal` or SQL `numeric`. Never use floating point for those domain values.
6. Never hardcode Clockify API hosts in add-on code. Use token or installation context through the platform/client services.
7. Never expose installation tokens to frontend code, logs, docs, screenshots, or test output.
8. Preserve workspace isolation in every repository query, service method, webhook path, and Clockify API call.
9. User-facing mileage creation must use the verified user ID from Clockify token claims, not a frontend or request-supplied `userId`. Do not add `userId` back to the create request DTO, multipart allowlist, iframe form, or frontend payload.
10. User-facing mileage creation follows Clockify's regular expense form shape and does not require or fetch tasks. Do not add a task selector, task options endpoint, `taskId` create-field, or `TASK_READ` manifest scope unless the product requirement changes and live scope evidence is captured first.
11. Main-page rate override is settings-gated. Keep `/api/mileage/create-context`, server-side rate override enforcement, and frontend visibility in sync.

## Module Map

- `addon-expenses-rest-api`: Mileage add-on application, UI, manifest, settings, webhooks, conversions, Dockerfile, compose file, and add-on docs.
- `addon-core`: Shared add-on auth, lifecycle routing, manifest controller, filters, security headers, and webhook dispatch.
- `addon-db`: JPA/Flyway persistence for installation context, encrypted tokens, settings, and webhook tokens.
- `clockify-rest-client`: Typed Clockify REST client and live-evidence-backed route behavior.
- `addon-testkit`: Test builders and fixtures shared by add-on/platform tests.
- `repo`: Vendored Maven artifacts for the Clockify add-on SDK.

## Current Product Facts

- Product name: `Mileage for Clockify`.
- Manifest strategy: manual schema 1.5 model in `MileageManifestV15`; do not switch to `ClockifyManifest.v1_5Builder()` unless you verify it exists locally.
- Manifest key: `mileage-for-clockify`.
- Main UI: `/iframe/mileage`; settings UI: `/iframe/settings`.
- Main user APIs: `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`.
- Main admin APIs: settings, diagnostics, categories, team mileage list/export, conversion list/detail/retry/export under `/api/mileage`.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- DB tables: `mileage_workspace_settings`, `mileage_conversion`.
- Mileage create requests intentionally omit `userId`; the backend injects the verified claims user into Clockify create commands and audit rows.
- Mileage create requests intentionally omit `taskId`; the UI lists projects and categories but does not call task APIs. Native expense conversion may still preserve an existing Clockify `taskId` from webhook snapshots.
- Manual mileage expenses default to billable when `billable` is omitted. An explicit `false` still stays non-billable.
- Main-page rate override is hidden and omitted unless workspace settings allow user overrides. Backend calculation still ignores submitted override rates when the setting is off.
- Add-on UI tables and previews display full `calculatedAmount` decimals as the primary amount. Clockify expense writes continue to use the rounded `roundedAmount`.
- Native expense `EXPENSE_CREATED` and `EXPENSE_RESTORED` handlers accept either `id` or `expenseId` payload shapes.
- Historical pre-Mileage migrations V5/V10 are retained for Flyway validation only; V12 drops their leftover generic tables. Do not add new `temp_addon_expenses*`, `clockify-expenses-api`, `Clockify Expenses API`, or `com.cake.clockify.addon.expenses` references.

## Commands

Run from the repository root.

```bash
mvn -pl addon-expenses-rest-api -am test
mvn -pl addon-expenses-rest-api -am clean test
docker compose -f addon-expenses-rest-api/docker-compose.yml build
```

Before Marketplace submission, also complete [addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md](addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md).

Use this stale/dead-code scan after documentation or migration cleanup:

```bash
rg -n "clockify-expenses-api|Clockify Expenses API|com\\.cake\\.clockify\\.addon\\.expenses|temp_addon_expenses|temp-addon-expenses|API_TEST_|CLOCKIFY_API_KEY:-|test-suite\\.sh" \
  addon-expenses-rest-api/src/main addon-expenses-rest-api/src/test/resources addon-expenses-rest-api/pom.xml \
  -g '!**/target/**' -g '!addon-expenses-rest-api/src/main/resources/db/migration/V5__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V10__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V12__*' \
  -g '!addon-expenses-rest-api/MARKETPLACE_OCS/**'
```

If local port `5432` is already in use, keep Postgres internal while running the Docker stack:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') up -d
curl -fsS http://localhost:8080/manifest
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') down
```

## Editing Guidance

- Use small, focused diffs.
- Use `apply_patch` for manual edits.
- Do not commit unless explicitly asked.
- Do not weaken tests to make verification pass.
- After functional changes, run the focused test first, then `mvn -pl addon-expenses-rest-api -am test`.
- After manifest, Docker, or runtime config changes, also run the Docker build and `/manifest` probe.
