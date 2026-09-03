#!/usr/bin/env bash
set -euo pipefail

baseline_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$baseline_root"

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

shared_kotlin_loc=$(count_source_lines shared/src/main/kotlin -g '*.kt')
templates_and_css_loc=$(count_source_lines shared/src/main/resources -g '*.html' -g '*.css')
mvc_host_kotlin_loc=$(count_source_lines spring-mvc/src/main/kotlin -g '*.kt')
webflux_host_kotlin_loc=$(count_source_lines spring-webflux/src/main/kotlin -g '*.kt')
route_string_occurrences=$(
    rg -o '/projects/' shared/src/main/resources spring-mvc/src/main/kotlin spring-webflux/src/main/kotlin |
        wc -l |
        tr -d ' '
)
htmx_target_selector_occurrences=$(
    rg -o 'hx-target="#[^"]+"' shared/src/main/resources/templates |
        wc -l |
        tr -d ' '
)
unique_htmx_target_selectors=$(
    rg -o 'hx-target="#[^"]+"' shared/src/main/resources/templates |
        sed 's/.*hx-target=//' |
        sort -u |
        wc -l |
        tr -d ' '
)

printf 'shared_kotlin_loc=%s\n' "$shared_kotlin_loc"
printf 'templates_and_css_loc=%s\n' "$templates_and_css_loc"
printf 'mvc_host_kotlin_loc=%s\n' "$mvc_host_kotlin_loc"
printf 'webflux_host_kotlin_loc=%s\n' "$webflux_host_kotlin_loc"
printf 'route_string_occurrences=%s\n' "$route_string_occurrences"
printf 'htmx_target_selector_occurrences=%s\n' "$htmx_target_selector_occurrences"
printf 'unique_htmx_target_selectors=%s\n' "$unique_htmx_target_selectors"
