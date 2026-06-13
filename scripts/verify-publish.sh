#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${REPO_ROOT}"

section() {
  printf '\n== %s ==\n' "$1"
}

select_docker_env() {
  if [ -n "${DOCKER_HOST:-}" ]; then
    echo "Using existing DOCKER_HOST=${DOCKER_HOST}" >&2
    return 0
  fi
  if [ -S /Users/15x/.docker/run/docker.sock ]; then
    export DOCKER_HOST=unix:///Users/15x/.docker/run/docker.sock
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
    echo "Using Docker Desktop socket ${DOCKER_HOST}" >&2
    return 0
  fi
  if [ -S /Users/15x/.colima/default/docker.sock ]; then
    export DOCKER_HOST=unix:///Users/15x/.colima/default/docker.sock
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
    export DOCKER_API_VERSION="${DOCKER_API_VERSION:-1.44}"
    echo "Using Colima socket ${DOCKER_HOST}" >&2
    return 0
  fi
  echo "FAIL: no Docker Desktop or Colima socket found for Testcontainers" >&2
  return 1
}

section "Clockify REST client multipart tests"
mvn -pl clockify-rest-client -Dtest=ExpensesClientTest,FilesClientTest test

section "Addon core claims normalizer tests"
mvn -pl addon-core -Dtest=ClaimsNormalizerTest test

section "Mileage security tests"
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test

section "Docker Testcontainers environment"
select_docker_env

section "Full add-on reactor tests with Testcontainers"
mvn -pl addon-expenses-rest-api -am test

section "Git whitespace check"
git diff --check

section "Settings JavaScript syntax check"
./scripts/check-mileage-settings-js.sh

section "Static guardrails"
./scripts/check-static-guardrails.sh

section "Mileage date helper behavior"
node scripts/test-mileage-date-helpers.mjs

section "Mileage settings behavior"
node scripts/test-mileage-settings-behavior.mjs

section "Secret scan"
gitleaks detect --source . --no-git --redact --verbose

section "Docker image build"
docker compose -f addon-expenses-rest-api/docker-compose.yml build
