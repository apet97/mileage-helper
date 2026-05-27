# Mileage for Clockify Agent Rules

This is the standalone repository for Mileage for Clockify. It contains the add-on plus the smallest local platform modules needed to build, test, and package it outside the original add-on factory workspace.

## Start Here

1. Run `git status --short --branch`.
2. Read this file, then `CLAUDE.md`, then [README.md](README.md).
3. For product behavior, use [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md), [addon-expenses-rest-api/endpoints.md](addon-expenses-rest-api/endpoints.md), [addon-expenses-rest-api/webhooks.md](addon-expenses-rest-api/webhooks.md), [clockify-rest-client/docs/endpoint-provenance.md](clockify-rest-client/docs/endpoint-provenance.md), and the implemented tests.

## Non-Negotiables

1. Do not guess Clockify API shapes. Prefer typed client tests, endpoint provenance docs, and live sacrificial-workspace evidence only when explicitly permitted.
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
12. Webhook handlers must acknowledge safely with HTTP 2xx after internal failure recording/logging. Do not let Clockify blindly retry failures that should be retried from the admin/internal path.
13. Native expense conversion must aggressively prevent loops: skip mileage audit markers, output-category expenses, and already-converted expenses before writing back to Clockify.
14. The Clockify REST client has no default API hosts. Builders and tests must pass explicit backend URLs, add-ons must route from verified token claims or installation context, and reports URLs may only be omitted for clients that do not use reports APIs.
15. Receipt and file uploads must use the shared Clockify client multipart helper. Do not hand-build multipart `Content-Disposition` or `Content-Type` headers; field names must be constrained and filenames/content types sanitized.

## Module Map

- `addon-expenses-rest-api`: Mileage add-on application, UI, manifest, settings, webhooks, conversions, Dockerfile, compose file, and add-on docs.
- `addon-core`: Shared add-on auth, lifecycle routing, manifest controller, filters, security headers, and webhook dispatch.
- `addon-db`: JPA/Flyway persistence for installation context, encrypted tokens, settings, and webhook tokens.
- `clockify-rest-client`: Typed Clockify REST client and endpoint-provenance-backed route behavior.
- `addon-testkit`: Test builders and fixtures shared by add-on/platform tests.
- `repo`: Vendored Maven artifacts for the Clockify add-on SDK.

## Current Product Facts

- Product name: `Mileage for Clockify`.
- Manifest strategy: manual schema 1.5 model in `MileageManifestV15`; do not switch to `ClockifyManifest.v1_5Builder()` unless you verify it exists locally.
- Manifest key: `mileage-for-clockify`.
- Main UI: `/iframe/mileage`; settings UI: `/iframe/settings`.
- Main user APIs: `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`.
- Main admin APIs: settings, Mileage category repair, diagnostics, categories, team mileage list/export, conversion list/detail/retry/export under `/api/mileage`.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- DB tables: `mileage_workspace_settings`, `mileage_conversion`.
- Mileage create requests intentionally omit `userId`; the backend injects the verified claims user into Clockify create commands and audit rows.
- Mileage create requests intentionally omit `taskId`; the UI lists projects and categories but does not call task APIs. Native expense conversion may still preserve an existing Clockify `taskId` from webhook snapshots.
- Manual mileage expenses default to billable when `billable` is omitted. An explicit `false` still stays non-billable.
- Main-page rate override is hidden and omitted unless workspace settings allow user overrides. Backend calculation still ignores submitted override rates when the setting is off.
- Mileage settings use one `Mileage` unit category with fixed unit `mile` and fixed `HALF_UP` rounding; existing input/output category settings normalize to that single category.
- Setup can adopt an existing Clockify `Mileage` UNIT/mile category and derive the rate from Clockify `unitPrice` cents when no local rate is saved yet. Do not force a new category when the default category is already usable.
- Generated Clockify notes are clean and exact, e.g. `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.`
- Add-on UI tables and previews display full `calculatedAmount` decimals as the primary amount. Clockify expense writes continue to use the rounded `roundedAmount`.
- Mileage lists and CSV exports filter by `expenseDate`, defaulting to the current US week, Sunday through Saturday.
- User-facing `Mine` and admin `Team` lists/CSVs exclude `DELETED` audit rows. Admin `Conversions` keeps deleted rows visible as audit history.
- Expense webhook handlers that need an expense ID accept either `id` or `expenseId` payload shapes. This includes updated/deleted webhooks, which have arrived as full payloads in live Clockify testing.
- Webhook dispatch records/dedupes events, marks processing outcomes internally, and still returns HTTP 2xx after handler or audit-status failures.
- Native conversion eligibility skips disabled/incomplete settings, workspace mismatches, output categories, mileage note markers, existing successful conversions, non-input categories, missing/invalid quantity, and locked/finalized expenses.
- `ClockifyClientFactory` builds per-workspace clients from installed backend/reports URLs. Missing backend URL is fatal; missing reports URL is allowed until a reports request is attempted.
- Clockify token timezone normalization accepts `userTimeZone`, `userTimezone`, `timeZone`, `timezone`, and `tz`; keep frontend timezone alias handling aligned with `ClaimsNormalizer`.
- The settings UI loads `/assets/mileage/settings-date.js` before `/assets/mileage/settings.js`. Keep date presets/default create dates in that helper so Clockify claim timezones stay aligned with backend default ranges.
- After any deploy that touches mileage static assets, probe both `/assets/mileage/settings-date.js` and `/assets/mileage/settings.js`; a single settings asset probe is not enough.
- Receipt uploads in `clockify-rest-client` centralize multipart body construction so Expenses and Files clients share field-name validation, filename sanitization, and content-type fallback behavior.
- The optional `clockify-rest-client` Spring MVC facade and WebClient transport were removed as dead/bloated surfaces. Do not reintroduce global proxy controllers around the typed client.
- Historical pre-Mileage migrations V5/V10 are retained for Flyway validation only; V12 drops their leftover generic tables. Do not add new `temp_addon_expenses*`, `clockify-expenses-api`, `Clockify Expenses API`, or `com.cake.clockify.addon.expenses` references.

## Hosted Verification Snapshot

- Current hosted add-on URL: `https://mileage-for-clockify-production.up.railway.app`.
- Current hosted manifest URL: `https://mileage-for-clockify-production.up.railway.app/manifest`.
- Use `railway deployment list` for the current Railway deployment ID. Do not treat old deployment IDs in notes, chats, or previous evidence as current truth.
- Dated deployment evidence belongs in the pre-publish checklist after each deploy.
- Pre-deploy hosted recheck, dated 2026-05-27: `/actuator/health` and `/manifest` passed, but `/assets/mileage/settings-date.js` returned `404`, proving production was still serving an older deployment.
- Post-deploy hosted recheck, dated 2026-05-27: `/actuator/health`, `/manifest`, `/assets/mileage/settings-date.js`, `/assets/mileage/settings.js`, `/assets/mileage/icon.png`, and unauthenticated `/iframe/mileage` probes passed. Future deploys that touch static assets must rerun both settings JS asset probes.
- Historical live Clockify smoke, dated 2026-05-27: uninstall/install/settings/create/delete passed after the deleted-expense webhook fix. Treat this as historical unless rerun.
- Expanded live Clockify API smoke on 2026-05-27 used local environment secrets only and proved workspace/user/category read probes plus sacrificial Mileage receipt expense create, fetch, full update, delete, and post-delete non-success (`400`). A receipt `fileId` was observed, but direct receipt download returned `200` with zero bytes, so binary receipt content download is not proven by this pass. Never persist dev API keys in docs, logs, or commits.
- Local hardening review on 2026-05-27 covered multipart receipt/header sanitization, shared file-upload behavior, server/frontend timezone alias parity, date-helper static asset verification, and secret-scan proof.
- Live Clockify smoke is optional and requires local secrets. Never commit or echo API keys/tokens. If not run, final output must say it was skipped.

## Commands

Run from the repository root.

```bash
./scripts/verify-publish.sh
mvn -pl addon-expenses-rest-api -am test
mvn -pl addon-expenses-rest-api -am clean test
docker compose -f addon-expenses-rest-api/docker-compose.yml build
```

If Testcontainers cannot find Docker on this Mac, force Maven onto Colima:

```bash
DOCKER_HOST=unix:///Users/15x/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test \
  -Ddocker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy \
  -Ddocker.host=unix:///Users/15x/.colima/default/docker.sock \
  -Dapi.version=1.44
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
