#!/usr/bin/env bash

set -u

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
adr_directory="$repository_root/docs/adr"
index_file="$adr_directory/README.md"
failure_count=0

fail() {
  printf 'ADR validation: %s\n' "$1" >&2
  failure_count=$((failure_count + 1))
}

if [[ ! -d "$adr_directory" ]]; then
  printf 'ADR validation: missing docs/adr directory\n' >&2
  exit 1
fi

if [[ ! -f "$index_file" ]]; then
  printf 'ADR validation: missing docs/adr/README.md\n' >&2
  exit 1
fi

duplicate_numbers=$(
  find "$adr_directory" -maxdepth 1 -type f -name '[0-9][0-9][0-9][0-9]-*.md' \
    | sed -E 's#^.*/([0-9]{4})-.*#\1#' \
    | sort \
    | uniq -d
)

if [[ -n "$duplicate_numbers" ]]; then
  fail "duplicate ADR numbers: $(printf '%s' "$duplicate_numbers" | tr '\n' ' ')"
fi

while IFS= read -r adr_file; do
  filename=$(basename "$adr_file")

  if [[ ! "$filename" =~ ^[0-9]{4}-[a-z0-9]+(-[a-z0-9]+)*\.md$ ]]; then
    fail "$filename does not match NNNN-lowercase-slug.md"
    continue
  fi

  if [[ "$filename" == "0000-template.md" ]]; then
    continue
  fi

  number=${filename:0:4}
  required_patterns=(
    "# ADR $number: "
    "- Status: "
    "- Date: "
    "- Decision owners: "
    "- Related issues: "
    "## Context"
    "## Decision"
    "## Alternatives considered"
    "## Consequences"
    "## Follow-up"
  )

  for pattern in "${required_patterns[@]}"; do
    if ! grep -Fq -- "$pattern" "$adr_file"; then
      fail "$filename is missing '$pattern'"
    fi
  done

  status=$(sed -nE 's/^- Status: (.*)$/\1/p' "$adr_file")
  case "$status" in
    Proposed|Accepted|Rejected|Deprecated|Superseded) ;;
    *) fail "$filename has invalid status '$status'" ;;
  esac

  date_value=$(sed -nE 's/^- Date: (.*)$/\1/p' "$adr_file")
  if [[ ! "$date_value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    fail "$filename has invalid date '$date_value'"
  fi

  if [[ "$status" == "Superseded" ]] && ! grep -Fq -- "- Superseded by:" "$adr_file"; then
    fail "$filename is superseded but has no '- Superseded by:' link"
  fi

  if ! grep -Fq -- "($filename)" "$index_file"; then
    fail "$filename is missing from docs/adr/README.md"
  fi

  while IFS= read -r linked_file; do
    [[ -z "$linked_file" ]] && continue
    if [[ ! -f "$(dirname "$adr_file")/$linked_file" ]]; then
      fail "$filename links to missing local file '$linked_file'"
    fi
  done < <(sed -nE 's/.*\]\(([^:)#]+\.md)(#[^)]*)?\).*/\1/p' "$adr_file")
done < <(find "$adr_directory" -maxdepth 1 -type f -name '*.md' ! -name 'README.md' | sort)

if (( failure_count > 0 )); then
  printf 'ADR validation failed with %d problem(s).\n' "$failure_count" >&2
  exit 1
fi

printf 'ADR validation passed.\n'
