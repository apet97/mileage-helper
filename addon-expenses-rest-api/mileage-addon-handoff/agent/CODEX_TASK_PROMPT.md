# Codex Maintenance Prompt

You are working in the standalone **Mileage for Clockify** repository. The add-on is already implemented; do not restart from the original generated expense boilerplate.

## Read first

1. `../../../AGENTS.md`
2. `../../../CLAUDE.md`
3. `../../README.md`
4. `../../endpoints.md`
5. `../../webhooks.md`
6. Relevant source and tests for the requested change

Use this handoff folder as historical context only.

## Current product goal

Maintain a Clockify add-on that lets users enter mileage with a precise decimal rate and creates a real Clockify flat expense. It also converts eligible native/mobile Clockify unit-based mileage expenses into flat expenses using signed expense webhooks.

The add-on must not replace Clockify expense reports, receipts, approvals, budgets, or invoices.

## Current implementation map

- Manifest: `../../src/main/java/com/cake/clockify/addon/mileage/config/MileageManifestV15.java`
- User/admin APIs: `../../src/main/java/com/cake/clockify/addon/mileage/api/`
- Calculation: `../../src/main/java/com/cake/clockify/addon/mileage/calculation/`
- Clockify gateway: `../../src/main/java/com/cake/clockify/addon/mileage/clockify/`
- Conversion: `../../src/main/java/com/cake/clockify/addon/mileage/conversion/`
- Webhooks: `../../src/main/java/com/cake/clockify/addon/mileage/webhook/`
- UI: `../../src/main/java/com/cake/clockify/addon/mileage/iframe/` and `../../src/main/resources/static/assets/mileage/`
- DB: `../../src/main/resources/db/migration/V11__create_mileage_tables.sql`
- Tests: `../../src/test/java/com/cake/clockify/addon/mileage/`

## Critical constraints

- Do not use `double`/`Double`/`float`/`Float` for mileage, rates, or money.
- Do not expose installation tokens to frontend.
- Do not hardcode Clockify API URLs.
- Do not create replacement expense reports.
- Do not loop on `EXPENSE_UPDATED`.
- Do not mutate finalized/locked/approved/invoiced expenses.
- Preserve workspace isolation in every query and service method.
- Keep `addon-java-sdk/` read-only.

## Verification

Run from the repository root:

```bash
mvn -pl addon-expenses-rest-api -am test
```

For manifest, Docker, or runtime changes also run:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml build
```
