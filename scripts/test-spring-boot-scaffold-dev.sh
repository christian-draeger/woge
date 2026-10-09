#!/usr/bin/env bash

# Runs `./gradlew wogeDev` in a fresh Spring Boot scaffold and checks the edit loop:
# start, edit, compile error (old version keeps serving), fix, and clean shutdown.

set -eu

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

if (( $# < 1 || $# > 2 )); then
  printf 'Usage: %s LOCAL_MAVEN_REPOSITORY [webflux|mvc]\n' "$0" >&2
  exit 2
fi

published_repository=$1
adapter=${2:-webflux}
[[ -d "$published_repository" ]] || {
  printf 'Missing scaffold Maven repository: %s\n' "$published_repository" >&2
  exit 2
}

scratch_root=$(mktemp -d)
fixture_root="$scratch_root/app"
log_file="$scratch_root/woge-dev.log"
page_file="$fixture_root/src/main/kotlin/example/woge/HomePage.kt"
gradle_pid=""

stop_session() {
  local pids
  pids=$(pgrep -f "$fixture_root/build/woge-dev/session.properties" || true)
  for pid in $pids; do
    kill "$pid" 2>/dev/null || true
  done
  if [[ -n "$gradle_pid" ]]; then
    for _ in $(seq 1 30); do
      kill -0 "$gradle_pid" 2>/dev/null || break
      sleep 1
    done
    kill "$gradle_pid" 2>/dev/null || true
  fi
}

cleanup() {
  stop_session
  rm -rf "$scratch_root"
}
trap cleanup EXIT

fail() {
  printf 'wogeDev smoke failed: %s\n--- session log ---\n' "$1" >&2
  cat "$log_file" >&2 || true
  exit 1
}

wait_for_log() {
  local pattern=$1 count=$2 seconds=$3
  for _ in $(seq 1 "$seconds"); do
    if (( $(grep -c -- "$pattern" "$log_file" || true) >= count )); then
      return 0
    fi
    sleep 1
  done
  fail "timed out waiting for '$pattern' (#$count)"
}

page() {
  curl --silent --show-error --max-time 10 "http://127.0.0.1:$port/"
}

port=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')

"$repository_root/scripts/materialize-spring-boot-scaffold.sh" "$fixture_root" "$adapter"

"$fixture_root/gradlew" \
  --project-dir "$fixture_root" \
  --no-daemon \
  --console=plain \
  "-PwogeRepository=$published_repository" \
  wogeDev "--port=$port" >"$log_file" 2>&1 &
gradle_pid=$!

wait_for_log '^\[woge\] Ready:' 1 300
html=$(page)
grep -Fq '<h1>Hello from Woge</h1>' <<<"$html" || fail 'first page is missing the heading'
grep -Fq 'name="woge-development"' <<<"$html" || fail 'development client meta tag is missing'
grep -Fq '/client.js' <<<"$html" || fail 'development client script is missing'

sed -i.bak 's/Hello from Woge/Edited by wogeDev/g' "$page_file"
wait_for_log '^\[woge\] Ready:' 2 120
grep -Fq '<h1>Edited by wogeDev</h1>' <<<"$(page)" || fail 'edit did not reach the browser'

printf '\nval brokenOnPurpose: Int = "not a number"\n' >>"$page_file"
wait_for_log '^\[woge\] Build #[0-9]* failed' 1 120
grep -q '^\[woge\]   src/main/kotlin/example/woge/HomePage.kt:[0-9]*:[0-9]* ' "$log_file" ||
  fail 'compile error has no source location'
grep -Fq '<h1>Edited by wogeDev</h1>' <<<"$(page)" || fail 'last working version stopped serving'

sed -i.bak '/brokenOnPurpose/d' "$page_file"
wait_for_log '^\[woge\] Ready:' 3 120
grep -Fq '<h1>Edited by wogeDev</h1>' <<<"$(page)" || fail 'fixed version is not served'

stop_session
if curl --silent --max-time 2 "http://127.0.0.1:$port/" >/dev/null; then
  fail 'application still answers after the session stopped'
fi

printf 'wogeDev edit loop passed for %s.\n' "$adapter"
