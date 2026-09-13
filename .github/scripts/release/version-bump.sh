#!/usr/bin/env bash
# Extracts, validates, and bumps the project version in build.gradle.kts.
# The version lives in the root build.gradle.kts allprojects block:
#   version = "0.2.0-SNAPSHOT"
#
# Release versioning follows Conventional Commits semantics:
#   - feat!  or BREAKING CHANGE  → major bump  (X.0.0 → (X+1).0.0)
#   - feat   (no breaking)        → minor bump  (0.Y.Z → 0.(Y+1).0)
#   - fix/other                   → patch bump  (0.Y.Z → 0.Y.(Z+1))
#
# Commits since the last release tag (vX.Y.0) are analysed. If there is no
# previous tag (first release) all commits are considered. If git is not
# available, falls back to stripping -SNAPSHOT.
#
# Usage:
#   version-bump.sh extract-release   # print computed release version
#   version-bump.sh extract-next-snap # print next SNAPSHOT (always patch+1)
#   version-bump.sh bump-release      # -SNAPSHOT -> computed release in build.gradle.kts
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

# Determine the bump type from Conventional Commits since the last tag.
# Returns "major", "minor", or "patch".
# Falls back to "patch" when git is unavailable or no commits found.
_get_bump_type() {
  local last_tag commits
  last_tag="$(git describe --tags --match 'v[0-9]*.*' --abbrev=0 2>/dev/null || true)"

  if [ -n "$last_tag" ]; then
    commits="$(git log --oneline "${last_tag}..HEAD" --pretty=format:'%s%n%b' 2>/dev/null || true)"
  else
    commits="$(git log --oneline --pretty=format:'%s%n%b' 2>/dev/null || true)"
  fi

  if [ -z "$commits" ]; then
    echo "patch"
    return
  fi

  # Breaking change: any commit with "!" after type, or BREAKING CHANGE footer.
  # %b (body) is included above so the BREAKING CHANGE footer is visible.
  if echo "$commits" | grep -qE '^(feat|fix|perf|refactor|build|chore|ci|style|test|docs)!:|BREAKING[ -]CHANGE'; then
    echo "major"
  elif echo "$commits" | grep -qE '^feat(:|[:(])'; then
    echo "minor"
  else
    echo "patch"
  fi
}

# Compute the release version from the current SNAPSHOT version and
# Conventional Commits since the last tag.
# Example: 0.2.0-SNAPSHOT + "feat:" commits → 0.3.0
compute_release_version() {
  local base bump major minor patch
  base="$(extract_release)"

  # Parse major.minor.patch
  IFS='.' read -r major minor patch <<<"$base"

  if ! bump="$(_get_bump_type)"; then
    bump="patch"
  fi

  case "$bump" in
    major) echo "$((major + 1)).0.0" ;;
    minor) echo "${major}.$((minor + 1)).0" ;;
    patch) echo "${major}.${minor}.$((patch + 1))" ;;
    *)     echo "$base" ;;
  esac
}

# The release version used by the PR title and bump-release.
# If the current version is already a release (no -SNAPSHOT suffix),
# returns it as-is. For SNAPSHOT versions, computes from git using
# Conventional Commits since the last tag. Falls back to stripping
# -SNAPSHOT when git is unavailable.
extract_computed_release() {
  local snap
  snap="$(extract_snapshot)"

  # Already a release version — return as-is.
  if [[ "$snap" != *-SNAPSHOT ]]; then
    echo "$snap"
    return
  fi

  # For SNAPSHOT, compute from git.
  if git rev-parse --git-dir >/dev/null 2>&1; then
    compute_release_version
  else
    echo "${snap%-SNAPSHOT}"
  fi
}

# Next SNAPSHOT is always a patch increment from the release version.
extract_next_snap() {
  local release
  release="$(extract_release)"
  local major minor patch
  IFS='.' read -r major minor patch <<<"$release"
  echo "${major}.${minor}.$((patch + 1))-SNAPSHOT"
}

# Generate (or prepend to) CHANGELOG.md for the computed release version.
# Uses Conventional Commits since the last tag, grouped by type.
generate_changelog() {
  local version changelog_file date last_tag commits raw_entries

  version="$(extract_computed_release)"
  changelog_file="CHANGELOG.md"
  date="$(date +%Y-%m-%d)"

  # Commits since the last release tag (or all commits if no tag).
  last_tag="$(git describe --tags --match 'v[0-9]*.*' --abbrev=0 2>/dev/null || true)"
  if [ -n "$last_tag" ]; then
    commits="$(git log --oneline "${last_tag}..HEAD" --pretty=format:'%s (%h)' 2>/dev/null || true)"
  else
    commits="$(git log --oneline --pretty=format:'%s (%h)' 2>/dev/null || true)"
  fi

  # Filter out release/version-bump noise.
  raw_entries="$(echo "$commits" | grep -vE '^(release:|chore\(release\):)' || true)"

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

  # Prepend: header + new section + existing content (minus header).
  local existing=""
  if [ -f "$changelog_file" ]; then
    # Strip existing header to avoid duplication.
    existing="$(sed '1,/^## /d' "$changelog_file" 2>/dev/null || true)"
  fi

  printf '%s%s\n%s\n' "$header" "$section" "$existing" > "$changelog_file"
  echo "Generated CHANGELOG.md for version ${version}"
}

bump_release() {
  local release
  release="$(extract_computed_release)"
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
  extract-release)    extract_computed_release ;;
  extract-next-snap)  extract_next_snap ;;
  bump-release)       bump_release ;;
  bump-next-snap)     bump_next_snap ;;
  changelog)          generate_changelog ;;
  *)
    echo "Usage: $0 {extract-release|extract-next-snap|bump-release|bump-next-snap|changelog}" >&2
    exit 1
    ;;
esac
