#!/usr/bin/env bash

# Builds the external Vite example against freshly published Woge artifacts. Production: `vite build`
# output is content-hashed into the boot jar and a second build is up to date. Development: `wogeDev`
# starts the Vite dev server, pages load modules from it, frontend edits do not rebuild Kotlin, and
# stopping the session frees both ports.

set -eu

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
example_root="$repository_root/examples/vite-spring-boot"

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
fixture_root="$scratch_root/application"
log_file="$scratch_root/woge-dev.log"
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
  printf 'Vite example failed: %s\n' "$1" >&2
  [[ -f "$log_file" ]] && { printf -- '--- session log ---\n' >&2; cat "$log_file" >&2; }
  exit 1
}

free_port() {
  python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()'
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

mkdir -p "$fixture_root/gradle/wrapper"
(cd "$example_root" && tar --exclude=./node_modules --exclude=./build --exclude=./.gradle --exclude=./.woge -cf - .) \
  | (cd "$fixture_root" && tar -xf -)
cp "$repository_root/gradlew" "$fixture_root/gradlew"
cp "$repository_root/gradle/wrapper/gradle-wrapper.jar" "$repository_root/gradle/wrapper/gradle-wrapper.properties" \
  "$fixture_root/gradle/wrapper/"
chmod +x "$fixture_root/gradlew"

gradle() {
  "$fixture_root/gradlew" --project-dir "$fixture_root" --no-daemon --console=plain \
    "-PwogeRepository=$published_repository" "$@"
}

(cd "$fixture_root" && npm ci --ignore-scripts --no-audit --no-fund --loglevel=error)
gradle --quiet bootJar

# Production: Vite's output joins the content-hashed asset tree, with relative chunk imports.
jar=$(ls "$fixture_root"/build/libs/*.jar | grep -v -- '-plain.jar$')
for file in vite/main.js vite/main.css vite/chunks/chart.js; do
  unzip -Z1 "$jar" | grep -Eq "^META-INF/woge/assets/[0-9a-f]{64}/$file\$" || fail "the boot jar lacks hashed $file"
done
main_js=$(unzip -Z1 "$jar" | grep -E '^META-INF/woge/assets/[0-9a-f]{64}/vite/main\.js$')
unzip -p "$jar" "$main_js" | grep -Fq './chunks/chart.js' || fail 'main.js does not import its chunk relatively'
if unzip -Z1 "$jar" | grep -Eq '\.map$'; then
  fail 'source maps are in the jar although they are off by default'
fi

# Nothing changed, so the second build does not run Vite again.
gradle wogeVite | grep -Eq '^> Task :wogeVite (UP-TO-DATE|FROM-CACHE)$' || fail 'wogeVite ran again without changes'

# Development: wogeDev runs the Vite dev server next to the application.
port=$(free_port)
vite_port=$(free_port)
sed -i.bak "s/devPort = 5173/devPort = $vite_port/" "$fixture_root/build.gradle.kts"
gradle wogeDev "--port=$port" >"$log_file" 2>&1 &
gradle_pid=$!

wait_for_log '^\[woge\] Ready:' 1 300
html=$(curl --silent --show-error --max-time 10 "http://127.0.0.1:$port/")
grep -Fq "src=\"http://127.0.0.1:$vite_port/@vite/client\"" <<<"$html" || fail 'the page does not load the Vite client'
grep -Fq "src=\"http://127.0.0.1:$vite_port/main.ts\"" <<<"$html" || fail 'the page does not load main.ts from Vite'
curl --silent --fail --max-time 10 -H "Origin: http://localhost:$port" -D "$scratch_root/headers" \
  "http://127.0.0.1:$vite_port/main.ts" >/dev/null || fail 'the Vite dev server does not serve main.ts'
grep -Fiq "access-control-allow-origin: http://localhost:$port" "$scratch_root/headers" ||
  fail 'the Vite dev server does not allow the application origin'

# A frontend edit is Vite's job: Vite serves it right away and Woge does not rebuild Kotlin.
printf '\nconsole.log("edited by the smoke test");\n' >>"$fixture_root/src/main/frontend/main.ts"
edited=""
for _ in $(seq 1 20); do
  if curl --silent --max-time 10 "http://127.0.0.1:$vite_port/main.ts" | grep -Fq 'edited by the smoke test'; then
    edited=yes
    break
  fi
  sleep 0.5
done
[[ -n "$edited" ]] || fail 'Vite did not serve the edited module'
sleep 2
(( $(grep -c '^\[woge\] Ready:' "$log_file") == 1 )) || fail 'a frontend edit rebuilt the Kotlin application'

stop_session
for checked in "$port" "$vite_port"; do
  if curl --silent --max-time 2 "http://127.0.0.1:$checked/" >/dev/null; then
    fail "port $checked still answers after the session stopped"
  fi
done

printf 'Vite example built, hashed and served in development.\n'
