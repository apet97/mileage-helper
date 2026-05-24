# Claude Code Notes: Historical Handoff Packet

This directory is a historical implementation handoff. The add-on has already been implemented in the standalone repo root. For active project guidance, read:

1. `../../../AGENTS.md`
2. `../../../CLAUDE.md`
3. `../../README.md`
4. `../../endpoints.md`
5. `../../webhooks.md`

Do not restart the original boilerplate migration from this folder unless a task explicitly asks for a reimplementation or an audit against the original plan.

## Current Product Facts

- Product: `Mileage for Clockify`.
- Manifest: manual schema `1.5` via `MileageManifestV15`.
- Main module: `addon-expenses-rest-api`.
- Main package: `com.cake.clockify.addon.mileage`.
- UI routes: `/iframe/mileage`, `/iframe/settings`.
- User APIs: `POST /api/mileage/preview`, `POST /api/mileage/expenses`.
- Admin APIs: settings, diagnostics, categories, conversion list/detail/retry under `/api/mileage`.
- Webhooks: `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`.
- DB tables: `mileage_workspace_settings`, `mileage_conversion`.

## Still Useful In This Packet

- `docs/PRD.md`: product intent.
- `docs/TECHNICAL_SPEC.md`: expected behavior and constraints.
- `docs/ACCEPTANCE_CRITERIA.md`: release checklist, not a live status report.
- `docs/TEST_PLAN.md`: coverage map.
- `docs/MIGRATION_FROM_EXPENSE_BOILERPLATE.md`: historical migration record.
- `agent/CODEX_TASK_PROMPT.md`: now a maintenance prompt template.
- `agent/IMPLEMENTATION_CHECKLIST.md`: current implementation evidence checklist.

## Hard Rules That Still Apply

- Use `BigDecimal` for mileage, rate, and money.
- Do not use floating point domain values.
- Do not hardcode Clockify API hosts.
- Do not expose installation tokens to frontend code.
- Preserve workspace isolation in DB queries and service methods.
- Do not edit the ignored `addon-java-sdk/` clone.
