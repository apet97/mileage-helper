# Mileage for Clockify — Coding Agent Handoff Pack

This pack is historical design and implementation context. Mileage for Clockify has already been implemented in this standalone repository; use the repo root `AGENTS.md`, `CLAUDE.md`, and `addon-expenses-rest-api/README.md` for active maintenance instructions.

Do not restart from the original generated expense boilerplate unless a task explicitly asks for a reimplementation or an audit of the old plan.

## Product summary

**Mileage for Clockify** is a precision mileage input and conversion layer for Clockify Expenses.

It does **not** replace Clockify expense reports, approvals, receipts, budgets, or invoicing. It creates or converts entries into real Clockify flat expenses so all native Clockify workflows remain the source of truth.

## Core problem

Clockify's native unit-based expense category flow can be inconvenient when the reimbursement rate requires more precision than the native category unit price UX supports, such as a 3-decimal mileage rate. The add-on lets users enter miles and a precise rate, calculates the final amount with `BigDecimal`, and stores the result as a normal flat Clockify expense.

## Two supported flows

1. **Add-on-first flow**
   - User opens the Mileage add-on.
   - User enters date, project/task, miles, rate, billable flag, note, and optional receipt.
   - Add-on creates a real Clockify flat expense.
   - Receipt is uploaded to the real Clockify expense.
   - Clockify remains the reporting and approval system.

2. **Native/mobile bridge**
   - User creates a unit-based mileage expense in Clockify mobile or web.
   - Clockify sends `EXPENSE_CREATED`.
   - Add-on fetches the full expense, checks eligibility, calculates the precise amount, and updates the same expense into the configured flat mileage category.
   - The update triggers `EXPENSE_UPDATED`; the add-on must ignore its own converted expense.

## Files in this pack

- `docs/PRD.md` - product requirements document.
- `docs/TECHNICAL_SPEC.md` - implementation-level technical specification.
- `docs/ARCHITECTURE.md` - architecture, flow, and service boundaries.
- `docs/DATA_MODEL.md` - database schema and entity notes.
- `docs/API_CONTRACTS.md` - internal API routes and request/response contracts.
- `docs/IMPLEMENTATION_PLAN.md` - historical staged implementation plan.
- `docs/TECH_STACK.md` - current stack and dependencies.
- `docs/ACCEPTANCE_CRITERIA.md` - release checklist and manual validation criteria.
- `docs/TEST_PLAN.md` - unit, integration, webhook, and manual test plan.
- `docs/SECURITY_PRIVACY.md` - security, token, webhook, and privacy requirements.
- `docs/MIGRATION_FROM_EXPENSE_BOILERPLATE.md` - completed migration record.
- `manifest/MANIFEST_V1_5_DRAFT.json` - original draft manifest; current manifest is served by `MileageManifestV15`.
- `agent/CLAUDE.md` - handoff-local note pointing to active root guidance.
- `agent/CODEX_TASK_PROMPT.md` - maintenance prompt template.
- `agent/IMPLEMENTATION_CHECKLIST.md` - current implementation evidence checklist.
- `templates/mileage-note-template.txt` - note format used for converted expenses.
- `templates/sql-schema-draft.sql` - original SQL schema draft; current DDL is in `src/main/resources/db/migration/V11__create_mileage_tables.sql`.

## Non-goals

- Do not build replacement expense reports.
- Do not build replacement approval workflows.
- Do not build replacement invoicing.
- Do not store receipt files permanently unless required for transient upload.
- Do not silently mutate finalized records without audit and skip rules.

## Primary engineering constraint

All monetary and mileage calculations must use `BigDecimal`, never `double` / `Double`.

## Current implementation pointers

- Manifest: `src/main/java/com/cake/clockify/addon/mileage/config/MileageManifestV15.java`.
- API controllers: `src/main/java/com/cake/clockify/addon/mileage/api/`.
- Conversion service: `src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java`.
- Clockify gateway: `src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGateway.java`.
- Migrations: `src/main/resources/db/migration/`.
- Tests: `src/test/java/com/cake/clockify/addon/mileage/`.
