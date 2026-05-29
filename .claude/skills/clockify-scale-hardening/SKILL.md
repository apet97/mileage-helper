---
name: clockify-scale-hardening
description: Harden Mileage for Clockify for many workspaces and many concurrent users — async webhook processing, DB pool sizing, metrics, and load evidence. Use when the user asks about scale, performance, throughput, webhook timeouts, connection pools, or "ready for all Clockify users".
---

# Clockify Scale Hardening

Target: thousands of installs, many concurrent webhook deliveries per workspace.

## G1 — Async webhook processing (Postgres job queue)

The `/webhook` request MUST do minimum work: verify signature → dedupe → persist
a PENDING job row → return 2xx. No Clockify writes on the request thread.

Architecture:
- Webhook request: verify + dedupe + enqueue PENDING job + 2xx. Zero Clockify writes.
- Worker profile (`--spring.profiles.active=worker`): polls with
  `SELECT ... FOR UPDATE SKIP LOCKED`, runs eligibility + loop-prevention checks
  first, then writes to Clockify, marks CONVERTED/FAILED.
- Shared Postgres is the queue broker — no Redis/SQS needed.
- SKIP LOCKED gives safe multi-worker horizontal scaling.
- Admin retry path and dedupe contract must survive unchanged.

Verification:
- Regression test: enqueue acks 2xx with no Clockify write made.
- Regression test: skip-locked claiming — two concurrent workers don't double-process.
- Worker liveness endpoint exposed and polled post-deploy.
- Queue depth + oldest-pending-age metrics wired.
- Stuck-job detection (job older than N minutes with status PENDING = alert).

## G2 — DB connection pool

Set explicit `spring.datasource.hikari.maximum-pool-size`, `minimum-idle`,
`connection-timeout` in `application.yaml`. Size against Railway Postgres max
connections. Virtual threads make unbounded blocking cheap — the pool is the
real ceiling. Recommended starting point: max=20, min-idle=5, timeout=30s.

## G3 — Metrics / observability

Expose `health,info,prometheus` via Micrometer. Add:
- Timers on webhook handling (enqueue path) and per-job worker execution.
- Timers on each Clockify client call (expenses, users, projects, files).
- Counters: conversion outcomes (CONVERTED / SKIPPED / FAILED / ALREADY_CONVERTED).
- Gauge: queue depth (PENDING job count).

NEVER tag metrics with userId, token values, or any PII.

## G4 — Dependency vulnerability scanning

Wire OWASP dependency-check into the build (`mvn dependency-check:check`).
Fix any HIGH/CRITICAL findings before submission. Add to CI.

## Workspace isolation

Every new query path must be workspace-scoped. Verify with rg for unscoped
repository methods before merging.

## Verification sequence
After any hardening change:
1. `mvn -pl addon-expenses-rest-api -am test`
2. Run the `clockify-publish-gate` skill.
3. For G1: manual webhook delivery test in a sacrificial workspace — confirm
   no conversion happens synchronously, worker picks up the job.
4. Keep diffs small; ask before structural addon-core/addon-db changes.
