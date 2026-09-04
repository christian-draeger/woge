#!/usr/bin/env bash

set -euo pipefail

spike_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
gradle_wrapper="$spike_directory/../spring-html-htmx-baseline/gradlew"
negative_output=$(mktemp)
archive_listing=$(mktemp)
trap 'rm -f "$negative_output" "$archive_listing"' EXIT

"$gradle_wrapper" -p "$spike_directory" check --rerun-tasks

if "$gradle_wrapper" -p "$spike_directory" :consumer:compileKotlin \
  -PnegativeFixture=string-density --rerun-tasks >"$negative_output" 2>&1; then
  printf 'The invalid string density fixture compiled unexpectedly.\n' >&2
  exit 1
fi
if ! grep -Fq "actual type is 'String', but 'ProjectBoardDensity' was expected" "$negative_output"; then
  printf 'The invalid density fixture did not produce the expected compiler diagnostic.\n' >&2
  cat "$negative_output" >&2
  exit 1
fi

node "$spike_directory/registry.mjs" verify \
  "$spike_directory/registry/project-board/0.1.0/manifest.json" >/dev/null
node "$spike_directory/registry.mjs" verify \
  "$spike_directory/registry/project-board/0.2.0/manifest.json" >/dev/null
(cd "$spike_directory" && npm test)

jar tf "$spike_directory/binary-components/build/libs/binary-components-0.1.0.jar" >"$archive_listing"
for asset in META-INF/woge/components.json META-INF/woge/project-board.css; do
  if ! grep -Fxq "$asset" "$archive_listing"; then
    printf 'Packaged component asset is missing: %s\n' "$asset" >&2
    exit 1
  fi
done

(cd "$spike_directory" && npm run measure)
printf 'Component distribution spike validation passed.\n'
