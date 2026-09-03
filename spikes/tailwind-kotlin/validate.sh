#!/usr/bin/env bash

set -euo pipefail

spike_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
gradle_wrapper="$spike_directory/../spring-html-htmx-baseline/gradlew"

"$gradle_wrapper" -p "$spike_directory" check --rerun-tasks

archive_listing=$(mktemp)
trap 'rm -f "$archive_listing"' EXIT
jar tf "$spike_directory/build/libs/tailwind-kotlin-spike.jar" >"$archive_listing"
for asset in application.css tailwind.min.css tailwind.min.css.map; do
  if ! grep -Fxq "static/assets/$asset" "$archive_listing"; then
    printf 'Packaged style asset is missing: %s\n' "$asset" >&2
    exit 1
  fi
done
if [[ $(grep -Ec '^static/assets/[^/]+$' "$archive_listing") -ne 3 ]]; then
  printf 'Packaged style assets contain stale or unexpected files.\n' >&2
  grep -E '^static/assets/[^/]+$' "$archive_listing" >&2
  exit 1
fi

(cd "$spike_directory" && npm test)
(cd "$spike_directory" && npm run measure)

if [[ "${WOGE_COMPARE_TAILWIND_STANDALONE:-false}" == "true" ]]; then
  "$spike_directory/compare-standalone.sh"
fi

printf 'Tailwind Kotlin spike validation passed.\n'
