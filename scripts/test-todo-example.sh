#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
if (( $# != 1 )); then
  printf 'Usage: %s LOCAL_MAVEN_REPOSITORY\n' "$0" >&2
  exit 2
fi
published_repository=$1
scratch_root=$(mktemp -d)
trap 'rm -rf "$scratch_root"' EXIT
fixture_root="$scratch_root/todo"
mkdir -p "$fixture_root"
(cd "$repository_root/examples/todo-spring-boot" && tar \
  --exclude=./build --exclude=./.gradle --exclude=./.woge --exclude=./node_modules \
  --exclude=./test-results --exclude=./playwright-report -cf - .) | (cd "$fixture_root" && tar -xf -)
"$fixture_root/gradlew" --project-dir "$fixture_root" --no-daemon --console=plain \
  "-PwogeRepository=$published_repository" "-PwogeVersion=${WOGE_TEST_VERSION:-0.1.0-SNAPSHOT}" check bootJar
printf 'External Todo application passed.\n'
