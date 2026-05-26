#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${REPO_ROOT}"

section() {
  printf '\n== %s ==\n' "$1"
}

section "Clockify REST client multipart tests"
mvn -pl clockify-rest-client -Dtest=ExpensesClientTest,FilesClientTest test

section "Addon core claims normalizer tests"
mvn -pl addon-core -Dtest=ClaimsNormalizerTest test

section "Mileage security tests"
mvn -pl addon-expenses-rest-api -Dtest=MileageSecurityTest test

section "Full add-on reactor tests with Colima Testcontainers"
DOCKER_HOST=unix:///Users/15x/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
DOCKER_API_VERSION=1.44 \
mvn -pl addon-expenses-rest-api -am test \
  -Ddocker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy \
  -Ddocker.host=unix:///Users/15x/.colima/default/docker.sock \
  -Dapi.version=1.44

section "Git whitespace check"
git diff --check

section "Settings JavaScript syntax check"
node --check addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js

section "Secret scan"
gitleaks detect --source . --no-git --redact --verbose

section "Docker image build"
docker compose -f addon-expenses-rest-api/docker-compose.yml build
