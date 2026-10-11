#!/usr/bin/env bash

set -u

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
failure_count=0

fail() {
  printf 'Documentation validation: %s\n' "$1" >&2
  failure_count=$((failure_count + 1))
}

while IFS= read -r markdown_file; do
  while IFS= read -r markdown_link; do
    target=${markdown_link#](}
    target=${target%)}
    target=${target#<}
    target=${target%>}
    case "$target" in
      ""|'#'*|http://*|https://*|mailto:*|codex://*) continue ;;
    esac
    target=${target%%#*}
    if [[ ! -e "$(dirname "$markdown_file")/$target" ]]; then
      fail "${markdown_file#"$repository_root/"} links to missing local target '$target'"
    fi
  done < <(grep -oE ']\((<[^>]+>|[^)]+)\)' "$markdown_file" || true)
done < <(
  find "$repository_root" \
    \( -type d \( -name .git -o -name .gradle -o -name build -o -name node_modules \) -prune \) \
    -o -type f -name '*.md' -print \
    | sort
)

while IFS= read -r snippet_reference; do
  snippet_path=${snippet_reference#*snippet: }
  snippet_path=${snippet_path% -->}
  if [[ ! -f "$repository_root/$snippet_path" ]]; then
    fail "snippet reference points to missing source '$snippet_path'"
  fi
done < <(
  grep -R -h -E '<!-- snippet: [A-Za-z0-9_./-]+ -->' \
    "$repository_root/README.md" "$repository_root/CONTRIBUTING.md" "$repository_root/docs" 2>/dev/null || true
)

# The existing index is also a contract: parse it and check local sources and pinned versions.
if ! python3 - "$repository_root" <<'PYTHON'
import json
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
index = json.loads((root / "docs/ai-dx/woge-framework-index.json").read_text())
assert index["schemaVersion"] == 1, "unsupported documentation index schema"
for key in ("startHere", "concepts", "troubleshooting", "canonicalPageAndAction"):
    assert key in index["documentation"], f"missing documentation entry point: {key}"

def check_paths(value):
    if isinstance(value, dict):
        for child in value.values():
            check_paths(child)
    elif isinstance(value, list):
        for child in value:
            check_paths(child)
    elif isinstance(value, str) and value.startswith(("docs/", "examples/", "scaffolds/", "scripts/", "modules/", "adapters/", "integrations/", "testing/")):
        assert (root / value).exists(), f"missing indexed path: {value}"

check_paths(index)
properties = (root / "gradle.properties").read_text()
catalog = (root / "gradle/libs.versions.toml").read_text()
assert index["frameworkVersion"] == re.search(r"^wogeVersion=(.+)$", properties, re.M)[1], "Woge version skew"
for indexed, name in ((index["language"]["kotlinVersion"], "kotlin"),
                      (index["hosts"]["springBootVersion"], "springBoot"),
                      (index["hosts"]["ktorVersion"], "ktor")):
    assert indexed == re.search(rf'^{name} = "([^"]+)"$', catalog, re.M)[1], f"{name} version skew"
client = json.loads((root / "client/woge-fallback-client/package.json").read_text())
assert index["browserClient"]["packageVersion"] == client["version"], "browser package version skew"
assert index["browserClient"]["patchProtocolVersion"] == index["protocol"]["patchStreamVersion"] == client["woge"]["patchProtocolVersion"], "patch protocol version skew"
PYTHON
then
  fail "invalid machine-readable documentation index"
fi

for required_path in docs/adr docs/guides docs/snippets examples; do
  if [[ ! -d "$repository_root/$required_path" ]]; then
    fail "missing canonical documentation/example location '$required_path'"
  fi
done

if (( failure_count > 0 )); then
  printf 'Documentation validation failed with %d problem(s).\n' "$failure_count" >&2
  exit 1
fi

printf 'Documentation validation passed.\n'
