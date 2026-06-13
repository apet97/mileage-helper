#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

shopt -s nullglob
files=(addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings*.js)
if [ "${#files[@]}" -eq 0 ]; then
  echo "FAIL: no mileage settings JS assets found" >&2
  exit 1
fi

for file in "${files[@]}"; do
  node --check "$file"
done
