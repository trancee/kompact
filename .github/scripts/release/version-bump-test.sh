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
