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
trap 'rm -rf "$scratch_root"' EXIT

"$repository_root/scripts/validate-spring-boot-scaffold.sh"

for adapter in webflux mvc; do
  fixture_root="$scratch_root/$adapter"
  "$repository_root/scripts/materialize-spring-boot-scaffold.sh" "$fixture_root" "$adapter"
  grep -Fqx "wogeSpringAdapter=$adapter" "$fixture_root/gradle.properties"
  grep -Fq -- "- Selected host: \`$adapter\`" "$fixture_root/AGENTS.md"
  "$fixture_root/gradlew" \
    --project-dir "$fixture_root" \
    --no-daemon \
    --stacktrace \
    check \
    "-PwogeRepository=$published_repository"
done

# Human and machine-readable workflow help come from the same list in the Gradle plugin.
workflow=$("$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  wogeTasks --format=json "-PwogeRepository=$published_repository")
python3 -c '
import json, sys
tasks = {entry["task"] for entry in json.loads(sys.argv[1])["tasks"]}
missing = {"wogeDev", "check", "verifyWogeProductionArtifact", "bootJar"} - tasks
sys.exit(f"wogeTasks is missing {missing}" if missing else 0)
' "$workflow"
"$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  wogeTasks "-PwogeRepository=$published_repository" | grep -Fq './gradlew wogeDev'

# Consumers add the browser runtime as an ordinary dependency; it must resolve and contain the module.
cat >> "$fixture_root/build.gradle.kts" <<'GRADLE'

dependencies {
    runtimeOnly("dev.woge:woge-fallback-client-assets:$wogeVersion")
}
GRADLE
"$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  bootJar "-PwogeRepository=$published_repository"
boot_jar=$(ls "$fixture_root"/build/libs/*.jar | grep -v -- '-plain\.jar$')
asset_jar=$(unzip -Z1 "$boot_jar" | grep -E '^BOOT-INF/lib/woge-fallback-client-assets-.*\.jar$')
unzip -p "$boot_jar" "$asset_jar" > "$scratch_root/assets.jar"
unzip -Z1 "$scratch_root/assets.jar" | grep -Fqx 'static/assets/woge/index.js'

printf 'External Spring Boot scaffold passed for WebFlux and MVC, including the production artifact check.\n'
