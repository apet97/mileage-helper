#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if rg -n "\b(double|Double|float|Float)\b" addon-expenses-rest-api/src/main/java; then
  echo "FAIL: float/double found in production mileage source" >&2
  exit 1
fi

if rg -n "api\.clockify\.me|global\.api\.clockify" addon-expenses-rest-api/src/main clockify-rest-client/src/main; then
  echo "FAIL: hardcoded Clockify host found" >&2
  exit 1
fi
