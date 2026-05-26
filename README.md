# Mileage for Clockify

Mileage for Clockify is a Clockify Marketplace add-on for mileage reimbursements. It creates mileage as real Clockify expenses and converts eligible native/mobile `Mileage` category expenses through signed Clockify webhooks.

Current hosted test add-on:

- App URL: `https://mileage-for-clockify-production.up.railway.app`
- Manifest: `https://mileage-for-clockify-production.up.railway.app/manifest`
- Railway deployment ID: use `railway deployment list` for the current Railway deployment ID.
- Dated deployment evidence belongs in the pre-publish checklist after each deploy; old deployment IDs are historical evidence, not current truth.
- Dated local hardening review snapshot: 2026-05-27, covering shared multipart receipt/file upload handling, Clockify timezone claim aliases, and secret-scan proof.
- Live Clockify smoke is optional and requires local secrets. Never commit or echo API keys/tokens. If not run, final output must say it was skipped.

## Repository Layout

- `addon-expenses-rest-api/` - product module with add-on source, UI, manifest, docs, Dockerfile, and compose file.
- `addon-core/` - shared add-on auth, lifecycle, manifest, webhook, and security-header plumbing.
- `addon-db/` - Flyway/JPA persistence for installation context and encrypted tokens.
- `clockify-rest-client/` - typed Clockify REST client and live-evidence-backed route docs.
- `addon-testkit/` - test builders and fixtures.
- `repo/` - vendored Maven artifacts for the Clockify add-on SDK.

The ignored local clone `addon-expenses-rest-api/addon-java-sdk/` is read-only reference material. Do not edit or commit it.

## What It Does

- Manifest: `GET /manifest`, schema `1.5`, key `mileage-for-clockify`, minimum plan `PRO`.
- UI: `GET /iframe/mileage`, `GET /iframe/settings`.
- Manual mileage expenses are billable by default unless the request explicitly sends `billable=false`.
- The main form hides rate override unless workspace settings allow it; users always see the configured workspace rate context first.
- Settings use one `Mileage` unit category, fixed unit `mile`, and fixed `HALF_UP` Clockify-style rounding.
- Setup can use an existing Clockify `Mileage` UNIT/mile category and derive the local rate from Clockify cents pricing.
- Generated Clockify notes use the exact calculated amount, for example: `Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.`
- The add-on displays full calculated mileage decimals in previews, Mine, Team, and Conversions while Clockify receives the rounded expense amount.
- Mine and Team lists/CSVs hide deleted expenses. The admin Conversions view keeps deleted rows as audit history.
- Expense webhooks accept both full payloads with `id` and reference payloads with `expenseId`.
- Receipt uploads are sent through shared Clockify client multipart construction that rejects unsafe field names and sanitizes filename/content-type headers.
- Clockify timezone claim aliases are normalized server-side and kept aligned with the settings UI fallback logic.

## API Surface

- User APIs: `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`.
- Admin APIs: settings, Mileage category repair, diagnostics, category options, team mileage list/export, conversion list/detail/retry/export under `/api/mileage`.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.

## Build And Test

Run from the repository root:

```bash
./scripts/verify-publish.sh
mvn -pl addon-expenses-rest-api -am test
mvn -pl addon-expenses-rest-api -am clean test
```

If local Testcontainers cannot discover Docker, use Colima explicitly:

```bash
DOCKER_HOST=unix:///Users/15x/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test \
  -Ddocker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy \
  -Ddocker.host=unix:///Users/15x/.colima/default/docker.sock \
  -Dapi.version=1.44
```

## Docker

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml build
docker compose -f addon-expenses-rest-api/docker-compose.yml up -d
curl -fsS http://localhost:8080/manifest
docker compose -f addon-expenses-rest-api/docker-compose.yml down
```

If local port `5432` is occupied, keep the compose database internal:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') up -d
```

## Documentation Map

- [AGENTS.md](AGENTS.md) - binding Codex/agent rules.
- [CLAUDE.md](CLAUDE.md) - Claude Code project guide.
- [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md) - product module guide.
- [addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md](addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md) - current local, live-dev, and manual gates before Marketplace submission.
- [addon-expenses-rest-api/endpoints.md](addon-expenses-rest-api/endpoints.md), [models.md](addon-expenses-rest-api/models.md), [webhooks.md](addon-expenses-rest-api/webhooks.md), [edge-cases.md](addon-expenses-rest-api/edge-cases.md), [reports.md](addon-expenses-rest-api/reports.md) - active product docs.
- `addon-expenses-rest-api/MARKETPLACE_OCS/` - copied Marketplace documentation reference.

## Configuration

Runtime configuration uses `SPRING_DATASOURCE_*`, `ADDON_BASE_URL`, `ADDON_KEY`, `ADDON_NAME`, `ADDON_DESCRIPTION`, and `ADDON_CRYPTO_*` variables. Default CORS includes Clockify origins plus the `ADDON_BASE_URL` origin, which is what local ngrok testing needs. See [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md) for the full list.
