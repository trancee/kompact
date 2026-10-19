#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(git rev-parse --show-toplevel)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

assert_release_version() {
  local name="$1"
  local base_version="$2"
  local snapshot_version="$3"
  local commit_subject="$4"
  local expected_version="$5"
  local commit_body="${6:-}"
  local repository="$TEST_ROOT/$name"

  mkdir -p "$repository/.github/scripts/release"
  cp "$REPOSITORY_ROOT/.github/scripts/release/version-bump.sh" \
    "$repository/.github/scripts/release/version-bump.sh"
  printf 'allprojects {\n    version = "%s"\n}\n' "$snapshot_version" \
    > "$repository/build.gradle.kts"

  git -C "$repository" init --quiet
  git -C "$repository" config user.name "Release Test"
  git -C "$repository" config user.email "release-test@example.invalid"
  git -C "$repository" add .
  git -C "$repository" commit --quiet -m "release: $base_version"
  git -C "$repository" tag "v$base_version"

  printf '%s\n' "$commit_subject" > "$repository/change.txt"
  git -C "$repository" add change.txt
  if [ -n "$commit_body" ]; then
    git -C "$repository" commit --quiet -m "$commit_subject" -m "$commit_body"
  else
    git -C "$repository" commit --quiet -m "$commit_subject"
  fi

  local actual_version
  local actual_current_version
  actual_current_version="$(
    cd "$repository"
    .github/scripts/release/version-bump.sh extract-version
  )"
  if [ "$actual_current_version" != "$snapshot_version" ]; then
    printf 'FAIL %s: expected current version %s, got %s\n' \
      "$name" "$snapshot_version" "$actual_current_version" >&2
    return 1
  fi

  actual_version="$(
    cd "$repository"
    .github/scripts/release/version-bump.sh extract-release
  )"

  if [ "$actual_version" != "$expected_version" ]; then
    printf 'FAIL %s: expected %s, got %s\n' \
      "$name" "$expected_version" "$actual_version" >&2
    return 1
  fi

  printf 'PASS %s: %s\n' "$name" "$actual_version"
}

assert_release_version \
  "pre-stable-breaking" \
  "0.3.0" \
  "0.4.0-SNAPSHOT" \
  "fix!: replace packed result representation" \
  "0.4.0"

assert_release_version \
  "stable-breaking" \
  "1.2.3" \
  "2.0.0-SNAPSHOT" \
  "fix!: replace packed result representation" \
  "2.0.0"

assert_release_version \
  "pre-stable-breaking-footer" \
  "0.3.0" \
  "0.4.0-SNAPSHOT" \
  "fix: replace packed result representation" \
  "0.4.0" \
  "BREAKING CHANGE: LongResult no longer exposes packed."

assert_release_version \
  "pre-stable-feature" \
  "0.3.0" \
  "0.4.0-SNAPSHOT" \
  "feat: add scalar decoder" \
  "0.4.0"

assert_release_version \
  "pre-stable-fix" \
  "0.3.0" \
  "0.3.1-SNAPSHOT" \
  "fix: reject truncated prefix" \
  "0.3.1"

assert_release_version \
  "first-supported-release-candidate" \
  "0.7.0" \
  "0.8.0-SNAPSHOT" \
  "fix: reject truncated prefix" \
  "0.8.0"

NEXT_SNAPSHOT_FIXTURE="$TEST_ROOT/next-snapshot"
mkdir -p "$NEXT_SNAPSHOT_FIXTURE"
printf 'allprojects {\n    version = "0.8.0"\n}\n' \
  > "$NEXT_SNAPSHOT_FIXTURE/build.gradle.kts"
actual_next_snapshot="$(
  cd "$NEXT_SNAPSHOT_FIXTURE"
  "$REPOSITORY_ROOT/.github/scripts/release/version-bump.sh" extract-next-snap
)"
if [ "$actual_next_snapshot" != "0.9.0-SNAPSHOT" ]; then
  printf 'FAIL next snapshot: expected 0.9.0-SNAPSHOT, got %s\n' \
    "$actual_next_snapshot" >&2
  exit 1
fi
printf 'PASS next snapshot: %s\n' "$actual_next_snapshot"

NOTES_FIXTURE="$TEST_ROOT/release-notes"
mkdir -p "$NOTES_FIXTURE"
cat > "$NOTES_FIXTURE/CHANGELOG.md" <<'CHANGELOG'
# Changelog

Preamble.

## [0.10.0] - 2026-10-19

### 📦 Other
- docs: keep version text true (#109) (cb71d83)

## [0.9.0] - 2026-10-18

### 🐛 Fixes
- fix: older entry (529dedd)
CHANGELOG
expected_notes="### 📦 Other
- docs: keep version text true (#109) (cb71d83)"
for version in 0.10.0 0.9.0; do
  actual_notes="$(
    cd "$NOTES_FIXTURE"
    "$REPOSITORY_ROOT/.github/scripts/release/version-bump.sh" release-notes "$version"
  )"
  if [ "$version" = "0.9.0" ]; then
    expected_notes="### 🐛 Fixes
- fix: older entry (529dedd)"
  fi
  if [ "$actual_notes" != "$expected_notes" ]; then
    printf 'FAIL release notes %s: got\n%s\n' "$version" "$actual_notes" >&2
    exit 1
  fi
done
if (cd "$NOTES_FIXTURE" && "$REPOSITORY_ROOT/.github/scripts/release/version-bump.sh" release-notes 0.1.0 >/dev/null 2>&1); then
  printf 'FAIL release notes: missing version must fail\n' >&2
  exit 1
fi
printf 'PASS release notes\n'
