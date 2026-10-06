#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

development_version="$(
  sed -nE 's/^[[:space:]]*version = "([0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?)".*/\1/p' build.gradle.kts \
    | head -1
)"
published_version="$(
  awk '/^## \[[0-9]+\.[0-9]+\.[0-9]+\]/ {
    line = $0
    sub(/^## \[/, "", line)
    sub(/\].*/, "", line)
    print line
    exit
  }' CHANGELOG.md
)"

if [[ -z "$development_version" ]]; then
  echo "Could not read a semantic project version from build.gradle.kts" >&2
  exit 1
fi
if [[ -z "$published_version" ]]; then
  echo "Could not read the latest published version from CHANGELOG.md" >&2
  exit 1
fi

doc_files=(
  README.md
  SECURITY.md
  docs/getting-started.md
  docs/api-reference.md
  docs/ci.md
  docs/how-to/*.md
  docs/agents/agent-quick-start.md
  docs/research/ksp-kmp-generation.md
)

failed=0
for file in "${doc_files[@]}"; do
  [[ -f "$file" ]] || continue

  while IFS=: read -r line_number version; do
    [[ -n "$version" ]] || continue
    if [[ "$version" != "$development_version" ]]; then
      printf '%s:%s: development version %s does not match build.gradle.kts (%s)\n' \
        "$file" "$line_number" "$version" "$development_version" >&2
      failed=1
    fi
  done < <(grep -nEo '[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT' "$file" || true)

  line_number=0
  while IFS= read -r line || [[ -n "$line" ]]; do
    ((line_number += 1))
    check_published=false
    check_development=false
    if [[ "$file" == "docs/research/ksp-kmp-generation.md" ]]; then
      if [[ "$line" =~ ch\.trancee\.kompact|Kompact.*(published|latest) ]]; then
        check_published=true
      fi
    elif [[ "$line" =~ ch\.trancee\.kompact|published|latest ]]; then
      check_published=true
    fi
    if [[ "$line" =~ development[[:space:]-]+version|current[[:space:]-]+checkout|this[[:space:]]+checkout ]]; then
      check_development=true
    fi

    [[ "$check_published" == true || "$check_development" == true ]] || continue
    while IFS= read -r version; do
      [[ -n "$version" ]] || continue
      if [[ "$check_development" == true && "$version" == *-SNAPSHOT && "$version" != "$development_version" ]]; then
        printf '%s:%s: development version %s does not match build.gradle.kts (%s)\n' \
          "$file" "$line_number" "$version" "$development_version" >&2
        failed=1
      elif [[ "$check_development" == true && "$development_version" != *-SNAPSHOT && "$version" != *-SNAPSHOT && "$version" != "$development_version" ]]; then
        printf '%s:%s: development version %s does not match build.gradle.kts (%s)\n' \
          "$file" "$line_number" "$version" "$development_version" >&2
        failed=1
      fi
      if [[ "$check_published" == true && "$version" != *-SNAPSHOT && "$version" != "$published_version" ]]; then
        printf '%s:%s: published version %s does not match latest CHANGELOG.md release (%s)\n' \
          "$file" "$line_number" "$version" "$published_version" >&2
        failed=1
      fi
    done < <(grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?' <<< "$line" || true)
  done < "$file"
done

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf 'Documentation versions match build.gradle.kts (%s) and CHANGELOG.md (%s).\n' \
  "$development_version" "$published_version"
