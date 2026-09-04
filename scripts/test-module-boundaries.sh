#!/usr/bin/env bash

set -u

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
scratch_root=$(mktemp -d)
trap 'rm -rf "$scratch_root"' EXIT
failure_count=0

fail() {
  printf 'Module-boundary tests: %s\n' "$1" >&2
  failure_count=$((failure_count + 1))
}

prepare_fixture() {
  local fixture_root=$1
  mkdir -p "$fixture_root/scripts"
  cp "$repository_root/scripts/validate-module-boundaries.sh" "$fixture_root/scripts/"
  cp -R "$repository_root/config" "$fixture_root/"
  cp -R "$repository_root/modules" "$fixture_root/"
  cp -R "$repository_root/adapters" "$fixture_root/"
  cp -R "$repository_root/integrations" "$fixture_root/"
  cp -R "$repository_root/testing" "$fixture_root/"
}

expect_rejection() {
  local fixture_root=$1
  local expected_message=$2
  local log_file="$fixture_root/validation.log"

  if bash "$fixture_root/scripts/validate-module-boundaries.sh" >"$log_file" 2>&1; then
    fail "fixture '$fixture_root' was accepted"
    return
  fi
  if ! grep -Fq -- "$expected_message" "$log_file"; then
    fail "fixture '$fixture_root' did not report '$expected_message'"
  fi
}

cycle_fixture="$scratch_root/cycle"
prepare_fixture "$cycle_fixture"
awk -F '\t' -v OFS='\t' '
  $1 == "woge-spring-boot-autoconfigure" { $6 = $6 ",woge-spring-boot-starter" }
  { print }
' "$cycle_fixture/config/architecture/module-boundaries.tsv" >"$cycle_fixture/module-boundaries.next"
mv "$cycle_fixture/module-boundaries.next" "$cycle_fixture/config/architecture/module-boundaries.tsv"
printf '\ndependencies {\n    compileOnly(project(":woge-spring-boot-starter"))\n}\n' \
  >>"$cycle_fixture/integrations/woge-spring-boot-autoconfigure/build.gradle.kts"
expect_rejection "$cycle_fixture" "contain a cycle"

drift_fixture="$scratch_root/drift"
prepare_fixture "$drift_fixture"
grep -Fv 'api(project(":woge-core"))' \
  "$drift_fixture/modules/woge-protocol/build.gradle.kts" >"$drift_fixture/protocol.next"
mv "$drift_fixture/protocol.next" "$drift_fixture/modules/woge-protocol/build.gradle.kts"
expect_rejection "$drift_fixture" "Gradle dependencies differ from the manifest"

leak_fixture="$scratch_root/framework-leak"
prepare_fixture "$leak_fixture"
mkdir -p "$leak_fixture/modules/woge-core/src/main/kotlin/dev/woge/core"
printf '%s\n' \
  'package dev.woge.core' \
  'import org.springframework.context.ApplicationContext' \
  'public interface InvalidCoreApi { public val context: ApplicationContext }' \
  >"$leak_fixture/modules/woge-core/src/main/kotlin/dev/woge/core/InvalidCoreApi.kt"
expect_rejection "$leak_fixture" "references a host framework"

adapter_fixture="$scratch_root/adapter-cross-dependency"
prepare_fixture "$adapter_fixture"
printf '\ndependencies {\n    testImplementation(project(":woge-spring-webflux"))\n}\n' \
  >>"$adapter_fixture/adapters/woge-spring-mvc/build.gradle.kts"
expect_rejection "$adapter_fixture" "woge-spring-mvc must not depend on woge-spring-webflux"

if (( failure_count > 0 )); then
  printf 'Module-boundary tests failed with %d problem(s).\n' "$failure_count" >&2
  exit 1
fi

printf 'Module-boundary tests passed.\n'
