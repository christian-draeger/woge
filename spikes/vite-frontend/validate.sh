#!/usr/bin/env bash

set -euo pipefail

spike_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
gradle_wrapper="$spike_directory/../../gradlew"

(cd "$spike_directory" && npm test)
(cd "$spike_directory" && npm run measure)

WOGE_NODE=$(command -v node) "$gradle_wrapper" -p "$spike_directory/gradle-probe" --configuration-cache gradleOnlyAssets
WOGE_NODE=$(command -v node) "$gradle_wrapper" -p "$spike_directory/gradle-probe" --configuration-cache viteBuild

printf 'Vite frontend spike validation passed.\n'
