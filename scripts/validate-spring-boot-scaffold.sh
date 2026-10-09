#!/usr/bin/env bash

set -u

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
scaffold_root="$repository_root/scaffolds/spring-boot"
properties="$scaffold_root/gradle.properties"
failure_count=0

fail() {
  printf 'Spring Boot scaffold validation: %s\n' "$1" >&2
  failure_count=$((failure_count + 1))
}

property_value() {
  local file=$1
  local name=$2
  sed -nE "s/^${name}=//p" "$file"
}

catalog_version() {
  local name=$1
  sed -nE "s/^${name} = \"([^\"]+)\"/\1/p" "$repository_root/gradle/libs.versions.toml"
}

[[ "$(property_value "$properties" wogeVersion)" == "$(property_value "$repository_root/gradle.properties" wogeVersion)" ]] ||
  fail "wogeVersion differs from the root build"
[[ "$(property_value "$properties" kotlinVersion)" == "$(catalog_version kotlin)" ]] ||
  fail "kotlinVersion differs from the version catalog"
[[ "$(property_value "$properties" kspVersion)" == "$(catalog_version ksp)" ]] ||
  fail "kspVersion differs from the version catalog"
[[ "$(property_value "$properties" springBootVersion)" == "$(catalog_version springBoot)" ]] ||
  fail "springBootVersion differs from the version catalog"
[[ "$(property_value "$properties" wogeSpringAdapter)" == "webflux" ]] ||
  fail "wogeSpringAdapter must explicitly select the WebFlux default"
[[ "$(property_value "$properties" buildJdk)" == "21" ]] || fail "buildJdk must remain 21"
[[ "$(property_value "$properties" jvmTarget)" == "17" ]] || fail "jvmTarget must remain 17"
[[ "$(property_value "$scaffold_root/scaffold.properties" schemaVersion)" == "1" ]] ||
  fail "unsupported scaffold schemaVersion"
[[ "$(property_value "$scaffold_root/scaffold.properties" scaffoldId)" == "spring-boot" ]] ||
  fail "scaffoldId must remain spring-boot"
[[ "$(property_value "$scaffold_root/scaffold.properties" scaffoldVersion)" == "0.3.0" ]] ||
  fail "scaffoldVersion must remain explicitly versioned"
[[ "$(property_value "$scaffold_root/scaffold.properties" defaultHost)" == "webflux" ]] ||
  fail "defaultHost must remain webflux"
[[ "$(property_value "$scaffold_root/scaffold.properties" generatedSources)" == \
  "build/generated/ksp/main/kotlin" ]] || fail "generatedSources ownership changed"

for required_path in \
  build.gradle.kts \
  settings.gradle.kts \
  scaffold.properties \
  src/main/kotlin/example/woge/Application.kt \
  src/main/kotlin/example/woge/HomePage.kt \
  src/webflux/kotlin/example/woge/WebFluxRoutes.kt \
  src/mvc/kotlin/example/woge/MvcRoutes.kt \
  src/main/resources/static/styles.css \
  src/test/kotlin/example/woge/ApplicationTest.kt \
  browser-tests/no-javascript.spec.mjs; do
  [[ -f "$scaffold_root/$required_path" ]] || fail "missing $required_path"
done

generated_guidance=$(mktemp)
trap 'rm -f "$generated_guidance"' EXIT
if ! "$repository_root/scripts/generate-spring-boot-agent-guidance.sh" "$generated_guidance"; then
  fail "AGENTS.md could not be generated from canonical metadata"
elif ! cmp -s "$generated_guidance" "$scaffold_root/AGENTS.md"; then
  fail "AGENTS.md is stale; run scripts/generate-spring-boot-agent-guidance.sh scaffolds/spring-boot/AGENTS.md"
fi

if grep -n -i -E '(tailwind|vite|io\.ktor|woge-ktor)' \
  "$scaffold_root/build.gradle.kts" "$scaffold_root/settings.gradle.kts" "$scaffold_root/package.json" >/dev/null; then
  fail "default dependencies must not install Tailwind, Vite or Ktor"
fi

if (( failure_count > 0 )); then
  printf 'Spring Boot scaffold validation failed with %d problem(s).\n' "$failure_count" >&2
  exit 1
fi

printf 'Spring Boot scaffold validation passed.\n'
