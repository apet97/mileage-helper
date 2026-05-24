# Mileage for Clockify

Standalone repository for the Mileage for Clockify add-on. It creates precise mileage reimbursements as real Clockify flat expenses and converts eligible native/mobile unit mileage expenses through expense webhooks.

## Repository Layout

- `addon-expenses-rest-api/` - product module with add-on source, UI, manifest, docs, Dockerfile, and compose file.
- `addon-core/` - shared add-on auth, lifecycle, manifest, webhook, and security-header plumbing.
- `addon-db/` - Flyway/JPA persistence for installation context and encrypted tokens.
- `clockify-rest-client/` - typed Clockify REST client and live-evidence-backed route docs.
- `addon-testkit/` - test builders and fixtures.
- `repo/` - vendored Maven artifacts for the Clockify add-on SDK.

The ignored local clone `addon-expenses-rest-api/addon-java-sdk/` is read-only reference material and must not be committed.

## Product Surface

- Manifest: `GET /manifest`, schema `1.5`, key `mileage-for-clockify`, minimum plan `PRO`.
- UI: `GET /iframe/mileage`, `GET /iframe/settings`.
- User APIs: `POST /api/mileage/preview`, `POST /api/mileage/expenses`.
- Admin APIs: settings, diagnostics, category options, conversion list/detail/retry under `/api/mileage`.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.

## Build And Test

Run from the repository root:

```bash
mvn -pl addon-expenses-rest-api -am test
mvn -pl addon-expenses-rest-api -am clean test
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
- [addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md](addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md) - local and manual gates before Marketplace submission.
- [addon-expenses-rest-api/endpoints.md](addon-expenses-rest-api/endpoints.md), [models.md](addon-expenses-rest-api/models.md), [webhooks.md](addon-expenses-rest-api/webhooks.md), [edge-cases.md](addon-expenses-rest-api/edge-cases.md), [reports.md](addon-expenses-rest-api/reports.md) - active product docs.
- `addon-expenses-rest-api/mileage-addon-handoff/` - historical design and implementation handoff, useful for audits but no longer the primary instruction set.
- `addon-expenses-rest-api/MARKETPLACE_OCS/` - copied Marketplace documentation reference.

## Configuration

Runtime configuration uses `SPRING_DATASOURCE_*`, `ADDON_BASE_URL`, `ADDON_KEY`, `ADDON_NAME`, `ADDON_DESCRIPTION`, and `ADDON_CRYPTO_*` variables. See [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md) for the full list.
