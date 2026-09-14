#!/usr/bin/env bash

set -eu

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

if (( $# != 1 )); then
  printf 'Usage: %s LOCAL_MAVEN_REPOSITORY\n' "$0" >&2
  exit 2
fi

published_repository=$1
[[ -d "$published_repository" ]] || {
  printf 'Missing scaffold Maven repository: %s\n' "$published_repository" >&2
  exit 2
}

scratch_root=$(mktemp -d)
fixture_root="$scratch_root/application"
evidence_root="$repository_root/build/reports/scaffold-browser"

capture_evidence_and_cleanup() {
  local status=$?
  set +e
  trap - EXIT
  if [[ -d "$fixture_root/playwright-report" ]]; then
    mkdir -p "$evidence_root/playwright-report"
    cp -R "$fixture_root/playwright-report/." "$evidence_root/playwright-report/" || true
  fi
  if [[ -d "$fixture_root/test-results" ]]; then
    mkdir -p "$evidence_root/test-results"
    cp -R "$fixture_root/test-results/." "$evidence_root/test-results/" || true
  fi
  rm -rf "$scratch_root"
  [[ -e "$scratch_root" ]] && printf 'Warning: deferred cleanup for %s\n' "$scratch_root" >&2
  exit "$status"
}
trap capture_evidence_and_cleanup EXIT

"$repository_root/scripts/materialize-spring-boot-scaffold.sh" "$fixture_root"
(
  cd "$fixture_root"
  npm ci --ignore-scripts
  WOGE_REPOSITORY="$published_repository" npm run test:browser
)
