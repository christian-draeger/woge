#!/usr/bin/env bash

# Runs `./gradlew wogeDev` in a fresh Spring Boot scaffold and checks the edit loop:
# start, stylesheet edit without restart, edit, compile error (old version keeps serving), fix, a new typed region (KSP), a rejected
# region declaration, incremental action registries, and clean shutdown.

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
region_file="$fixture_root/src/main/kotlin/example/woge/Status.kt"
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

ready_count() {
  grep -c '^\[woge\] Ready:' "$log_file" || true
}

wait_for_page() {
  local text=$1 seconds=$2
  for _ in $(seq 1 "$seconds"); do
    if page 2>/dev/null | grep -Fq -- "$text"; then
      return 0
    fi
    sleep 1
  done
  fail "timed out waiting for the page to contain '$text'"
}

port=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')

"$repository_root/scripts/materialize-spring-boot-scaffold.sh" "$fixture_root" "$adapter"

# A strict application policy: wogeDev must allow its own client origin without other changes.
policy_file="$fixture_root/src/main/kotlin/example/woge/StrictPolicy.kt"
if [[ "$adapter" == "mvc" ]]; then
  cat >"$policy_file" <<'KOTLIN'
package example.woge

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
public class StrictPolicy : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        response.setHeader("Content-Security-Policy", "default-src 'self'")
        chain.doFilter(request, response)
    }
}
KOTLIN
else
  cat >"$policy_file" <<'KOTLIN'
package example.woge

import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
public class StrictPolicy : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        exchange.response.headers.set("Content-Security-Policy", "default-src 'self'")
        return chain.filter(exchange)
    }
}
KOTLIN
fi

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
client_origin=$(grep -o 'src="http://127.0.0.1:[0-9]*/' <<<"$html" | head -1 | sed 's/^src="//; s|/$||')
[[ -n "$client_origin" ]] || fail 'development client origin is missing'
policy=$(curl --silent --max-time 10 --dump-header - --output /dev/null "http://127.0.0.1:$port/" |
  tr -d '\r' | grep -i '^content-security-policy:' || true)
grep -Fq "connect-src 'self' $client_origin" <<<"$policy" ||
  fail "strict CSP does not allow the development client: $policy"

# A stylesheet-only edit is served without restarting the application.
ready_before=$(ready_count)
printf '\n.woge-hot-css-smoke { color: rebeccapurple; }\n' >>"$fixture_root/src/main/resources/static/styles.css"
wait_for_log '^\[woge\] Stylesheets updated with build #' 1 120
curl --silent --max-time 10 "http://127.0.0.1:$port/styles.css" | grep -Fq 'woge-hot-css-smoke' ||
  fail 'stylesheet edit is not served'
(( $(ready_count) == ready_before )) || fail 'stylesheet edit restarted the application'

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

# Structural edit: a new @WogeRegion function makes KSP generate StatusRegion, which the page renders.
cat >"$region_file" <<'KOTLIN'
package example.woge

import dev.woge.host.WogeRegion
import dev.woge.html.HtmlWriter
import dev.woge.html.p

@WogeRegion
internal fun HtmlWriter.status(message: String) {
    p(attributes = { classes("status") }) { text(message) }
}
KOTLIN
perl -0pi -e 's/(( *)h1 \{ text\("Edited by wogeDev"\) \}\n)/$1$2StatusRegion.render(this, "Typed region ready")\n/' "$page_file"
grep -Fq 'StatusRegion.render' "$page_file" || fail 'could not add the region to the page'
wait_for_page '<p class="status">Typed region ready</p>' 180

# A rejected declaration is reported at its source line with its stable rule ID.
sed -i.bak 's/status(message: String)/status(message: String, extra: Int)/' "$region_file"
wait_for_log '^\[woge\]   src/main/kotlin/example/woge/Status.kt:[0-9]*:[0-9]* WOGE-REF-' 1 120
grep -Fq 'Typed region ready' <<<"$(page)" || fail 'last working version stopped serving'

ready_before=$(ready_count)
sed -i.bak 's/status(message: String, extra: Int)/status(message: String)/' "$region_file"
wait_for_log '^\[woge\] Ready:' $((ready_before + 1)) 120
grep -Fq '<p class="status">Typed region ready</p>' <<<"$(page)" || fail 'repaired region is not served'

# Action registries must follow incremental additions, ID edits, duplicate failures and removals.
action_file="$fixture_root/src/main/kotlin/example/woge/SmokeAction.kt"
generated_actions="$fixture_root/build/generated/ksp/main/kotlin/example/woge"
ready_before=$(ready_count)
cat >"$action_file" <<'KOTLIN'
package example.woge

import dev.woge.host.*
import dev.woge.html.applicationUrl

internal data class SmokeCommand(val title: String)

@WogeAction("smoke-submit")
internal suspend fun smokeSubmit(command: SmokeCommand, context: RequestContext): PageResult =
    if (command.title.isBlank()) failure(FailureCategory.BAD_REQUEST, context.correlationId)
    else redirect(applicationUrl("/"))
KOTLIN
wait_for_log '^\[woge\] Ready:' $((ready_before + 1)) 120
grep -Fq 'smoke-submit' "$generated_actions/SmokeSubmitAction.kt" || fail 'action descriptor was not generated'
grep -Fq 'listOf(SmokeSubmitAction)' "$generated_actions/WogeActions.kt" || fail 'action registry is incomplete'

ready_before=$(ready_count)
sed -i.bak 's/smoke-submit/smoke-save/' "$action_file"
wait_for_log '^\[woge\] Ready:' $((ready_before + 1)) 120
grep -Fq 'smoke-save' "$generated_actions/SmokeSubmitAction.kt" || fail 'incremental action ID stayed stale'
if grep -Fq 'smoke-submit' "$generated_actions/SmokeSubmitAction.kt"; then
  fail 'previous action ID survived regeneration'
fi

printf '\n@WogeAction("smoke-save")\ninternal suspend fun duplicateSmoke(command: SmokeCommand, context: RequestContext): PageResult = smokeSubmit(command, context)\n' >>"$action_file"
wait_for_log '^\[woge\]   src/main/kotlin/example/woge/SmokeAction.kt:[0-9]*:[0-9]* WOGE-ACTION-007' 1 120
grep -Fq 'Typed region ready' <<<"$(page)" || fail 'duplicate action stopped the last working application'

ready_before=$(ready_count)
rm "$action_file"
wait_for_log '^\[woge\] Ready:' $((ready_before + 1)) 120
[[ ! -f "$generated_actions/SmokeSubmitAction.kt" && ! -f "$generated_actions/WogeActions.kt" ]] ||
  fail 'removed actions survived in incremental generated output'

stop_session
if curl --silent --max-time 2 "http://127.0.0.1:$port/" >/dev/null; then
  fail 'application still answers after the session stopped'
fi

printf 'wogeDev edit loop passed for %s.\n' "$adapter"
