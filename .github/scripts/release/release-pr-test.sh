#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(git rev-parse --show-toplevel)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

REPOSITORY="$TEST_ROOT/repository"
REMOTE="$TEST_ROOT/remote.git"
mkdir -p "$REPOSITORY/.github/scripts/release" "$REPOSITORY/.github/scripts/docs" "$TEST_ROOT/bin"
cp "$REPOSITORY_ROOT/.github/scripts/release/manage-release-pr.sh" \
  "$REPOSITORY/.github/scripts/release/manage-release-pr.sh"
cp "$REPOSITORY_ROOT/.github/scripts/release/version-bump.sh" \
  "$REPOSITORY/.github/scripts/release/version-bump.sh"
cp "$REPOSITORY_ROOT/.github/scripts/docs/check-version-references.sh" \
  "$REPOSITORY/.github/scripts/docs/check-version-references.sh"
cp "$REPOSITORY_ROOT/.github/scripts/docs/sync-version-references.py" \
  "$REPOSITORY/.github/scripts/docs/sync-version-references.py"
cp -R "$REPOSITORY_ROOT/docs" "$REPOSITORY/"
cp "$REPOSITORY_ROOT/README.md" "$REPOSITORY/README.md"
cp "$REPOSITORY_ROOT/SECURITY.md" "$REPOSITORY/SECURITY.md"
cp "$REPOSITORY_ROOT/CHANGELOG.md" "$REPOSITORY/CHANGELOG.md"
printf 'allprojects {\n    version = "0.8.0-SNAPSHOT"\n}\n' > "$REPOSITORY/build.gradle.kts"

cat > "$TEST_ROOT/bin/gh" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$GH_CALLS"
case "$1 $2" in
  "pr list") exit 0 ;;
  "pr create") exit 0 ;;
  *) echo "Unexpected gh command: $*" >&2; exit 1 ;;
esac
EOF
chmod +x "$TEST_ROOT/bin/gh"
export PATH="$TEST_ROOT/bin:$PATH"
export GH_CALLS="$TEST_ROOT/gh-calls"

git init --quiet --initial-branch=main "$REPOSITORY"
git -C "$REPOSITORY" config user.name "Release Test"
git -C "$REPOSITORY" config user.email "release-test@example.invalid"
git -C "$REPOSITORY" add .
git -C "$REPOSITORY" commit --quiet -m "chore: prepare release fixture"
git -C "$REPOSITORY" tag v0.7.0
git init --quiet --bare "$REMOTE"
git -C "$REPOSITORY" remote add origin "$REMOTE"
git -C "$REPOSITORY" push --quiet -u origin main

(cd "$REPOSITORY" && .github/scripts/release/manage-release-pr.sh)
git -C "$REPOSITORY" fetch --quiet origin

release_version="$(git -C "$REPOSITORY" show origin/release/ongoing:build.gradle.kts)"
if ! grep -q 'version = "0.8.0"' <<< "$release_version"; then
  printf 'FAIL release PR branch does not contain the stable release version:\n%s\n' \
    "$release_version" >&2
  exit 1
fi

release_changelog="$(git -C "$REPOSITORY" show origin/release/ongoing:CHANGELOG.md)"
if ! grep -q '^## \[0.8.0\]' <<< "$release_changelog"; then
  printf 'FAIL release PR branch does not contain its release changelog:\n%s\n' \
    "$release_changelog" >&2
  exit 1
fi

release_readme="$(git -C "$REPOSITORY" show origin/release/ongoing:README.md)"
if ! grep -q 'implementation("ch.trancee.kompact:kompact:0.8.0")' <<< "$release_readme"; then
  printf 'FAIL release PR branch documentation does not describe the candidate release\n' >&2
  exit 1
fi
if grep -q '0.8.0-SNAPSHOT' <<< "$release_readme"; then
  printf 'FAIL release PR branch documentation still describes the snapshot candidate\n' >&2
  exit 1
fi

printf 'PASS release PR branch contains version, changelog, and synchronized docs\n'
