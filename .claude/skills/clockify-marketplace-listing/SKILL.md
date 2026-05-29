---
name: clockify-marketplace-listing
description: Prepare the non-code Marketplace submission package for Mileage for Clockify (listing copy, scope justification, screenshots checklist, data-privacy disclosure). Use when the user asks about the Marketplace listing, submission form, descriptions, or review readiness. Excludes website and privacy policy (handled separately).
---

# Clockify Marketplace Listing Prep

Produce review-ready listing material in English.
User handles website + privacy policy separately as a CAKE.com employee — do NOT draft those.

## Deliverables

1. **Short description** (1–2 sentences, under 160 chars) matching actual behavior
   from addon README and endpoints.md.

2. **Long description** (full feature set, onboarding steps, limitations).
   Must match implemented and tested features only — no aspirational claims.

3. **Scope justification table** — for each scope in the manifest, the exact
   feature that requires it:
   - `EXPENSE_READ` — read existing mileage expenses; fetch expense detail for webhook conversion
   - `EXPENSE_WRITE` — create mileage expenses; update/delete expenses via admin
   - `USER_READ` — resolve user display names in team lists and CSV exports
   - `PROJECT_READ` — resolve project names in mileage lists and CSV exports
   - `WORKSPACE_READ` — read workspace settings on install and category setup
   Note: `TASK_READ` is explicitly NOT requested — justify the absence.

4. **Data handling disclosure** — what's stored:
   - `mileage_workspace_settings`: workspace rate, rounding mode, category IDs, billable flag
   - `mileage_conversion`: per-expense conversion audit row (expenseId, userId, outcome)
   - Installation token: encrypted at rest, server-side only, never logged or exposed to frontend
   - What is NOT stored: receipt files, user PII beyond Clockify userId, raw webhook bodies
   - Uninstall: on lifecycle DELETE event, workspace settings and installation token are removed

5. **Screenshot / gallery shot list** (up to 5 items):
   - Mileage entry form (iframe main page)
   - Admin settings page (rate, categories, billable default)
   - A created mileage expense as it appears in Clockify Expenses
   - Team mileage list (admin view with user names)
   - CSV export preview

6. **Reviewer notes**:
   - Multi-workspace test path: install in workspace A and workspace B independently
   - Role coverage: test as OWNER, ADMIN, and MEMBER
   - Native conversion trigger: create an expense in the configured input category,
     observe EXPENSE_CREATED webhook, confirm conversion to mileage expense in output category
   - Admin retry path: if a conversion shows FAILED, use admin UI to retry

7. **Grammar / spelling pass** of all copy (review guideline: zero typos).

## Rules
- Every claim must map to a real, tested, deployed feature.
- Do not invent features that aren't in the codebase.
- English only.
- No prohibited content.
- Do not reference pricing tiers or SLAs you can't control.
