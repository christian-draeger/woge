#!/usr/bin/env bash

set -u

spike_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
gradle_wrapper="$spike_directory/../spring-html-htmx-baseline/gradlew"

"$gradle_wrapper" -p "$spike_directory" test
"$gradle_wrapper" -p "$spike_directory" run --quiet
