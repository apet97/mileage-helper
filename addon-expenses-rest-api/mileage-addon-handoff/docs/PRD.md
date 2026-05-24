# Product Requirements Document: Mileage for Clockify

## 1. Product name

**Mileage for Clockify**

Working alternatives:
- Mileage Precision for Clockify
- Mileage Reimbursement Helper
- Mileage Converter for Clockify

## 2. Product statement

Mileage for Clockify lets teams create accurate mileage reimbursements using precise rates, then writes those reimbursements into Clockify as normal flat expenses. Clockify remains the source of truth for expense reports, receipts, approvals, project budgets, and invoices.

## 3. Problem

Clockify supports expenses and unit-based categories such as mileage, but organizations may need reimbursement rates with more precision than the native unit price workflow comfortably supports. For example, a mileage reimbursement rate may require three decimal places. If a unit category cannot represent the rate precisely, the calculated total can be wrong.

Users also submit expenses from mobile. A Clockify add-on UI is a web component, so the add-on must support users who create mileage expenses through native Clockify mobile.

## 4. Goals

### G1 — Create real Clockify expenses

When a user submits mileage through the add-on, the add-on creates a real Clockify flat expense.

### G2 — Convert native/mobile mileage expenses

When a user submits a native unit-based mileage expense in Clockify, the add-on converts it into a flat mileage expense using the configured precise rate.

### G3 — Preserve native Clockify workflows

Expense reports, receipt handling, approval, invoicing, and project budget behavior remain native Clockify features.

### G4 — Maintain auditability

Every conversion must be traceable, idempotent, and visible in an admin log.

### G5 — Prevent webhook loops

The add-on must handle `EXPENSE_UPDATED` events caused by its own update without repeated conversion.

## 5. Non-goals

- Replacing Clockify expense reports.
- Replacing Clockify approvals.
- Replacing Clockify invoicing.
- Replacing Clockify receipts.
- Automatically fetching or deciding official government mileage rates.
- Building payroll or tax advice logic.
- Building accounting ledger reconciliation.

## 6. Users

### Workspace admin / owner

Configures:
- mileage rate
- input unit mileage category
- output flat mileage category
- rounding mode
- auto-conversion behavior
- note template

Views:
- conversion log
- skipped/failed conversions
- webhook status
- retry actions

### Regular user

Creates mileage expenses using:
- add-on Mileage form in Clockify web
- native Clockify Expenses on web/mobile using configured input category

### Manager / approver

Uses native Clockify approvals and reports. The add-on should not change their workflow.

## 7. User stories

### US1 — Admin configures mileage conversion

As an admin, I want to configure a mileage rate and category mapping so that mileage expenses are converted consistently.

### US2 — User creates mileage in add-on

As a user, I want to enter miles and upload a receipt so that a real Clockify expense is created with the correct flat amount.

### US3 — Mobile user creates mileage natively

As a mobile user, I want to create mileage in Clockify mobile so that the add-on converts it automatically without needing add-on UI on mobile.

### US4 — Admin reviews conversions

As an admin, I want to see recent conversions and failures so that I can audit and fix issues.

### US5 — Add-on avoids loops

As an admin, I want the add-on to ignore already converted expenses so that webhook updates do not cause repeated mutations.

## 8. MVP requirements

### Functional

1. Serve a valid Clockify manifest targeting schema 1.5.
2. Require minimum subscription plan `PRO`.
3. Request scopes:
   - `EXPENSE_READ`
   - `EXPENSE_WRITE`
   - `USER_READ`
   - `PROJECT_READ`
   - optionally `WORKSPACE_READ`
4. Register lifecycle hooks:
   - `INSTALLED`
   - `DELETED`
   - `SETTINGS_UPDATED`
   - `STATUS_CHANGED`
5. Register expense webhooks:
   - `EXPENSE_CREATED`
   - `EXPENSE_UPDATED`
   - `EXPENSE_DELETED`
   - `EXPENSE_RESTORED`
6. Provide add-on UI:
   - Mileage entry form for users.
   - Admin configuration/settings.
   - Conversion log.
7. Create real Clockify flat expenses from add-on mileage entries.
8. Attach receipt files to created expenses.
9. Convert configured native unit mileage expenses into flat expenses.
10. Preserve project/task/date/user/billable/receipt where supported by the API.
11. Append audit note marker.
12. Store conversion metadata.
13. Prevent webhook loops.
14. Skip locked/approved/invoiced/finalized records when detectable; record skip reason.
15. Use `BigDecimal` for all mileage/rate/money calculations.

## 9. Success metrics

- 100% of eligible add-on-created mileage entries create real Clockify flat expenses.
- 100% of eligible native mileage expenses are converted exactly once.
- No repeated webhook conversion loops.
- Receipt preserved in all conversion tests.
- Amount calculation matches expected cent rounding for all test cases.
- Admin can identify failed/skipped conversions in the UI.

## 10. Open questions for implementation

These must be verified during development:

1. Exact field names in full expense response for:
   - quantity
   - total
   - amount
   - category
   - note/notes
   - locked/approved/invoiced status
   - receipt/file metadata
2. Exact update payload required to convert a unit expense to flat.
3. Whether `EXPENSE_RESTORED` payload is full or reference-only.
4. Whether webhook payload includes actor/add-on user metadata.
5. Whether the official Java SDK version used by the project exposes `v1_5Builder()` or requires serving schema 1.5 JSON manually.
