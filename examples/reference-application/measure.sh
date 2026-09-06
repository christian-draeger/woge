#!/usr/bin/env bash
set -euo pipefail

reference_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$reference_root"

count_source_lines() {
    local source_path="$1"
    shift
    local files
    files=$(rg --files "$source_path" "$@")
    awk '
        NF && $0 !~ /^[[:space:]]*(\/\/|\/\*|\*|<!--|-->)/ { count++ }
        END { print count + 0 }
    ' $files
}

count_matches() {
    local pattern="$1"
    shift
    local matches
    matches=$(rg -o "$pattern" "$@" || true)
    printf '%s\n' "$matches" | awk 'NF { count++ } END { print count + 0 }'
}

shared_kotlin_loc=$(count_source_lines shared/src/main/kotlin -g '*.kt')
shared_web_assets_loc=$(count_source_lines shared/src/main/resources -g '*.css' -g '*.js')
mvc_host_kotlin_loc=$(count_source_lines spring-mvc/src/main/kotlin -g '*.kt')
webflux_host_kotlin_loc=$(count_source_lines spring-webflux/src/main/kotlin -g '*.kt')
ktor_host_kotlin_loc=$(count_source_lines ktor/src/main/kotlin -g '*.kt')
route_string_occurrences=$(
    count_matches '/projects/' \
        shared/src/main/kotlin \
        spring-mvc/src/main/kotlin \
        spring-webflux/src/main/kotlin \
        ktor/src/main/kotlin
)
framework_imports_in_shared=$(
    count_matches '^import (org\.springframework|io\.ktor|jakarta\.servlet|reactor\.)' shared/src/main/kotlin
)
application_owned_patch_selector_occurrences=$(
    count_matches "hx-target=|querySelector\\([\"']#" shared/src/main
)

printf 'shared_kotlin_loc=%s\n' "$shared_kotlin_loc"
printf 'shared_web_assets_loc=%s\n' "$shared_web_assets_loc"
printf 'mvc_host_kotlin_loc=%s\n' "$mvc_host_kotlin_loc"
printf 'webflux_host_kotlin_loc=%s\n' "$webflux_host_kotlin_loc"
printf 'ktor_host_kotlin_loc=%s\n' "$ktor_host_kotlin_loc"
printf 'route_string_occurrences=%s\n' "$route_string_occurrences"
printf 'framework_imports_in_shared=%s\n' "$framework_imports_in_shared"
printf 'application_owned_patch_selector_occurrences=%s\n' "$application_owned_patch_selector_occurrences"
