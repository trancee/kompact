#!/usr/bin/env bash
# Extracts, validates, and bumps the project version in build.gradle.kts.
# The version lives in the root build.gradle.kts allprojects block:
#   version = "0.2.0-SNAPSHOT"
#
# The root build.gradle.kts version is the authoritative release candidate.
# Release automation strips -SNAPSHOT; Conventional Commits group changelog
# entries but do not independently calculate another version.
#
# Usage:
#   version-bump.sh extract-release   # print root candidate without -SNAPSHOT
#   version-bump.sh extract-next-snap # print next SNAPSHOT (minor+1 from release)
#   version-bump.sh bump-release      # -SNAPSHOT -> root candidate release in build.gradle.kts
#   version-bump.sh bump-next-snap    # release -> next -SNAPSHOT in build.gradle.kts
#   version-bump.sh changelog         # generate/update CHANGELOG.md for release
set -euo pipefail

BUILD_FILE="build.gradle.kts"

# Read the raw version string from build.gradle.kts.
extract_snapshot() {
  grep 'version = ' "$BUILD_FILE" \
    | head -1 \
    | sed 's/.*"\(.*\)".*/\1/'
}

# Strip "-SNAPSHOT" if present (pure file read, no git).
extract_release() {
  local snap
  snap="$(extract_snapshot)"
  if [[ "$snap" == *-SNAPSHOT ]]; then
    echo "${snap%-SNAPSHOT}"
  else
    echo "$snap"
  fi
}

# Next SNAPSHOT starts the following minor development cycle.
extract_next_snap() {
  local release
  release="$(extract_release)"
  local major minor
  IFS='.' read -r major minor _ <<<"$release"
  echo "${major}.$((minor + 1)).0-SNAPSHOT"
}

# Generate (or prepend to) CHANGELOG.md for the computed release version.
# Uses Conventional Commits since the last tag, grouped by type.
generate_changelog() {
  local version changelog_file date last_tag commits raw_entries

  version="$(extract_release)"
  changelog_file="CHANGELOG.md"
  date="$(date +%Y-%m-%d)"

  # Commits since the last release tag (or all commits if no tag).
  last_tag="$(git describe --tags --match 'v[0-9]*.*' --abbrev=0 2>/dev/null || true)"
  if [ -n "$last_tag" ]; then
    commits="$(git log --oneline "${last_tag}..HEAD" --pretty=format:'%s (%h)' 2>/dev/null || true)"
  else
    commits="$(git log --oneline --pretty=format:'%s (%h)' 2>/dev/null || true)"
  fi

  # Filter out release-process noise: any (release) scope or release: prefix.
  raw_entries="$(echo "$commits" | grep -vE '^(release:|[^()]*\(release\):)' || true)"

  # Group by Conventional Commit type.
  local feat fix breaking other
  breaking="$(echo "$raw_entries" | grep -E '^(feat|fix|perf|refactor|build)!:|BREAKING[ -]CHANGE' || true)"
  feat="$(echo "$raw_entries" | grep -E '^feat(:|[(])' || true)"
  fix="$(echo "$raw_entries" | grep -E '^fix:' || true)"
  other="$(echo "$raw_entries" | grep -vE '^(feat|fix|perf|refactor|build)!:|^feat(:|[(])|^fix:' || true)"

  # Build the changelog section.
  local section="## [${version}] - ${date}
"
  if [ -n "$breaking" ]; then
    section+="
### ⚠️ Breaking
$(echo "$breaking" | sed 's/^/- /')
"
  fi
  if [ -n "$feat" ]; then
    section+="
### ✨ Features
$(echo "$feat" | sed 's/^/- /')
"
  fi
  if [ -n "$fix" ]; then
    section+="
### 🐛 Fixes
$(echo "$fix" | sed 's/^/- /')
"
  fi
  if [ -n "$other" ]; then
    section+="
### 📦 Other
$(echo "$other" | sed 's/^/- /')
"
  fi

  local header="# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

"

  # Prepend: fresh header + new section + leftover sections.
  #
  # Idempotency (M1): re-running `changelog` for the SAME version must REPLACE
  # the prior section for that version, not duplicate it. We drop the preamble
  # (it is rebuilt verbatim from $header above, so it can never drift) and strip
  # any section whose header is `## [${version}]` — that header line plus its
  # body up to the next `## ` section or EOF — before splicing in the freshly
  # built $section. Without this, N runs multiply the same release notes.
  #
  # NOTE: this fix prevents future duplication, but it replaces only the
  # *current* version's section. A CHANGELOG already corrupted by the
  # pre-fix generator (e.g. a triplicated `## [0.1.6]` block) requires a
  # one-time release-ops rebuild: run `changelog` once per historical tag so
  # each `## [X.Y.Z]` section is regenerated and collapses from N copies to one.
  local existing=""
  if [ -f "$changelog_file" ]; then
    existing="$(awk -v target="## [${version}]" '
      /^## / { started = 1 }
      !started { next }
      index($0, target) == 1 { skip = 1; next }
      /^## / { skip = 0 }
      !skip { print }
    ' "$changelog_file" 2>/dev/null || true)"
  fi

  printf '%s%s\n%s\n' "$header" "$section" "$existing" > "$changelog_file"
  echo "Generated CHANGELOG.md for version ${version}"
}

bump_release() {
  local release
  release="$(extract_release)"
  sed -i.bak "s/version = \".*-SNAPSHOT\"/version = \"${release}\"/" "$BUILD_FILE"
  rm -f "${BUILD_FILE}.bak"
  echo "Bumped to release version: ${release}"
}

bump_next_snap() {
  local next
  next="$(extract_next_snap)"
  sed -i.bak "s/version = \".*\"/version = \"${next}\"/" "$BUILD_FILE"
  rm -f "${BUILD_FILE}.bak"
  echo "Bumped to next SNAPSHOT: ${next}"
}

case "${1:-}" in
  extract-release)    extract_release ;;
  extract-version)   extract_snapshot ;;
  extract-next-snap) extract_next_snap ;;
  bump-release)       bump_release ;;
  bump-next-snap)     bump_next_snap ;;
  changelog)          generate_changelog ;;
  *)
    echo "Usage: $0 {extract-version|extract-release|extract-next-snap|bump-release|bump-next-snap|changelog}" >&2
    exit 1
    ;;
esac
