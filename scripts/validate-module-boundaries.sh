#!/usr/bin/env bash

set -u

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
manifest="$repository_root/config/architecture/module-boundaries.tsv"
failure_count=0

fail() {
  printf 'Module-boundary validation: %s\n' "$1" >&2
  failure_count=$((failure_count + 1))
}

if [[ ! -f "$manifest" ]]; then
  printf 'Module-boundary validation: missing %s\n' "$manifest" >&2
  exit 1
fi

records=$(mktemp)
edges=$(mktemp)
trap 'rm -f "$records" "$edges"' EXIT

grep -v '^[[:space:]]*#' "$manifest" | grep -v '^[[:space:]]*$' > "$records"
[[ -s "$records" ]] || fail "manifest has no module rows"

if awk -F '\t' 'NF != 6 { exit 1 }' "$records"; then
  :
else
  fail "every manifest row must have six tab-separated columns"
fi

duplicates=$(cut -f1 "$records" | sort | uniq -d)
[[ -z "$duplicates" ]] || fail "duplicate modules: $(printf '%s' "$duplicates" | tr '\n' ' ')"

duplicate_paths=$(cut -f4 "$records" | sort | uniq -d)
[[ -z "$duplicate_paths" ]] || fail "duplicate module paths: $(printf '%s' "$duplicate_paths" | tr '\n' ' ')"

required_modules=(
  woge-core
  woge-protocol
  woge-host-spi
  woge-server-runtime
  woge-adapter-tck
  woge-spring-mvc
  woge-spring-webflux
  woge-ktor
  woge-spring-boot-autoconfigure
  woge-spring-boot-starter
)

for required_module in "${required_modules[@]}"; do
  if ! awk -F '\t' -v name="$required_module" '$1 == name { found = 1 } END { exit !found }' "$records"; then
    fail "missing required initial module '$required_module'"
  fi
done

role_allows() {
  case "$1:$2" in
    protocol:foundation|port:foundation|port:protocol|runtime:foundation|runtime:protocol|runtime:port) return 0 ;;
    adapter:foundation|adapter:protocol|adapter:port|adapter:runtime) return 0 ;;
    test-support:foundation|test-support:protocol|test-support:port|test-support:runtime) return 0 ;;
    integration:foundation|integration:protocol|integration:port|integration:runtime|integration:adapter|integration:integration) return 0 ;;
    *) return 1 ;;
  esac
}

while IFS=$'\t' read -r module role exposure source_path dependencies optional_dependencies; do
  case "$role" in
    foundation|protocol|port|runtime|adapter|integration|test-support) ;;
    *) fail "$module has unknown role '$role'" ;;
  esac

  case "$exposure" in
    public|internal|support) ;;
    *) fail "$module has unknown exposure '$exposure'" ;;
  esac

  all_dependencies="$dependencies,$optional_dependencies"
  all_dependencies=${all_dependencies//-,/}
  all_dependencies=${all_dependencies//,-/}
  [[ "$all_dependencies" == "-" ]] && continue

  IFS=',' read -r -a dependency_list <<< "$all_dependencies"
  for dependency in "${dependency_list[@]}"; do
    dependency_record=$(awk -F '\t' -v name="$dependency" '$1 == name { print; exit }' "$records")
    if [[ -z "$dependency_record" ]]; then
      fail "$module depends on unknown module '$dependency'"
      continue
    fi

    dependency_role=$(printf '%s\n' "$dependency_record" | cut -f2)
    if ! role_allows "$role" "$dependency_role"; then
      fail "$module ($role) may not depend on $dependency ($dependency_role)"
    fi
    printf '%s %s\n' "$module" "$dependency" >> "$edges"
  done
done < "$records"

if [[ -s "$edges" ]] && ! tsort "$edges" >/dev/null 2>&1; then
  fail "the declared project dependencies contain a cycle"
fi

for module in woge-core woge-protocol woge-host-spi woge-server-runtime; do
  source_path=$(awk -F '\t' -v name="$module" '$1 == name { print $4; exit }' "$records")
  [[ -n "$source_path" ]] || continue
  [[ -d "$repository_root/$source_path" ]] || continue

  forbidden_imports='^[[:space:]]*import[[:space:]]+(org\.springframework|reactor\.|jakarta\.servlet|javax\.servlet|io\.ktor)'
  if command -v rg >/dev/null 2>&1; then
    matches=$(rg -n --glob '*.kt' "$forbidden_imports" "$repository_root/$source_path" || true)
  else
    matches=$(grep -R -n -E --include='*.kt' "$forbidden_imports" "$repository_root/$source_path" || true)
  fi
  [[ -z "$matches" ]] || fail "$module imports a host framework:\n$matches"
done

if (( failure_count > 0 )); then
  printf 'Module-boundary validation failed with %d problem(s).\n' "$failure_count" >&2
  exit 1
fi

printf 'Module-boundary validation passed.\n'
