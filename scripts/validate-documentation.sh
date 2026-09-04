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
