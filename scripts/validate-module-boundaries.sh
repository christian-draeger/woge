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
  woge-ui-headless
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
    ui:foundation) return 0 ;;
    protocol:foundation|port:foundation|port:protocol|runtime:foundation|runtime:protocol|runtime:port) return 0 ;;
    adapter:foundation|adapter:protocol|adapter:port|adapter:runtime) return 0 ;;
    test-support:foundation|test-support:protocol|test-support:port|test-support:runtime) return 0 ;;
    integration:foundation|integration:protocol|integration:port|integration:runtime|integration:adapter|integration:integration) return 0 ;;
    *) return 1 ;;
  esac
}

while IFS=$'\t' read -r module role exposure source_path dependencies optional_dependencies; do
  if [[ ! "$module" =~ ^woge-[a-z0-9]+(-[a-z0-9]+)*$ ]]; then
    fail "invalid module name '$module'"
  fi
  if [[ ! "$source_path" =~ ^(modules|adapters|integrations|testing)/${module}$ ]]; then
    fail "$module has non-canonical source path '$source_path'"
  fi

  case "$role" in
    foundation|ui|protocol|port|runtime|adapter|integration|test-support) ;;
    *) fail "$module has unknown role '$role'" ;;
  esac

  case "$exposure" in
    public|internal|support) ;;
    *) fail "$module has unknown exposure '$exposure'" ;;
  esac

  module_directory="$repository_root/$source_path"
  build_file="$module_directory/build.gradle.kts"
  if [[ ! -d "$module_directory" ]]; then
    fail "$module has no source directory at '$source_path'"
  elif [[ ! -f "$build_file" ]]; then
    fail "$module has no build.gradle.kts at '$source_path'"
  else
    expected_dependencies=$(
      if [[ "$dependencies" != "-" ]]; then
        printf '%s\n' "$dependencies" | tr ',' '\n' | sort -u
      fi
    )
    actual_dependencies=$(
      sed -nE 's/^[[:space:]]*(api|implementation|runtimeOnly)\(project\(":([^"]+)"\)\).*$/\2/p' "$build_file" | sort -u
    )
    if [[ "$actual_dependencies" != "$expected_dependencies" ]]; then
      fail "$module Gradle dependencies differ from the manifest (expected: '${dependencies}'; actual: '$(printf '%s' "$actual_dependencies" | tr '\n' ',')')"
    fi

    expected_optional_dependencies=$(
      if [[ "$optional_dependencies" != "-" ]]; then
        printf '%s\n' "$optional_dependencies" | tr ',' '\n' | sort -u
      fi
    )
    actual_optional_dependencies=$(
      sed -nE 's/^[[:space:]]*compileOnly\(project\(":([^"]+)"\)\).*$/\1/p' "$build_file" | sort -u
    )
    if [[ "$actual_optional_dependencies" != "$expected_optional_dependencies" ]]; then
      fail "$module optional Gradle dependencies differ from the manifest (expected: '${optional_dependencies}'; actual: '$(printf '%s' "$actual_optional_dependencies" | tr '\n' ',')')"
    fi

    if [[ "$exposure" != "public" ]] && grep -Eq '(^|[^A-Za-z-])maven-publish([^A-Za-z-]|$)' "$build_file"; then
      fail "$module is '$exposure' but applies Maven publishing"
    fi
  fi

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

if [[ -s "$edges" ]]; then
  tsort_output=$(LC_ALL=C tsort "$edges" 2>&1)
  tsort_status=$?
  if (( tsort_status != 0 )) || grep -Eiq '(cycle|loop)' <<<"$tsort_output"; then
    fail "the declared project dependencies contain a cycle"
  fi
fi

for module in woge-core woge-ui-headless woge-protocol woge-host-spi woge-server-runtime; do
  source_path=$(awk -F '\t' -v name="$module" '$1 == name { print $4; exit }' "$records")
  [[ -n "$source_path" ]] || continue
  [[ -d "$repository_root/$source_path" ]] || continue
  main_source_path="$repository_root/$source_path/src/main"
  [[ -d "$main_source_path" ]] || continue

  forbidden_types='(org\.springframework|reactor\.|jakarta\.servlet|javax\.servlet|io\.ktor)'
  if command -v rg >/dev/null 2>&1; then
    matches=$(rg -n --glob '*.kt' --glob '*.java' "$forbidden_types" "$main_source_path" || true)
  else
    matches=$(grep -R -n -E --include='*.kt' --include='*.java' "$forbidden_types" "$main_source_path" 2>/dev/null || true)
  fi
  [[ -z "$matches" ]] || fail "$module references a host framework in production source:\n$matches"
done

if grep -Eq 'project\(":woge-spring-webflux"\)' "$repository_root/adapters/woge-spring-mvc/build.gradle.kts"; then
  fail "woge-spring-mvc must not depend on woge-spring-webflux"
fi
if grep -Eq 'project\(":woge-spring-mvc"\)' "$repository_root/adapters/woge-spring-webflux/build.gradle.kts"; then
  fail "woge-spring-webflux must not depend on woge-spring-mvc"
fi

if (( failure_count > 0 )); then
  printf 'Module-boundary validation failed with %d problem(s).\n' "$failure_count" >&2
  exit 1
fi

printf 'Module-boundary validation passed.\n'
