#!/usr/bin/env bash
# Mileage for Clockify - one-command local add-on over a Cloudflare Tunnel.
#
# Brings up Postgres + the add-on (web + worker) with docker compose, opens a
# public https://<random>.trycloudflare.com tunnel, wires ADDON_BASE_URL to it,
# waits for /manifest, and prints the URL to paste into Clockify. Ctrl-C tears
# the whole stack down.
#
# Usage:
#   scripts/dev-tunnel.sh            # reuse the existing image (fast)
#   scripts/dev-tunnel.sh --build    # rebuild the image after code changes
#   REBUILD=1 scripts/dev-tunnel.sh  # same as --build
#
# The trycloudflare URL is random per run, so re-paste the manifest each time.
# For a stable URL, create a named tunnel with a free Cloudflare account/domain:
#   https://developers.cloudflare.com/cloudflare-one/connections/connect-apps
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE="$REPO_ROOT/addon-expenses-rest-api/docker-compose.yml"
APP_PORT=8080
CF_LOG="$(mktemp -t mileage-cf)"
OVERRIDE="$(mktemp -t mileage-override)"
CF_PID=""

say() { printf '\033[36m[dev-tunnel]\033[0m %s\n' "$*"; }
err() { printf '\033[31m[dev-tunnel]\033[0m %s\n' "$*" >&2; }

cleanup() {
  say "shutting down..."
  [ -n "$CF_PID" ] && kill "$CF_PID" 2>/dev/null || true
  docker compose -f "$COMPOSE" -f "$OVERRIDE" down 2>/dev/null || true
  rm -f "$CF_LOG" "$OVERRIDE" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# --- preflight -------------------------------------------------------------
command -v cloudflared >/dev/null || { err "cloudflared not found - 'brew install cloudflared'"; exit 1; }
command -v docker >/dev/null      || { err "docker not found"; exit 1; }
docker info >/dev/null 2>&1        || { err "Docker daemon unreachable - start Docker/Colima first (e.g. 'colima start')"; exit 1; }

REBUILD_FLAG=""
if [ "${REBUILD:-}" = "1" ] || [ "${1:-}" = "--build" ]; then
  REBUILD_FLAG="--build"
fi

# --- open the tunnel, capture the public URL -------------------------------
say "opening Cloudflare tunnel -> http://localhost:$APP_PORT ..."
cloudflared tunnel --url "http://localhost:$APP_PORT" >"$CF_LOG" 2>&1 &
CF_PID=$!

BASE_URL=""
for _ in $(seq 1 30); do
  BASE_URL="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$CF_LOG" | head -1 || true)"
  [ -n "$BASE_URL" ] && break
  sleep 1
done
[ -n "$BASE_URL" ] || { err "tunnel URL never appeared:"; sed 's/^/  /' "$CF_LOG" >&2; exit 1; }
say "tunnel up: $BASE_URL"

# --- override: tunnel URL for app + worker, keep db off host port 5432 -----
cat >"$OVERRIDE" <<YAML
services:
  db:
    ports: !reset []
  addon:
    environment:
      ADDON_BASE_URL: "$BASE_URL"
  addon-worker:
    environment:
      ADDON_BASE_URL: "$BASE_URL"
YAML

# --- bring up the stack ----------------------------------------------------
say "starting Postgres + add-on (web + worker)${REBUILD_FLAG:+ - rebuilding}..."
docker compose -f "$COMPOSE" -f "$OVERRIDE" up -d $REBUILD_FLAG

# --- wait for the app ------------------------------------------------------
say "waiting for /manifest ..."
READY=""
for _ in $(seq 1 60); do
  if curl -fsS -o /dev/null "http://localhost:$APP_PORT/manifest" 2>/dev/null; then READY=1; break; fi
  sleep 2
done
[ -n "$READY" ] || { err "app never became ready - last logs:"; docker compose -f "$COMPOSE" -f "$OVERRIDE" logs --tail=40 addon >&2; exit 1; }

cat <<BANNER

  Mileage for Clockify is live over Cloudflare Tunnel

     Manifest : $BASE_URL/manifest
     Health   : $BASE_URL/actuator/health
     Iframe   : $BASE_URL/iframe/mileage

  -> Install in Clockify: dashboard -> add-on / developer page -> install from the manifest URL above.
  -> Keep this terminal open. Press Ctrl-C to stop the tunnel and the whole stack.

BANNER

# Tail app + worker logs; Ctrl-C triggers cleanup.
docker compose -f "$COMPOSE" -f "$OVERRIDE" logs -f addon addon-worker
