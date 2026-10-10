#!/usr/bin/env bash

set -eu

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
framework_index="$repository_root/docs/ai-dx/woge-framework-index.json"
template="$repository_root/docs/ai-dx/application-agents.template.md"

if (( $# < 1 || $# > 2 )); then
  printf 'Usage: %s OUTPUT_FILE [APPLICATION_ROOT]\n' "$0" >&2
  exit 2
fi

output_file=$1
application_root=${2:-"$repository_root/scaffolds/spring-boot"}
gradle_properties="$application_root/gradle.properties"
scaffold_properties="$application_root/scaffold.properties"
package_json="$application_root/package.json"

for required_file in "$framework_index" "$template" "$gradle_properties" "$scaffold_properties" "$package_json"; do
  [[ -f "$required_file" ]] || {
    printf 'Agent-guidance input does not exist: %s\n' "$required_file" >&2
    exit 2
  }
done

property_value() {
  local file=$1
  local name=$2
  sed -nE "s/^${name}=//p" "$file"
}

json_string() {
  local file=$1
  local name=$2
  sed -nE "s|^[[:space:]]*\"${name}\": \"([^\"]+)\",?$|\1|p" "$file" | head -n 1
}

guidance_version=$(sed -nE 's/^[[:space:]]*"templateVersion": ([0-9]+),?$/\1/p' "$framework_index")
woge_version=$(json_string "$framework_index" frameworkVersion)
kotlin_version=$(json_string "$framework_index" kotlinVersion)
spring_boot_version=$(json_string "$framework_index" springBootVersion)
scaffold_version=$(property_value "$scaffold_properties" scaffoldVersion)
selected_host=$(property_value "$gradle_properties" wogeSpringAdapter)
build_jdk=$(property_value "$gradle_properties" buildJdk)
jvm_target=$(property_value "$gradle_properties" jvmTarget)
generated_sources=$(property_value "$scaffold_properties" generatedSources)
playwright_version=$(json_string "$package_json" '@playwright/test')
repository_blob_url='https://github.com/christian-draeger/woge/blob/main'
repository_tree_url='https://github.com/christian-draeger/woge/tree/main'
kotlin_guide_url="$repository_blob_url/$(json_string "$framework_index" kotlinGuide)"
safe_values_guide_url="$repository_blob_url/$(json_string "$framework_index" safeValuesGuide)"
html_guide_url="$repository_blob_url/$(json_string "$framework_index" htmlGuide)"
css_guide_url="$repository_blob_url/$(json_string "$framework_index" cssGuide)"
page_guide_url="$repository_blob_url/$(json_string "$framework_index" pageGuide)"
patch_guide_url="$repository_blob_url/$(json_string "$framework_index" patchGuide)"
manifest_guide_url="$repository_blob_url/$(json_string "$framework_index" manifestGuide)"
compiler_examples_url="$repository_tree_url/$(json_string "$framework_index" compilerExamples)"

case "$selected_host" in
  webflux) selected_host_label='Spring Boot WebFlux' ;;
  mvc) selected_host_label='Spring Boot MVC' ;;
  *)
    printf 'Unsupported selected Woge Spring adapter: %s\n' "$selected_host" >&2
    exit 2
    ;;
esac

[[ "$(property_value "$gradle_properties" wogeVersion)" == "$woge_version" ]] || {
  printf 'Scaffold Woge version does not match the framework index.\n' >&2
  exit 2
}
[[ "$(property_value "$gradle_properties" kotlinVersion)" == "$kotlin_version" ]] || {
  printf 'Scaffold Kotlin version does not match the framework index.\n' >&2
  exit 2
}

mkdir -p "$(dirname "$output_file")"
temporary_output=$(mktemp "${output_file}.tmp.XXXXXX")
trap 'rm -f "$temporary_output"' EXIT

awk \
  -v guidance_version="$guidance_version" \
  -v scaffold_version="$scaffold_version" \
  -v woge_version="$woge_version" \
  -v kotlin_version="$kotlin_version" \
  -v spring_boot_version="$spring_boot_version" \
  -v build_jdk="$build_jdk" \
  -v jvm_target="$jvm_target" \
  -v selected_host="$selected_host" \
  -v selected_host_label="$selected_host_label" \
  -v playwright_version="$playwright_version" \
  -v generated_sources="$generated_sources" \
  -v kotlin_guide_url="$kotlin_guide_url" \
  -v safe_values_guide_url="$safe_values_guide_url" \
  -v html_guide_url="$html_guide_url" \
  -v css_guide_url="$css_guide_url" \
  -v page_guide_url="$page_guide_url" \
  -v patch_guide_url="$patch_guide_url" \
  -v manifest_guide_url="$manifest_guide_url" \
  -v compiler_examples_url="$compiler_examples_url" \
  '{
    gsub(/\{\{GUIDANCE_VERSION\}\}/, guidance_version)
    gsub(/\{\{SCAFFOLD_VERSION\}\}/, scaffold_version)
    gsub(/\{\{WOGE_VERSION\}\}/, woge_version)
    gsub(/\{\{KOTLIN_VERSION\}\}/, kotlin_version)
    gsub(/\{\{SPRING_BOOT_VERSION\}\}/, spring_boot_version)
    gsub(/\{\{BUILD_JDK\}\}/, build_jdk)
    gsub(/\{\{JVM_TARGET\}\}/, jvm_target)
    gsub(/\{\{SELECTED_HOST\}\}/, selected_host)
    gsub(/\{\{SELECTED_HOST_LABEL\}\}/, selected_host_label)
    gsub(/\{\{PLAYWRIGHT_VERSION\}\}/, playwright_version)
    gsub(/\{\{GENERATED_SOURCES\}\}/, generated_sources)
    gsub(/\{\{KOTLIN_GUIDE_URL\}\}/, kotlin_guide_url)
    gsub(/\{\{SAFE_VALUES_GUIDE_URL\}\}/, safe_values_guide_url)
    gsub(/\{\{HTML_GUIDE_URL\}\}/, html_guide_url)
    gsub(/\{\{CSS_GUIDE_URL\}\}/, css_guide_url)
    gsub(/\{\{PAGE_GUIDE_URL\}\}/, page_guide_url)
    gsub(/\{\{PATCH_GUIDE_URL\}\}/, patch_guide_url)
    gsub(/\{\{MANIFEST_GUIDE_URL\}\}/, manifest_guide_url)
    gsub(/\{\{COMPILER_EXAMPLES_URL\}\}/, compiler_examples_url)
    print
  }' "$template" > "$temporary_output"

mv "$temporary_output" "$output_file"
trap - EXIT
printf 'Generated Woge application guidance at %s\n' "$output_file"
