# 🚗 Mileage for Clockify

> A Clockify Marketplace add-on that turns mileage into real Clockify expenses — and automatically converts native `Mileage`-category expenses into accurate, reimbursable amounts via signed webhooks.

[![CI](https://github.com/apet97/mileage-helper/actions/workflows/ci.yml/badge.svg)](https://github.com/apet97/mileage-helper/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192)
![Manifest schema](https://img.shields.io/badge/manifest-schema%201.5-555)
![Minimum plan](https://img.shields.io/badge/min%20plan-PRO-7B61FF)

Users log mileage from a Clockify sidebar UI and have it created as a proper expense. The add-on also watches the workspace's `Mileage` expense category, so **any** expense created in Clockify — web or mobile — is converted into a reimbursement with a clean, auditable note.

---

## ✨ Features

- **Two ways to capture mileage** — submit from the add-on's own form, or just create a native Clockify `Mileage` expense and let the webhook converter handle it.
- **Exact money math** — every mileage / rate / amount value is `BigDecimal`. Clockify receives the rounded amount while the UI keeps full decimal precision.
- **Honest notes** — the converted note **preserves any user-typed note** and reconciles the add-on's calculated amount with the real Clockify category charge, e.g. `12.4 miles x 7.25123 = 89.915252 (Clockify category charge: 89.90)`.
- **Async, built for scale** — webhooks are verified, de-duplicated, and queued in Postgres; a worker drains them with `SELECT … FOR UPDATE SKIP LOCKED`, so Clockify never waits on a conversion or retries on a timeout.
- **Loop-safe** — the add-on's own write fires another webhook, which the conversion guard correctly skips.
- **Observable** — Prometheus counters/gauges for conversion outcomes, queue depth, and worker latency at `/actuator/prometheus` (low-cardinality tags only — no PII).
- **Secure by default** — installation tokens stay server-side, CSP/HSTS/Permissions-Policy headers, OWASP dependency-check gate (fail on CVSS ≥ 7.0), and workspace isolation on every query.

## 🧭 How conversion works

```text
Clockify ──EXPENSE_CREATED──▶  /webhook/**            ──▶ 2xx (no Clockify write on this thread)
                               verify → dedupe → enqueue PENDING
                                       │
                                       ▼
                               addon_webhook_jobs  (Postgres queue)
                                       │   SELECT … FOR UPDATE SKIP LOCKED
                                       ▼
                               WebhookJobWorker ──▶ MileageConversionService ──▶ Clockify update
                                                    (BigDecimal math, loop guard, clean note)
```

The web pod and worker pod run from the **same image**; scale workers horizontally with `docker compose up --scale addon-worker=N`.

## 🏗️ Architecture

A small Maven multi-module project: one product module plus the minimal platform modules copied from the add-on factory.

| Module | Responsibility |
| --- | --- |
| [`addon-expenses-rest-api/`](addon-expenses-rest-api/) | **Product module** — add-on source, server-rendered iframe UI, manifest, settings, conversion, async worker, Prometheus metrics, Dockerfile & compose. |
| [`addon-core/`](addon-core/) | Shared add-on auth, lifecycle routing, manifest controller, security headers, async webhook dispatch. |
| [`addon-db/`](addon-db/) | Flyway / JPA persistence: installation context, encrypted tokens, settings, webhook events, async job queue. |
| [`clockify-rest-client/`](clockify-rest-client/) | Typed Clockify REST client with endpoint-provenance-backed route behavior. |
| [`addon-testkit/`](addon-testkit/) | Test builders and fixtures shared across modules. |
| [`repo/`](repo/) | Vendored Maven artifacts for the Clockify add-on SDK. |

> The ignored local clone `addon-expenses-rest-api/addon-java-sdk/` is read-only reference material — never edit or commit it.

## 🧰 Tech stack

| Layer | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.3.x |
| Persistence | PostgreSQL · Flyway · JPA/Hibernate |
| HTTP client | Typed Clockify REST client (JDK `HttpClient`) |
| Metrics | Micrometer + Prometheus |
| Build | Maven (multi-module reactor) |
| Tests | JUnit 5 · AssertJ · Mockito · Testcontainers |
| Delivery | Docker · Cloudflared dev tunnel · Railway historical production |

## 🚀 Quickstart

```bash
# Full publish safety bundle (asset checks + reactor)
./scripts/verify-publish.sh

# Fast focused add-on reactor
mvn -pl addon-expenses-rest-api -am test

# Run locally on Docker (web pod + worker pod + Postgres)
docker compose -f addon-expenses-rest-api/docker-compose.yml up -d
curl -fsS http://localhost:8080/manifest
docker compose -f addon-expenses-rest-api/docker-compose.yml down
```

### Run as a live Clockify add-on (free public tunnel)

For real Clockify install / iframe / webhook testing without paid hosting, expose the local stack through a free **Cloudflare Tunnel** — no account, no card, and (unlike ngrok's free plan) no browser interstitial on the iframe:

```bash
scripts/dev-tunnel.sh           # reuse the built image (fast)
scripts/dev-tunnel.sh --build   # rebuild after code changes
```

It opens a `https://<random>.trycloudflare.com` tunnel, brings up Postgres + the add-on, wires `ADDON_BASE_URL` to the tunnel, waits for `/manifest`, and prints the URL to paste into Clockify's add-on / developer page. In tunnel mode the web container runs the webhook worker too, so the public `/actuator/prometheus` endpoint shows worker liveness and live-smoke deltas; normal compose still uses separate web and worker services. `Ctrl-C` tears the whole stack down. Requires `cloudflared` (`brew install cloudflared`) and a running Docker/Colima daemon. The URL is random per run — reinstall the manifest after each restart. For a stable one, use a [named Cloudflare tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps).

<details>
<summary>Testcontainers can't find Docker? Use Colima explicitly</summary>

```bash
DOCKER_HOST=unix:///Users/15x/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test \
  -Ddocker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy \
  -Ddocker.host=unix:///Users/15x/.colima/default/docker.sock \
  -Dapi.version=1.44
```
</details>

<details>
<summary>Local port 5432 busy? Keep the compose database internal</summary>

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml \
  -f <(printf 'services:\n  db:\n    ports: !reset []\n') up -d
```
</details>

## 🔌 API surface

- **UI** — `GET /iframe/mileage`, `GET /iframe/settings`
- **User** — `GET /api/mileage/create-context`, `GET /api/mileage/mine`, `GET /api/mileage/mine.csv`, `POST /api/mileage/preview`, `POST /api/mileage/expenses`
- **Admin** — settings, Mileage category repair, diagnostics, category options, team list/export, conversion list/detail/retry/export under `/api/mileage`
- **Webhooks** — `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `EXPENSE_RESTORED`
- **Ops** — `GET /manifest`, `GET /actuator/health`, `GET /actuator/prometheus`

Manifest: schema `1.5`, key `mileage-for-clockify`, minimum plan `PRO`, scopes `EXPENSE_READ/WRITE`, `USER_READ`, `PROJECT_READ`, `WORKSPACE_READ`.

## 🌐 Production

| | |
| --- | --- |
| App URL | `https://mileage-for-clockify-production.up.railway.app` |
| Manifest | `https://mileage-for-clockify-production.up.railway.app/manifest` |
| Current deployment id | run `railway deployment list` (old ids in notes are historical, not current truth) |

Dated deploy / smoke evidence lives in [the pre-publish checklist](addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md). Live Clockify checks are optional and require local secrets — never commit or echo API keys/tokens.

## ⚙️ Configuration

Runtime config uses `SPRING_DATASOURCE_*` (incl. `SPRING_DATASOURCE_HIKARI_*` pool overrides), `ADDON_BASE_URL`, `ADDON_KEY`, `ADDON_NAME`, `ADDON_DESCRIPTION`, `ADDON_CRYPTO_*`, and `MILEAGE_WORKER_*` (worker toggle, poll delay, stuck-job timeout, batch size). Default CORS allows Clockify origins plus the `ADDON_BASE_URL` origin. Full list: [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md).

## 📚 Documentation

| Doc | Purpose |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Binding agent rules, module map, non-negotiables. |
| [CLAUDE.md](CLAUDE.md) | Claude Code project guide. |
| [addon-expenses-rest-api/README.md](addon-expenses-rest-api/README.md) | Product module guide. |
| [endpoints.md](addon-expenses-rest-api/endpoints.md) · [models.md](addon-expenses-rest-api/models.md) · [webhooks.md](addon-expenses-rest-api/webhooks.md) · [edge-cases.md](addon-expenses-rest-api/edge-cases.md) · [reports.md](addon-expenses-rest-api/reports.md) | Active product docs. |
| [PRE_PUBLISH_CHECKLIST.md](addon-expenses-rest-api/docs/PRE_PUBLISH_CHECKLIST.md) | Local, live-dev, and manual gates before Marketplace submission. |
