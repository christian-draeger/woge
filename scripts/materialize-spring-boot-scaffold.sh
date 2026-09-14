#!/usr/bin/env bash

set -eu

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
template_root="$repository_root/scaffolds/spring-boot"

if (( $# < 1 || $# > 2 )); then
  printf 'Usage: %s TARGET_DIRECTORY [webflux|mvc]\n' "$0" >&2
  exit 2
fi

target_directory=$1
selected_host=${2:-webflux}
case "$selected_host" in
  webflux|mvc) ;;
  *)
    printf 'Spring host must be webflux or mvc: %s\n' "$selected_host" >&2
    exit 2
    ;;
esac
if [[ -e "$target_directory" ]] && [[ -n "$(find "$target_directory" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  printf 'Scaffold target must be absent or empty: %s\n' "$target_directory" >&2
  exit 2
fi

mkdir -p "$target_directory/gradle/wrapper"
cp -R "$template_root/." "$target_directory/"
cp "$repository_root/gradlew" "$target_directory/gradlew"
cp "$repository_root/gradlew.bat" "$target_directory/gradlew.bat"
cp "$repository_root/gradle/wrapper/gradle-wrapper.jar" "$target_directory/gradle/wrapper/gradle-wrapper.jar"
cp "$repository_root/gradle/wrapper/gradle-wrapper.properties" "$target_directory/gradle/wrapper/gradle-wrapper.properties"
chmod +x "$target_directory/gradlew"

temporary_properties=$(mktemp "$target_directory/gradle.properties.tmp.XXXXXX")
awk -v selected_host="$selected_host" '
  /^wogeSpringAdapter=/ { print "wogeSpringAdapter=" selected_host; next }
  { print }
' "$target_directory/gradle.properties" > "$temporary_properties"
mv "$temporary_properties" "$target_directory/gradle.properties"
"$repository_root/scripts/generate-spring-boot-agent-guidance.sh" "$target_directory/AGENTS.md" "$target_directory"

printf 'Materialized Woge Spring Boot scaffold at %s\n' "$target_directory"
