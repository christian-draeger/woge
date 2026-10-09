#!/usr/bin/env bash
set -euo pipefail

SPIKE_DIR=$(cd "$(dirname "$0")" && pwd)

"$SPIKE_DIR/measure-spring.sh" 1 "$SPIKE_DIR/build/spring-measurements.tsv"
npm test --prefix "$SPIKE_DIR"

test -s "$SPIKE_DIR/build/spring-measurements.tsv"
test -s "$SPIKE_DIR/build/browser-measurements.json"

printf 'Spring Boot reload topology spike passed.\n'
