# Unsupported or experimental endpoints

This module only exposes typed Java methods when endpoint behavior is documented or otherwise evidenced.

## Implemented first-slice typed clients

- users
- workspaces
- clients
- projects
- tasks
- tags
- time entries
- reports: summary, detailed, weekly
- files: image upload
- user groups
- holidays
- invoices
- expenses
- custom fields
- approvals
- webhooks
- scheduling
- time off (policies, requests, balances)
- shared reports

## Experimental / partial

- Some delete operations are documented but live behavior can reject freshly-created entities depending on Clockify state or plan. See `endpoint-provenance.md` notes.
- Holidays in-period query remains experimental until exact query semantics are verified.
- The Spring controller/raw facade covers the clean1 OpenAPI surface, but that is not the same as fully typed Java domain support.

## Remaining deferred typed domains

- Remaining workspace/project admin methods: rates, archive, memberships, roles.
- Broader files/download APIs beyond image upload and expense receipt download.
- Audit logs: unsupported until an allowed official source documents the route and add-on auth behavior.

Use `ClockifyRawClient` for advanced documented routes until a typed domain method is added with provenance and tests. When a route remains uncertain, add an explicit unsupported or experimental entry instead of fake behavior.
