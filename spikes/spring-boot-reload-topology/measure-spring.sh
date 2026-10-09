#!/usr/bin/env bash
set -euo pipefail

SPIKE_DIR=$(cd "$(dirname "$0")" && pwd)
WOGE_ROOT=$(cd "$SPIKE_DIR/../.." && pwd)
SAMPLES=${1:-1}
OUTPUT_FILE=${2:-$SPIKE_DIR/build/spring-measurements.tsv}
WORK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/woge-reload-topology.XXXXXX")
APP_PID=""

if ! [[ "$SAMPLES" =~ ^[1-9][0-9]*$ ]]; then
    echo "samples must be a positive integer" >&2
    exit 2
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"
printf 'topology\tscenario\tsample\tdetection_ms\tcompile_ms\tready_ms\ttotal_ms\tpid_before\tpid_after\n' > "$OUTPUT_FILE"

now_ms() {
    python3 -c 'import time; print(time.monotonic_ns() // 1000000)'
}

free_port() {
    python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()'
}

stop_app() {
    if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
        kill -TERM "$APP_PID" 2>/dev/null || true
        local deadline=$((SECONDS + 20))
        while kill -0 "$APP_PID" 2>/dev/null && (( SECONDS < deadline )); do
            sleep 0.1
        done
        if kill -0 "$APP_PID" 2>/dev/null; then
            kill -KILL "$APP_PID" 2>/dev/null || true
        fi
        wait "$APP_PID" 2>/dev/null || true
    fi
    APP_PID=""
}

cleanup() {
    set +e
    stop_app
    if [[ "${WOGE_RELOAD_KEEP_WORK:-false}" == "true" ]]; then
        printf 'Keeping reload spike work directory at %s\n' "$WORK_DIR" >&2
    else
        rm -rf "$WORK_DIR"
    fi
}
trap cleanup EXIT INT TERM

prepare_app() {
    local app_dir=$1
    "$WOGE_ROOT/scripts/materialize-spring-boot-scaffold.sh" "$app_dir" webflux
    printf '\nwogeRepository=%s\n' "$WOGE_ROOT/build/scaffold-maven-repository" >> "$app_dir/gradle.properties"
    printf '\nspring.devtools.restart.trigger-file=.reloadtrigger\nspring.devtools.livereload.enabled=false\n' \
        >> "$app_dir/src/main/resources/application.properties"
    : > "$app_dir/src/main/resources/.reloadtrigger"
    printf '\n:root { --woge-reload-probe: a; }\n' >> "$app_dir/src/main/resources/static/styles.css"
    cp "$SPIKE_DIR/fixtures/build.gradle.append.kts" "$app_dir/build.gradle.append.kts"
    awk 'FNR == NR { addition = addition $0 ORS; next } { print } END { printf "%s", addition }' \
        "$app_dir/build.gradle.append.kts" "$app_dir/build.gradle.kts" > "$app_dir/build.gradle.kts.tmp"
    mv "$app_dir/build.gradle.kts.tmp" "$app_dir/build.gradle.kts"
    rm "$app_dir/build.gradle.append.kts"
    cp "$SPIKE_DIR/fixtures/ProbeController.a.kt" \
        "$app_dir/src/main/kotlin/example/woge/ReloadProbeController.kt"
    mkdir -p "$app_dir/build/generated/sources/woge/main/kotlin/example/woge"
    cp "$SPIKE_DIR/fixtures/ReloadProbe.a.kt" \
        "$app_dir/build/generated/sources/woge/main/kotlin/example/woge/ReloadProbe.kt"
    build_app "$app_dir" classes writeReloadProbeRuntimeClasspath
}

build_app() {
    local app_dir=$1
    shift
    mkdir -p "$app_dir/build/reload-probe"
    if ! "$WOGE_ROOT/gradlew" -p "$app_dir" "$@" \
        --no-configuration-cache --console=plain > "$app_dir/build/reload-probe/last-build.log" 2>&1; then
        sed -n '1,260p' "$app_dir/build/reload-probe/last-build.log" >&2
        return 1
    fi
}

start_app() {
    local app_dir=$1
    local port=$2
    local devtools_enabled=$3
    local classpath
    mkdir -p "$app_dir/build/reload-probe"
    classpath=$(<"$app_dir/build/reload-probe/runtime-classpath.txt")
    java -Dspring.devtools.restart.enabled="$devtools_enabled" \
        -cp "$classpath" example.woge.ApplicationKt \
        --server.address=127.0.0.1 --server.port="$port" --spring.main.banner-mode=off \
        > "$app_dir/build/reload-probe/application.log" 2>&1 &
    APP_PID=$!
}

probe() {
    local port=$1
    curl --silent --show-error --max-time 2 "http://127.0.0.1:$port/__reload-probe"
}

wait_for_probe() {
    local port=$1
    local expected=$2
    local deadline=$((SECONDS + 35))
    while (( SECONDS < deadline )); do
        local response
        response=$(probe "$port" 2>/dev/null || true)
        if [[ "$response" == *"$expected"* ]]; then
            printf '%s' "$response"
            return 0
        fi
        sleep 0.05
    done
    echo "Timed out waiting for '$expected' on port $port" >&2
    return 1
}

response_pid() {
    local response=$1
    printf '%s' "${response##*pid=}"
}

record() {
    local topology=$1 scenario=$2 sample=$3 changed=$4 detected=$5 compile_finished=$6 ready=$7 before=$8 after=$9
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$topology" "$scenario" "$sample" \
        "$((detected - changed))" "$((compile_finished - detected))" "$((ready - compile_finished))" \
        "$((ready - changed))" \
        "$before" "$after" >> "$OUTPUT_FILE"
}

set_source() {
    local app_dir=$1 version=$2
    cp "$SPIKE_DIR/fixtures/ProbeController.$version.kt" \
        "$app_dir/src/main/kotlin/example/woge/ReloadProbeController.kt"
}

set_generated() {
    local app_dir=$1 version=$2
    cp "$SPIKE_DIR/fixtures/ReloadProbe.$version.kt" \
        "$app_dir/build/generated/sources/woge/main/kotlin/example/woge/ReloadProbe.kt"
}

trigger_devtools() {
    local app_dir=$1
    touch "$app_dir/build/resources/main/.reloadtrigger"
}

measure_css() {
    local topology=$1 app_dir=$2 port=$3 sample=$4 marker=$5 before_pid=$6
    local changed detected compile_finished ready response after_pid css_ready=false
    changed=$(now_ms)
    perl -0pi -e "s/--woge-reload-probe: [ab]/--woge-reload-probe: $marker/" \
        "$app_dir/src/main/resources/static/styles.css"
    detected=$(now_ms)
    build_app "$app_dir" processResources
    compile_finished=$(now_ms)
    local deadline=$((SECONDS + 15))
    while (( SECONDS < deadline )); do
        if curl --silent --show-error --max-time 2 "http://127.0.0.1:$port/styles.css" \
            | grep -q -- "--woge-reload-probe: $marker"; then
            css_ready=true
            break
        fi
        sleep 0.05
    done
    if [[ "$css_ready" != "true" ]]; then
        echo "Timed out waiting for CSS marker '$marker' on port $port" >&2
        return 1
    fi
    ready=$(now_ms)
    response=$(probe "$port")
    after_pid=$(response_pid "$response")
    [[ "$after_pid" == "$before_pid" ]]
    record "$topology" css "$sample" "$changed" "$detected" "$compile_finished" "$ready" \
        "$before_pid" "$after_pid"
}

run_devtools() {
    local app_dir="$WORK_DIR/devtools-app"
    local port response pid_before version marker changed detected compile_finished ready pid_after sample expected
    prepare_app "$app_dir"
    port=$(free_port)
    start_app "$app_dir" "$port" true
    response=$(wait_for_probe "$port" 'source-a|generated-object')
    pid_before=$(response_pid "$response")

    for ((sample = 1; sample <= SAMPLES; sample++)); do
        if (( sample % 2 == 1 )); then version=b; else version=a; fi
        expected="source-$version"
        changed=$(now_ms)
        set_source "$app_dir" "$version"
        detected=$(now_ms)
        build_app "$app_dir" classes
        compile_finished=$(now_ms)
        trigger_devtools "$app_dir"
        response=$(wait_for_probe "$port" "$expected")
        ready=$(now_ms)
        pid_after=$(response_pid "$response")
        [[ "$pid_after" == "$pid_before" ]]
        record devtools kotlin "$sample" "$changed" "$detected" "$compile_finished" "$ready" \
            "$pid_before" "$pid_after"
    done

    set_source "$app_dir" broken
    if build_app "$app_dir" classes 2>/dev/null; then
        echo "Expected the intentionally broken Kotlin build to fail" >&2
        return 1
    fi
    response=$(probe "$port")
    [[ "$(response_pid "$response")" == "$pid_before" ]]

    set_source "$app_dir" a
    build_app "$app_dir" classes
    trigger_devtools "$app_dir"
    wait_for_probe "$port" 'source-a' >/dev/null

    for ((sample = 1; sample <= SAMPLES; sample++)); do
        if (( sample % 2 == 1 )); then
            version=b
            expected=generated-companion
        else
            version=a
            expected=generated-object
        fi
        changed=$(now_ms)
        set_generated "$app_dir" "$version"
        detected=$(now_ms)
        build_app "$app_dir" classes
        compile_finished=$(now_ms)
        trigger_devtools "$app_dir"
        response=$(wait_for_probe "$port" "$expected")
        ready=$(now_ms)
        pid_after=$(response_pid "$response")
        [[ "$pid_after" == "$pid_before" ]]
        record devtools generated "$sample" "$changed" "$detected" "$compile_finished" "$ready" \
            "$pid_before" "$pid_after"
    done

    for ((sample = 1; sample <= SAMPLES; sample++)); do
        if (( sample % 2 == 1 )); then marker=b; else marker=a; fi
        measure_css devtools "$app_dir" "$port" "$sample" "$marker" "$pid_before"
    done
    stop_app
}

restart_managed_app() {
    local app_dir=$1 port=$2
    stop_app
    start_app "$app_dir" "$port" false
}

run_managed_child() {
    local app_dir="$WORK_DIR/managed-app"
    local port response pid_before version marker changed detected compile_finished ready pid_after sample expected
    prepare_app "$app_dir"
    port=$(free_port)
    start_app "$app_dir" "$port" false
    response=$(wait_for_probe "$port" 'source-a|generated-object')
    pid_before=$(response_pid "$response")

    for ((sample = 1; sample <= SAMPLES; sample++)); do
        if (( sample % 2 == 1 )); then version=b; else version=a; fi
        expected="source-$version"
        changed=$(now_ms)
        set_source "$app_dir" "$version"
        detected=$(now_ms)
        build_app "$app_dir" classes writeReloadProbeRuntimeClasspath
        compile_finished=$(now_ms)
        response=$(probe "$port")
        [[ "$(response_pid "$response")" == "$pid_before" ]]
        restart_managed_app "$app_dir" "$port"
        response=$(wait_for_probe "$port" "$expected")
        ready=$(now_ms)
        pid_after=$(response_pid "$response")
        [[ "$pid_after" != "$pid_before" ]]
        record managed-child kotlin "$sample" "$changed" "$detected" "$compile_finished" "$ready" \
            "$pid_before" "$pid_after"
        pid_before=$pid_after
    done

    set_source "$app_dir" broken
    if build_app "$app_dir" classes 2>/dev/null; then
        echo "Expected the intentionally broken Kotlin build to fail" >&2
        return 1
    fi
    response=$(probe "$port")
    [[ "$(response_pid "$response")" == "$pid_before" ]]

    set_source "$app_dir" a
    build_app "$app_dir" classes writeReloadProbeRuntimeClasspath
    restart_managed_app "$app_dir" "$port"
    response=$(wait_for_probe "$port" 'source-a')
    pid_before=$(response_pid "$response")

    for ((sample = 1; sample <= SAMPLES; sample++)); do
        if (( sample % 2 == 1 )); then
            version=b
            expected=generated-companion
        else
            version=a
            expected=generated-object
        fi
        changed=$(now_ms)
        set_generated "$app_dir" "$version"
        detected=$(now_ms)
        build_app "$app_dir" classes writeReloadProbeRuntimeClasspath
        compile_finished=$(now_ms)
        response=$(probe "$port")
        [[ "$(response_pid "$response")" == "$pid_before" ]]
        restart_managed_app "$app_dir" "$port"
        response=$(wait_for_probe "$port" "$expected")
        ready=$(now_ms)
        pid_after=$(response_pid "$response")
        [[ "$pid_after" != "$pid_before" ]]
        record managed-child generated "$sample" "$changed" "$detected" "$compile_finished" "$ready" \
            "$pid_before" "$pid_after"
        pid_before=$pid_after
    done

    for ((sample = 1; sample <= SAMPLES; sample++)); do
        if (( sample % 2 == 1 )); then marker=b; else marker=a; fi
        measure_css managed-child "$app_dir" "$port" "$sample" "$marker" "$pid_before"
    done
    stop_app

    python3 -c 'import socket,sys,time; s=socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 0); s.bind(("127.0.0.1", int(sys.argv[1]))); s.listen(); time.sleep(30)' \
        "$port" > "$app_dir/build/reload-probe/port-conflict.log" 2>&1 &
    local blocker_pid=$!
    sleep 0.3
    if ! kill -0 "$blocker_pid" 2>/dev/null; then
        echo "Port-conflict blocker failed to start" >&2
        return 1
    fi
    local classpath
    classpath=$(<"$app_dir/build/reload-probe/runtime-classpath.txt")
    java -Dspring.devtools.restart.enabled=false -cp "$classpath" example.woge.ApplicationKt \
        --server.address=127.0.0.1 --server.port="$port" --spring.main.banner-mode=off \
        > "$app_dir/build/reload-probe/conflicting-application.log" 2>&1 &
    local conflicting_pid=$!
    local conflict_deadline=$((SECONDS + 12))
    while kill -0 "$conflicting_pid" 2>/dev/null && (( SECONDS < conflict_deadline )); do
        sleep 0.1
    done
    if kill -0 "$conflicting_pid" 2>/dev/null; then
        kill -TERM "$conflicting_pid" 2>/dev/null || true
        wait "$conflicting_pid" 2>/dev/null || true
        echo "Expected a conflicting application port to fail" >&2
        kill -TERM "$blocker_pid" 2>/dev/null || true
        wait "$blocker_pid" 2>/dev/null || true
        return 1
    fi
    if wait "$conflicting_pid"; then
        echo "Expected a conflicting application port to exit unsuccessfully" >&2
        kill -TERM "$blocker_pid" 2>/dev/null || true
        wait "$blocker_pid" 2>/dev/null || true
        return 1
    fi
    kill -TERM "$blocker_pid" 2>/dev/null || true
    wait "$blocker_pid" 2>/dev/null || true
}

"$WOGE_ROOT/gradlew" publishScaffoldArtifacts --no-configuration-cache --console=plain
run_devtools
run_managed_child
printf 'Spring reload measurements written to %s\n' "$OUTPUT_FILE"
