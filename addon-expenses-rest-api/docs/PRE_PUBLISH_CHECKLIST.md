# Mileage for Clockify Pre-Publish Checklist

This checklist is the current publish gate. Older deploy transcripts live in git history; keep this file focused on the gates and the latest reusable evidence.

## Required Gates Run Now

Run from the repository root before claiming publish readiness:

- [ ] `git status --short --branch` reviewed.
- [ ] `./scripts/verify-publish.sh` passes.
- [ ] `git diff --check` passes.
- [ ] If a new OCI deploy was made, capture the systemd restart time, jar SHA, and a fresh `journalctl -u mileage-for-clockify.service` scan for boot errors.
- [ ] If a hosted deploy was made, probe `/actuator/health`, `/manifest`, every `/assets/mileage/settings*.js` asset, `/assets/mileage/report.css`, `/assets/mileage/report.js`, `/assets/mileage/packet.css`, `/assets/mileage/packet.js`, `/assets/mileage/icon.png`, unauthenticated `/iframe/mileage`, unauthenticated `/iframe/report`, unauthenticated `/iframe/reimbursement-packet`, prometheus metric families, and scheduler liveness.
- [ ] If Railway was explicitly restored for this run, use `railway deployment list` for the current deployment id. Do not reuse old ids from historical notes.

`./scripts/verify-publish.sh` runs:

- Focused multipart, claims-normalizer, and `MileageSecurityTest` checks.
- Full Docker/Testcontainers-backed add-on reactor.
- `git diff --check`, static JS checks, static guardrails, date/settings behavior Node tests.
- `gitleaks detect --source . --no-git --redact --verbose`.
- `docker compose -f addon-expenses-rest-api/docker-compose.yml build`.

## Hosted Probe Set

Use the current base URL unless a run explicitly targets Cloudflared or restored Railway:

```bash
BASE=https://89-168-93-85.sslip.io
curl -sS -w "\n%{http_code}\n" "$BASE/actuator/health"
curl -sS -w "\n%{http_code}\n" "$BASE/manifest"
for asset in \
  settings-date.js settings-core.js settings-ranges.js settings-create.js \
  settings-admin.js settings-tables.js settings.js; do
  curl -sS -o /dev/null -D - "$BASE/assets/mileage/$asset" | head -3
done
curl -sS -o /dev/null -D - "$BASE/assets/mileage/report.css" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/report.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/packet.css" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/packet.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/icon.png" | head -3
curl -sS -o /dev/null -D - "$BASE/iframe/mileage" | head -10
curl -sS -o /dev/null -w "%{http_code}\n" "$BASE/iframe/report"
curl -sS -o /dev/null -w "%{http_code}\n" "$BASE/iframe/reimbursement-packet"
curl -sS "$BASE/actuator/prometheus" | grep -E "^mileage_conversion_outcome_total|^mileage_webhook_queue_depth|^mileage_webhook_job_process_seconds_count"
curl -sS "$BASE/actuator/prometheus" | grep -E "^tasks_scheduled_execution_seconds_count.*pollAndProcess.*outcome=\"SUCCESS\""
```

Metric expectations:

- `mileage_conversion_outcome_total` has one series for each current `MileageConversionStatus`: `RECEIVED`, `DRY_RUN`, `SKIPPED`, `CONVERTING`, `CONVERTED`, `FAILED`, `DELETED`, `RESTORED_IGNORED`.
- `mileage_webhook_queue_depth{status="PENDING"}` is present.
- `mileage_webhook_job_process_seconds_count` is present; `0` is acceptable before a real webhook lands.
- `tasks_scheduled_execution_seconds_count{code_function="pollAndProcess",outcome="SUCCESS",exception="none"}` grows across scrapes.
- No `mileage_` metric line may contain `userid=`, `workspaceid=`, `expenseid=`, or `token=`.

## Optional Live Clockify Smoke

Live Clockify smoke is optional and requires local secrets. Never commit or echo API keys/tokens. If not run, final output must say it was skipped.

There is no repo-owned live smoke script. Use environment variables, stdin, or a local secret store for sacrificial-workspace testing. Do not paste real keys into repo files, docs, screenshots, terminal transcripts intended for docs, or final reports.

## Latest Evidence Snapshot

Historical only unless rerun for the current change.

- 2026-06-15 docs hygiene publish check: `./scripts/verify-publish.sh` passed end-to-end, hosted probes passed for health, manifest, every then-current settings asset, report assets, icon, unauthenticated iframe/report guards, prometheus metric families, scheduler liveness, and no PII metric tags. Live Clockify smoke stopped before mutation because the sacrificial workspace had no Mileage add-on webhook URL installed; reinstall the add-on before rerunning the E2E smoke.
- 2026-06-13 OCI deploy at git `785408b`: `./scripts/verify-publish.sh` passed end-to-end, jar SHA matched after copy to OCI, `mileage-for-clockify.service` restarted active, and hosted probes passed for health, manifest, every current settings asset, report assets, icon, unauthenticated iframe/report guards, prometheus metric families, scheduler liveness, and no PII metric tags.
- 2026-06-13 installed Clockify browser smoke: iframe boot requests carried `Authorization`, URL token scrub happened after boot, settings loaded, no error toasts appeared, Preview/Create worked, Mine refreshed with the new `ADDON_FORM` row, queue depth stayed `0`, and the loop-guard `SKIPPED` counter incremented after Clockify echoed the add-on create.
- 2026-06-06 OCI deploys proved the deferred note-charge reconcile path and the `MileageNoteReconcileWorker` scheduler. Add-on-created notes are initially clean; the `(Clockify category charge: X)` parenthetical appears asynchronously after the row settles.
- 2026-05-30 and 2026-05-31 live webhook smokes proved async webhook queueing, SKIP LOCKED worker dispatch, conversion-loop prevention, and cleanup delete behavior. Treat those as historical evidence unless rerun.

## Required Manual Product Gates

- [ ] Runtime `/manifest` probe passes.
- [ ] Runtime probes pass for every `/assets/mileage/settings*.js` asset.
- [ ] Runtime `/assets/mileage/report.css`, `/assets/mileage/report.js`, `/assets/mileage/packet.css`, `/assets/mileage/packet.js`, and `/assets/mileage/icon.png` probes pass.
- [ ] Static secret scan passes.
- [ ] Manifest uses the production `ADDON_BASE_URL`.
- [ ] Installed lifecycle payload with official webhook entries stores manifest event types.
- [ ] Webhook requests require valid `Clockify-Signature` and `Clockify-Webhook-Event-Type`.
- [ ] `/api/mileage/**` requires a verified user token.
- [ ] User mileage creation uses verified token claims for target user identity; create requests and multipart form fields do not carry `userId`.
- [ ] Installation token is not exposed to frontend HTML, JavaScript, logs, docs, screenshots, or test output.
- [ ] Uninstall removes stored installation and webhook secrets.
- [ ] Diagnostics show installation, settings, native conversion readiness, setup checklist, and webhook queue health.
- [ ] UI creates mileage without raw or hidden user ID entry.
- [ ] Report buttons open `/iframe/report` without client-supplied display-name query parameters; single-user labels are resolved server-side.
- [ ] Deleting a Clockify expense marks the audit row `DELETED` and removes it from `Mine`/`Team` refreshes.
- [ ] Active source has no legacy temp-addon schema names, deleted shell probes, or generic expense-boilerplate references outside immutable Flyway history.

## Required Manual Marketplace Gates

- [ ] Test install in a CAKE.com Marketplace testing environment.
- [ ] Test at least two workspaces.
- [ ] Test at least OWNER/ADMIN and MEMBER users.
- [ ] Test sidebar UI.
- [ ] Test settings UI.
- [ ] Test JSON mileage creation.
- [ ] Test receipt mileage creation.
- [ ] Test native/mobile unit expense conversion via `EXPENSE_CREATED`.
- [ ] Test update/delete/restore webhook behavior.
- [ ] Confirm listing icon, screenshots, privacy policy, terms, and support contact are ready in English.

## Known Non-Goals For Pre-Publish

- No production Marketplace submission is performed by this checklist.
- No live Clockify workspace mutation is performed unless the operator explicitly allows sacrificial-workspace testing.
