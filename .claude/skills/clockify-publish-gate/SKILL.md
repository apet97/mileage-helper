---
name: clockify-publish-gate
description: Run the full Mileage-for-Clockify pre-publish verification and produce a dated evidence block for the Marketplace submission. Use before any Marketplace version submission, before a hosted deploy, or when the user asks "is this ready to publish / ship / submit".
---

# Clockify Publish Gate

Drive `Mileage for Clockify` to a provably-shippable state and emit copy-paste
evidence for `addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md`.

## Steps

1. `git status --short --branch` — confirm clean tree on the working branch.
2. Run `./scripts/verify-publish.sh`. If Testcontainers can't find Docker,
   fall back to the Docker Desktop socket wiring in CLAUDE.md. Capture exact output.
3. Static guardrail scans (must all be empty):
   - forbidden floats: `rg -n "\b(double|Double|float|Float)\b" addon-expenses-rest-api/src/main/java`
   - hardcoded Clockify hosts: `rg -n "api\.clockify\.me|global\.api\.clockify" addon-expenses-rest-api/src/main clockify-rest-client/src/main`
   - stale boilerplate scan from CLAUDE.md.
4. Manifest probe: build/run the compose stack and `curl -fsS .../manifest`;
   confirm schema `1.5`, key `mileage-for-clockify`, the 5 scopes, the 4 webhooks.
5. If a deploy happened: probe `/actuator/health`, `/manifest`,
   every current `/assets/mileage/settings*.js` file (`settings-date.js`,
   `settings-core.js`, `settings-ranges.js`, `settings-create.js`,
   `settings-admin.js`, `settings-tables.js`, and boot `settings.js`),
   `/assets/mileage/icon.png`, and unauthenticated `/iframe/mileage` (expect 401 + no-store/CSP/HSTS).
   For OCI, capture the systemd restart time and fresh `journalctl` error scan. For Railway, use `railway deployment list` for the CURRENT deployment id — never reuse an old id.
6. Write a dated evidence block (today's date, git sha, deployment id) and
   show it for pasting into PRE_PUBLISH_CHECKLIST.md. State explicitly if live
   Clockify smoke was SKIPPED.

## Hard rules
- Never weaken a test to make the gate pass.
- Never print secrets / tokens / receipt bytes.
- Do not claim a deploy is live without post-deploy probes for every current settings JS asset.
