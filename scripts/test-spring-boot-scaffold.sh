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
  if [[ "$adapter" == webflux ]]; then
    "$fixture_root/gradlew" \
      --project-dir "$fixture_root" \
      --no-daemon \
      --stacktrace \
      check \
      -PwogeSpringAdapter=mvc \
      "-PwogeRepository=$published_repository"
    grep -Fq -- '- Selected host: `webflux`' "$fixture_root/AGENTS.md"
  fi
done

# Incremental KSP output must forget deleted descriptors, and clean builds must be byte-stable.
manifest="$fixture_root/.woge/manifest.json"
cp "$manifest" "$scratch_root/manifest.json"
cat > "$fixture_root/src/main/kotlin/example/woge/TemporaryInput.kt" <<'KOTLIN'
package example.woge.temporary
import dev.woge.host.WogeRoute
@WogeRoute("/temporary") data object TemporaryInput
KOTLIN
"$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  wogeManifest "-PwogeRepository=$published_repository"
grep -Fq 'TemporaryRoute' "$manifest"
rm "$fixture_root/src/main/kotlin/example/woge/TemporaryInput.kt"
"$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  wogeManifest "-PwogeRepository=$published_repository"
cmp "$scratch_root/manifest.json" "$manifest"
"$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  clean "-PwogeRepository=$published_repository"
[[ ! -e "$manifest" ]]
"$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  wogeManifest "-PwogeRepository=$published_repository"
cmp "$scratch_root/manifest.json" "$manifest"
# A failed compile must remove the previous success-shaped manifest, rather than silently keep it.
printf 'package example.woge\nthis is not Kotlin\n' > "$fixture_root/src/main/kotlin/example/woge/Broken.kt"
if "$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  wogeManifest "-PwogeRepository=$published_repository" > "$scratch_root/expected-error.log" 2>&1; then
  printf 'The intentionally broken scaffold unexpectedly compiled.\n' >&2
  exit 1
fi
[[ ! -e "$manifest" ]]
rm "$fixture_root/src/main/kotlin/example/woge/Broken.kt"
"$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  wogeManifest "-PwogeRepository=$published_repository"
cmp "$scratch_root/manifest.json" "$manifest"

# Human and machine-readable workflow help come from the same list in the Gradle plugin.
workflow=$("$fixture_root/gradlew" --project-dir "$fixture_root" --quiet --console=plain \
  wogeTasks --format=json "-PwogeRepository=$published_repository")
python3 -c '
import json, sys
tasks = {entry["task"] for entry in json.loads(sys.argv[1])["tasks"]}
missing = {"wogeDev", "check", "verifyWogeProductionArtifact", "bootJar", "wogeManifest"} - tasks
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
if unzip -Z1 "$boot_jar" | grep -E '(^|/)(\.woge/manifest\.json|META-INF/woge/manifest\.json)$'; then
  printf 'The application manifest must not be packaged by default.\n' >&2
  exit 1
fi
asset_jar=$(unzip -Z1 "$boot_jar" | grep -E '^BOOT-INF/lib/woge-fallback-client-assets-.*\.jar$')
unzip -p "$boot_jar" "$asset_jar" > "$scratch_root/assets.jar"
unzip -Z1 "$scratch_root/assets.jar" | grep -Fqx 'static/assets/woge/index.js'

printf 'External Spring Boot scaffold passed for WebFlux, MVC and temporary MVC override, including production artifacts.\n'
