#!/usr/bin/env bash

set -u

spike_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
gradle_wrapper="$spike_directory/../spring-html-htmx-baseline/gradlew"
failure_count=0

"$gradle_wrapper" -p "$spike_directory" test

for fixture in raw-string boolean-string; do
  output_file=$(mktemp)
  if "$gradle_wrapper" -p "$spike_directory" \
    -PnegativeFixture="$fixture" \
    --no-configuration-cache \
    --no-build-cache \
    compileKotlin >"$output_file" 2>&1; then
    printf 'Negative fixture unexpectedly compiled: %s\n' "$fixture" >&2
    failure_count=$((failure_count + 1))
  elif ! grep -Eq 'Argument type mismatch|Type mismatch' "$output_file"; then
    printf 'Negative fixture failed without an expected type diagnostic: %s\n' "$fixture" >&2
    cat "$output_file" >&2
    failure_count=$((failure_count + 1))
  else
    printf 'Negative fixture rejected as expected: %s\n' "$fixture"
  fi
  rm -f "$output_file"
done

if (( failure_count > 0 )); then
  exit 1
fi

printf 'HTML writer strategy validation passed.\n'
