#!/usr/bin/env bash

set -euo pipefail

spike_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
system=$(uname -s)
architecture=$(uname -m)

case "$system/$architecture" in
  Darwin/arm64)
    asset="tailwindcss-macos-arm64"
    expected_sha256="cdf646702987a743464dff4d9c60fd4480d1c1e73dd819a9a67f1078815dce9d"
    ;;
  Darwin/x86_64)
    asset="tailwindcss-macos-x64"
    expected_sha256="7922e0953f2110c05976e3bf58f14e643d90427575e766b7d433f5f80cbee7e1"
    ;;
  Linux/x86_64)
    asset="tailwindcss-linux-x64"
    expected_sha256="dc61b3ac6b8c9ca874c0cc4c57b2409791a64c5540404ca5f5367360babc313a"
    ;;
  Linux/aarch64)
    asset="tailwindcss-linux-arm64"
    expected_sha256="55fd0b241214eff3de1e8ee4f22796662f2d2e7a49bcfca7477cfd0bac398195"
    ;;
  *)
    printf 'No pinned Tailwind standalone binary for %s/%s\n' "$system" "$architecture" >&2
    exit 2
    ;;
esac

binary_directory="$spike_directory/build/standalone/bin"
binary="$binary_directory/$asset"
download="$binary.download"
mkdir -p "$binary_directory"

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d ' ' -f 1
  else
    shasum -a 256 "$1" | cut -d ' ' -f 1
  fi
}

if [[ ! -f "$binary" ]]; then
  rm -f "$download"
  curl --fail --location --retry 3 \
    "https://github.com/tailwindlabs/tailwindcss/releases/download/v4.3.3/$asset" \
    --output "$download"
  if [[ $(sha256_file "$download") != "$expected_sha256" ]]; then
    rm -f "$download"
    printf 'Tailwind standalone checksum mismatch for downloaded %s\n' "$asset" >&2
    exit 1
  fi
  mv "$download" "$binary"
fi

actual_sha256=$(sha256_file "$binary")

if [[ "$actual_sha256" != "$expected_sha256" ]]; then
  printf 'Tailwind standalone checksum mismatch for %s\n' "$asset" >&2
  exit 1
fi

chmod +x "$binary"
(cd "$spike_directory" && TAILWIND_STANDALONE="$binary" node compare-standalone.mjs)
