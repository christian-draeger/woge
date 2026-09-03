#!/usr/bin/env bash

set -euo pipefail

spike_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
gradle_wrapper="$spike_directory/../spring-html-htmx-baseline/gradlew"
failure_count=0

"$gradle_wrapper" -p "$spike_directory" test

class_file="$spike_directory/build/classes/kotlin/main/woge/css/CssAuthoringKt.class"
metadata_file=$(mktemp)
trap 'rm -f "$metadata_file"' EXIT
javap -v "$class_file" >"$metadata_file"

metadata_patterns=(
  'org.intellij.lang.annotations.Language('
  'value="CSS"'
  'prefix=".woge-declaration-list {"'
  'suffix="}"'
)
metadata_valid=true
for pattern in "${metadata_patterns[@]}"; do
  if ! grep -Fq "$pattern" "$metadata_file"; then
    printf 'Compiled API is missing language-injection metadata: %s\n' "$pattern" >&2
    metadata_valid=false
    failure_count=$((failure_count + 1))
  fi
done
if $metadata_valid; then
  printf 'JetBrains CSS language-injection metadata retained in bytecode.\n'
fi

for fixture in stylesheet-as-declarations string-as-stylesheet; do
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

(cd "$spike_directory" && npm test)

if (( failure_count > 0 )); then
  exit 1
fi

printf 'CSS authoring spike validation passed.\n'
