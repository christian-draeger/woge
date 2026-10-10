#!/usr/bin/env bash

# Runs `./gradlew wogeDev` in a fresh Ktor application and checks the edit loop:
# start, development client, edit (full restart), compile error (old version keeps serving), fix,
# a new typed route (KSP) and clean shutdown.

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
fixture_root="$scratch_root/app"
log_file="$scratch_root/woge-dev.log"
source_root="$fixture_root/src/main/kotlin/example/woge"
page_file="$source_root/HomePage.kt"
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
  printf 'Ktor wogeDev smoke failed: %s\n--- session log ---\n' "$1" >&2
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
  curl --silent --show-error --max-time 10 "http://127.0.0.1:$port$1"
}

property() {
  grep "^$1=" "$repository_root/scaffolds/spring-boot/gradle.properties" | cut -d= -f2
}

ktor_version=$(sed -n 's/^ktor = "\(.*\)"$/\1/p' "$repository_root/gradle/libs.versions.toml")
[[ -n "$ktor_version" ]] || fail 'could not read the Ktor version'
port=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')

mkdir -p "$fixture_root/gradle/wrapper" "$source_root"
cp "$repository_root/gradlew" "$fixture_root/gradlew"
cp "$repository_root/gradle/wrapper/gradle-wrapper.jar" "$repository_root/gradle/wrapper/gradle-wrapper.properties" \
  "$fixture_root/gradle/wrapper/"

cat >"$fixture_root/gradle.properties" <<PROPERTIES
wogeVersion=$(property wogeVersion)
kotlinVersion=$(property kotlinVersion)
kspVersion=$(property kspVersion)
ktorVersion=$ktor_version
org.gradle.configuration-cache=true
PROPERTIES

cat >"$fixture_root/settings.gradle.kts" <<'KOTLIN'
rootProject.name = "woge-ktor-application"

pluginManagement {
    val kotlinVersion: String by settings
    val kspVersion: String by settings
    val wogeVersion: String by settings

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("com.google.devtools.ksp") version kspVersion
        id("dev.woge.application") version wogeVersion
    }
    repositories {
        maven { url = uri(providers.gradleProperty("wogeRepository").get()) }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("wogeRepository").get()) }
        mavenCentral()
    }
}
KOTLIN

cat >"$fixture_root/build.gradle.kts" <<'KOTLIN'
plugins {
    application
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("dev.woge.application")
}

val wogeVersion: String by project
val ktorVersion: String by project

kotlin { jvmToolchain(21) }

application { mainClass = "example.woge.ApplicationKt" }

dependencies {
    implementation("dev.woge:woge-ktor:$wogeVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
}
KOTLIN

cat >"$source_root/Application.kt" <<'KOTLIN'
package example.woge

import dev.woge.ktor.WogeKtorHandlers
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port) {
        val page = WogeKtorHandlers().page(HomePage(), HomeRoute)
        routing { get(HomeRoute.path) { page.handle(call) } }
    }.start(wait = true)
}
KOTLIN

cat >"$page_file" <<'KOTLIN'
package example.woge

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.title

@WogeRoute("/")
data object HomeInput

class HomePage : PageUseCase<HomeInput> {
    override suspend fun open(request: PageRequest<HomeInput>): PageResult =
        htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head { title("Ktor") }
                body { h1 { text("Hello from Ktor") } }
            }
        }
}
KOTLIN

"$fixture_root/gradlew" \
  --project-dir "$fixture_root" \
  --no-daemon \
  --console=plain \
  "-PwogeRepository=$published_repository" \
  wogeDev "--port=$port" >"$log_file" 2>&1 &
gradle_pid=$!

wait_for_log '^\[woge\] Ready:' 1 300
html=$(page /)
grep -Fq '<h1>Hello from Ktor</h1>' <<<"$html" || fail 'first page is missing the heading'
grep -Fq 'name="woge-development"' <<<"$html" || fail 'development client meta tag is missing'

sed -i.bak 's/Hello from Ktor/Edited by wogeDev/' "$page_file"
wait_for_log '^\[woge\] Ready:' 2 120
grep -Fq '<h1>Edited by wogeDev</h1>' <<<"$(page /)" || fail 'edit did not reach the browser'

printf '\nval brokenOnPurpose: Int = "not a number"\n' >>"$page_file"
wait_for_log '^\[woge\] Build #[0-9]* failed' 1 120
grep -q '^\[woge\]   src/main/kotlin/example/woge/HomePage.kt:[0-9]*:[0-9]* ' "$log_file" ||
  fail 'compile error has no source location'
grep -Fq '<h1>Edited by wogeDev</h1>' <<<"$(page /)" || fail 'last working version stopped serving'

sed -i.bak '/brokenOnPurpose/d' "$page_file"
wait_for_log '^\[woge\] Ready:' 3 120
grep -Fq '<h1>Edited by wogeDev</h1>' <<<"$(page /)" || fail 'fixed version is not served'

# A new typed route: KSP generates AboutRoute, which the application then serves.
cat >"$source_root/AboutPage.kt" <<'KOTLIN'
package example.woge

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.title

@WogeRoute("/about")
data object AboutInput

class AboutPage : PageUseCase<AboutInput> {
    override suspend fun open(request: PageRequest<AboutInput>): PageResult =
        htmlPage {
            doctype()
            html { head { title("About") }; body { h1 { text("About Ktor") } } }
        }
}
KOTLIN
perl -0pi -e 's/(        routing \{ get\(HomeRoute.path\) \{ page.handle\(call\) \} \}\n)/        val about = WogeKtorHandlers().page(AboutPage(), AboutRoute)\n        routing {\n            get(HomeRoute.path) { page.handle(call) }\n            get(AboutRoute.path) { about.handle(call) }\n        }\n/' \
  "$source_root/Application.kt"
grep -Fq 'AboutRoute.path' "$source_root/Application.kt" || fail 'could not add the route'
wait_for_log '^\[woge\] Ready:' 4 180
grep -Fq '<h1>About Ktor</h1>' <<<"$(page /about)" || fail 'new typed route is not served'

stop_session
if curl --silent --max-time 2 "http://127.0.0.1:$port/" >/dev/null; then
  fail 'application still answers after the session stopped'
fi

printf 'wogeDev edit loop passed for Ktor.\n'
