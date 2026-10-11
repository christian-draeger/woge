#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
./gradlew -p spikes/local-island-target :js-direct:jsProductionExecutableCompileSync :js-browser:jsProductionExecutableCompileSync :wasm:wasmJsProductionExecutableCompileSync --console=plain -q
node spikes/local-island-target/scripts/measure.mjs
