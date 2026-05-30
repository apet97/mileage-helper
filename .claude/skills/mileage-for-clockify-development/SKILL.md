---
name: mileage-for-clockify-development
description: Project-specialized knowledge for the Mileage for Clockify add-on. Activates ANY time you are touching this codebase — bug fixes, behavior changes, deployments, doc updates, audits. Encodes the hard rules and the production-only gotchas so you don't have to rediscover them.
---

# Mileage for Clockify — Development Skill

You are working on a standalone Java 21 / Spring Boot 3.3.x add-on for the Clockify Marketplace. The add-on is functionally complete, security-hardened, scale-hardened, and runs in production at `https://mileage-for-clockify-production.up.railway.app`. The mode is **maintenance**: small diffs, focused regression tests, full reactor verification for behavior changes, hosted probes after deploys.

## When the user gives you any task in this repo

1. Read `CLAUDE.md`, `AGENTS.md`, and the relevant `README.md` / module docs.
2. Plan the smallest diff that achieves the goal.
3. Run the full reactor before claiming done. Use Colima wiring on this Mac.
4. After Behavior, Docker, manifest, or migration changes: also run the hosted probe section below.
5. Keep `CLAUDE.md` and `AGENTS.md` current — if a change invalidates a Product Fact, a Hard Rule, or the Hosted State entries, update them in the same PR. The meta-rule in `CLAUDE.md` is non-negotiable.

## Hard rules — these crash production if you violate them

| Rule | Why |
|---|---|
| No `float` / `double` / `Float` / `Double` for mileage, rate, money values. Use `BigDecimal`. | Money rounding errors compound across many conversions. Tests enforce this with a static rg scan. |
| No hardcoded Clockify API hosts. Use claims `backendUrl` or installation context. | The add-on routes traffic per Clockify region; hardcoding the global host breaks EU customers. |
| Never expose installation tokens to frontend JS/HTML, logs, docs, screenshots, or test output. | A leaked installation token grants workspace-scope access until rotation. |
| Webhook handlers must acknowledge HTTP 2xx even after internal failure (logging the failure first). | Clockify retries 4xx/5xx; blindly retrying internal failures storms us. |
| Webhook controller MUST NOT call `AddonWebhookHandler.handle` or any Clockify gateway method on the request thread when a `WebhookJobQueue` bean is wired. Verify → dedupe → enqueue PENDING → 2xx. | The G1 async contract; reverting reintroduces the timeout-and-retry storm. |
| `WebhookJobWorkerConfig` MUST stay an `@AutoConfiguration(after = AddonDbAutoConfiguration.class)` registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. | Plain `@Configuration` (even with `@ConditionalOnBean` at the `@Bean` method level) silently skips the worker beans in production. The 2026-05-30 deploys `33e2c56c` and `fdf6a328` proved this; only `9d89508d` registered the worker. |
| Worker `claimNext` transaction must wrap `SELECT … FOR UPDATE SKIP LOCKED` and the status flip to `CLAIMED` in ONE transaction. Do NOT extend it across the handler dispatch. | The Clockify HTTPS write must happen outside any DB lock so the row lock doesn't span a network call. |
| Prometheus counters/gauges may be tagged ONLY by stable enums (`outcome`, `status`). Never tag with `userId`, `workspaceId`, `expenseId`, token values. | Cardinality explodes; tagged identifiers leak into scrape endpoints. `MileageConversionMetricsTest` enforces this. |
| Do not lower `failBuildOnCVSS` below `7.0` in the OWASP dep-check plugin. Document suppressions; never blanket-skip findings. | The HIGH/CRITICAL gate is the whole point. |
| New `addon-db` platform migrations must be numbered AFTER the highest applied mileage migration in production (currently V17 lands after V16). | Flyway validates strict ordering and crashes boot with `Detected resolved migration not applied to database: N`. The d11e2088 deploy crashed on this with V7. |
| Native expense conversion must aggressively prevent loops: skip mileage audit markers, output-category expenses, and already-converted expenses before writing back to Clockify. | The addon's own update fires another `EXPENSE_UPDATED` webhook; without the guard you'd recursively re-convert. Pinned by `updatedWebhookOnAlreadyConvertedExpenseSkipsToBreakLoop`. |
| User-facing mileage creation must use the verified Clockify user JWT claim. Do not add `userId` back to the create DTO or multipart allowlist. | Anything else lets a user impersonate another user. |
| No task selector / `taskId` / `TASK_READ` scope unless the product requirement changes and live scope evidence is captured first. | Manifest-scope minimization. |
| Keep `addon-core` and `addon-db` changes conservative. ASK before structural changes. | These are shared platform modules; structural drift breaks future add-ons. |

## Commands you'll use

```bash
# Repo-local publish safety bundle (runs the whole gate)
./scripts/verify-publish.sh

# Fast focused reactor (most behavior work)
mvn -pl addon-expenses-rest-api -am test

# Clean reactor (use after dep bumps or weird state)
mvn -pl addon-expenses-rest-api -am clean test

# Full reactor with Colima Testcontainers — the canonical local CI parity check
DOCKER_HOST=unix:///Users/15x/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test \
  -Ddocker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy \
  -Ddocker.host=unix:///Users/15x/.colima/default/docker.sock \
  -Dapi.version=1.44

# Docker image (builds BOTH addon web pod and addon-worker pod)
docker compose -f addon-expenses-rest-api/docker-compose.yml build

# OWASP dep-check (requires NVD_API_KEY for fast scan; CI dep-check job runs it with secret)
NVD_API_KEY=… mvn -pl addon-expenses-rest-api -am -DskipTests dependency-check:check
```

## Test schemas can carry stale Flyway history

If you've ever applied a migration locally that got renamed (like the V7→V17 case), the local Postgres `addon_db_test`, `mileage_test`, and `mileage_skiplocked` schemas inside `addon_test` retain that history and reactor tests crash with `Detected applied migration not resolved locally`. Drop and re-create:

```bash
PSQL=/opt/homebrew/opt/postgresql@16/bin/psql
for s in addon_db_test mileage_test mileage_skiplocked; do
  $PSQL -h localhost -p 5432 -U 15x -d addon_test -c "DROP SCHEMA IF EXISTS $s CASCADE;"
done
```

## Deploy + verify

1. Push to `main`.
2. `railway up --service mileage-for-clockify --detach` to trigger the deploy (Railway is not auto-deploy from GitHub on this project).
3. Poll `railway deployment list` until the new id transitions to `SUCCESS` (≈2–3 min build + 30s deploy).
4. Run the hosted probe section below. Any 4xx/5xx on health/manifest, or missing metric family, is a regression.
5. Append a dated evidence block to `addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md`.

For a structured run, dispatch the `mileage-deployer` subagent.

## Hosted probe set (run after every deploy)

```bash
BASE=https://mileage-for-clockify-production.up.railway.app
curl -sS -w "\n  %{http_code}\n" "$BASE/actuator/health"
curl -sS -w "\n  %{http_code}\n" "$BASE/manifest"
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings-date.js" | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/settings.js"      | head -3
curl -sS -o /dev/null -D - "$BASE/assets/mileage/icon.png"         | head -3
curl -sS -o /dev/null -D - "$BASE/iframe/mileage"                  | head -10
# G3 metrics — confirm all three families and check for no PII tags
curl -sS "$BASE/actuator/prometheus" | grep -E "^mileage_conversion_outcome_total|^mileage_webhook_queue_depth|^mileage_webhook_job_process_seconds_count"
# Worker liveness — proves the @Scheduled poll loop is hitting the DB
curl -sS "$BASE/actuator/prometheus" | grep -E "^tasks_scheduled_execution_seconds_count.*pollAndProcess.*outcome=\"SUCCESS\""
```

## Live Clockify E2E webhook smoke

Requires:
- The add-on installed in a sacrificial workspace (Clockify dashboard → Apps → install from `https://mileage-for-clockify-production.up.railway.app/manifest`).
- `CLOCKIFY_API_KEY`, `CLOCKIFY_WORKSPACE_ID`, `CLOCKIFY_API_BASE_URL` set in the operator's shell.
- The Clockify expense-create multipart contract: send the miles value in the **`amount`** multipart field, NOT `quantity`. Clockify computes `total = amount × unitPrice` and writes the resulting `quantity` back. Sending `quantity=N` silently records `quantity=0`. The addon's `ClockifyExpenseGateway.createBody` already uses `amount`; this matters for any ad-hoc smoke hitting Clockify directly.

For a structured run, dispatch the `mileage-webhook-smoke` subagent.

## When the user asks "what's left"

The project is in maintenance mode. There is no debt list. There are no scale gaps. There are no open PRs. Production runs `9d89508d` (or later) with the worker polling at ~4 Hz and zero exceptions. Suggest concrete maintenance items only if asked, and frame them as optional.

## What changes to this skill require

Update this `SKILL.md` AND `CLAUDE.md` / `AGENTS.md` whenever any of the following changes:
- A new Hard Rule is added or an existing one is amended.
- The deploy / probe procedure changes (URL, command, expected response shape).
- A new module is added or a structural refactor moves package responsibilities.
- A new metric, env var, or migration is added.
- A new live-smoke prerequisite is introduced.

If you change code that this skill describes and you do NOT update the skill, the next agent will follow stale guidance. The meta-rule in `CLAUDE.md` makes this a hard requirement.
