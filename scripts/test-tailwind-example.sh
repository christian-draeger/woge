#!/usr/bin/env bash

# Builds the external Tailwind example against freshly published Woge artifacts and checks that
# the stylesheet contains the classes used in Kotlin, is content-hashed for production, and that
# class names built at runtime fail the build with a file and line.

set -eu

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
example_root="$repository_root/examples/tailwind-spring-boot"

if (( $# != 1 )); then
  printf 'Usage: %s LOCAL_MAVEN_REPOSITORY\n' "$0" >&2
  exit 2
fi

published_repository=$1
[[ -d "$published_repository" ]] || {
  printf 'Missing Woge Maven repository: %s\n' "$published_repository" >&2
  exit 2
}

scratch_root=$(mktemp -d)
trap 'rm -rf "$scratch_root"' EXIT
fixture_root="$scratch_root/application"

mkdir -p "$fixture_root/gradle/wrapper"
(cd "$example_root" && tar --exclude=./node_modules --exclude=./build --exclude=./.gradle --exclude=./.woge -cf - .) \
  | (cd "$fixture_root" && tar -xf -)
cp "$repository_root/gradlew" "$fixture_root/gradlew"
cp "$repository_root/gradle/wrapper/gradle-wrapper.jar" "$repository_root/gradle/wrapper/gradle-wrapper.properties" \
  "$fixture_root/gradle/wrapper/"
chmod +x "$fixture_root/gradlew"

gradle() {
  "$fixture_root/gradlew" --project-dir "$fixture_root" --no-daemon --console=plain --quiet \
    "-PwogeRepository=$published_repository" "$@"
}

(cd "$fixture_root" && npm ci --ignore-scripts --no-audit --no-fund --loglevel=error)
gradle bootJar

stylesheet="$fixture_root/build/resources/main/static/tailwind.css"
[[ -s "$stylesheet" ]] || { printf 'Missing generated stylesheet: %s\n' "$stylesheet" >&2; exit 1; }
for class in '.bg-ok-soft' '.text-danger' '.rounded-lg' '.max-w-xl' '.motion-safe\:transition' 'var(--woge-surface)' \
  '.cursor-pointer' '.backdrop\:bg-black\/50'; do
  grep -Fq -- "$class" "$stylesheet" || { printf 'Generated stylesheet lacks %s\n' "$class" >&2; exit 1; }
done
if grep -Fq '.bg-sky-500' "$stylesheet"; then
  printf 'Generated stylesheet contains a class that no source uses.\n' >&2
  exit 1
fi

manifest="$fixture_root/build/generated/woge-assets/resources/META-INF/woge/assets.properties"
grep -Eq '^asset\./tailwind\.css=/_woge/assets/[0-9a-f]{64}/tailwind\.css$' "$manifest" || {
  printf 'The production asset manifest does not hash /tailwind.css.\n' >&2
  exit 1
}
unzip -l "$fixture_root"/build/libs/*.jar | grep -Eq 'META-INF/woge/assets/[0-9a-f]{64}/tailwind\.css' || {
  printf 'The boot jar lacks the hashed Tailwind stylesheet.\n' >&2
  exit 1
}

# Ordinary CSS next to Tailwind is never rewritten.
site_css=$(unzip -Z1 "$fixture_root"/build/libs/*.jar | grep -E 'META-INF/woge/assets/[0-9a-f]{64}/site\.css$')
unzip -p "$fixture_root"/build/libs/*.jar "$site_css" | cmp - "$example_root/src/main/resources/static/site.css" || {
  printf 'Plain CSS changed on its way into the boot jar.\n' >&2
  exit 1
}

# A new class in Kotlin reaches the stylesheet on the next build.
sed -i.bak 's/text-3xl font-bold/text-4xl font-bold/' "$fixture_root/src/main/kotlin/example/tailwind/StatusPage.kt"
gradle classes
grep -Fq '.text-4xl' "$stylesheet" || { printf 'A new Kotlin class did not reach the stylesheet.\n' >&2; exit 1; }

# Class names assembled at runtime are invisible to Tailwind and fail with file and line.
cat > "$fixture_root/src/main/kotlin/example/tailwind/Dynamic.kt" <<'KOTLIN'
package example.tailwind

internal fun dynamicBadge(tone: Tone): String =
    "bg-${tone.name.lowercase()}-soft"
KOTLIN
if gradle classes > "$scratch_root/dynamic.log" 2>&1; then
  printf 'A class name built at runtime unexpectedly passed the Tailwind check.\n' >&2
  exit 1
fi
grep -Fq 'src/main/kotlin/example/tailwind/Dynamic.kt:4' "$scratch_root/dynamic.log" || {
  cat "$scratch_root/dynamic.log" >&2
  printf 'The dynamic class error does not name the file and line.\n' >&2
  exit 1
}

printf 'Tailwind example built, hashed and checked.\n'
