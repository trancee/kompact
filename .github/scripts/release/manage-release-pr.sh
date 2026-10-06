#!/usr/bin/env bash
# Manages the release PR lifecycle: creates or syncs a release/ongoing branch
# and opens/updates a release PR against main. Called by release-pr.yml.
#
# The release/ongoing branch carries the release version, changelog, and
# synchronized consumer documentation for human review before publishing.
set -euo pipefail

RELEASE_BRANCH="release/ongoing"
CURRENT_VERSION="$(.github/scripts/release/version-bump.sh extract-version)"
if [[ "$CURRENT_VERSION" != *-SNAPSHOT ]]; then
  echo "Main is at ${CURRENT_VERSION}, not a SNAPSHOT; no release candidate PR is needed."
  exit 0
fi

VERSION="$(.github/scripts/release/version-bump.sh extract-release)"
PR_TITLE="Release: ${VERSION}"

# Build changelog from commits since the last release tag.
LAST_TAG="$(git describe --tags --match 'v[0-9]*.*' --abbrev=0 2>/dev/null || true)"
if [ -n "$LAST_TAG" ]; then
  LOG="$(git log --oneline "${LAST_TAG}..HEAD" --pretty=format:'- %s (%h)' | head -50)"
else
  LOG="$(git log --oneline --pretty=format:'- %s (%h)' | head -50)"
fi

# Write PR body to a temp file to avoid YAML heredoc escaping issues.
PR_BODY_FILE="$(mktemp)"
trap 'rm -f "$PR_BODY_FILE"' EXIT
cat > "$PR_BODY_FILE" <<EOF
## Release PR

Prepares **${VERSION}** for release from the canonical candidate in
\`build.gradle.kts\`. The release version, generated changelog, and synchronized
consumer documentation are included in this human-reviewed PR.

_Merging this PR triggers the release gates and creates the \`v${VERSION}\` tag.
Publishing to Maven Central requires approval through the \`release\` environment._

### Changes since last release

\`\`\`
${LOG}
\`\`\`

### Next steps after merge

1. The \`Release Publish\` workflow reruns required quality and artifact gates on the merge commit.
2. After the gates pass, that reviewed merge commit is tagged \`v${VERSION}\`.
3. Artifacts are built, signed, and deployed to Central Portal. *(requires approval)*
4. After approval, the bundle is published to Maven Central.
5. A separate PR advances the development version and documentation to the next \`-SNAPSHOT\`.
EOF

prepare_release_candidate() {
  .github/scripts/release/version-bump.sh bump-release
  .github/scripts/release/version-bump.sh changelog
  python3 .github/scripts/docs/sync-version-references.py .
  bash .github/scripts/docs/check-version-references.sh .
}

commit_release_candidate() {
  git add CHANGELOG.md build.gradle.kts README.md SECURITY.md docs
  if ! git diff --cached --quiet; then
    git commit -m "chore(release): prepare ${VERSION}"
  fi
}

if git ls-remote --heads origin "${RELEASE_BRANCH}" | grep -q "${RELEASE_BRANCH}"; then
  echo "::group::Syncing release branch with main"
  git fetch origin main "${RELEASE_BRANCH}"
  git checkout -B "${RELEASE_BRANCH}" "origin/${RELEASE_BRANCH}"
  git reset --hard origin/main

  prepare_release_candidate
  commit_release_candidate

  if ! git diff --quiet origin/main...HEAD; then
    git push origin "${RELEASE_BRANCH}" --force-with-lease
  fi

  PR_NUMBER="$(gh pr list --head "${RELEASE_BRANCH}" --base main --state open --label release --json number --template '{{range .}}{{.number}}{{end}}')"
  if [ -n "$PR_NUMBER" ]; then
    gh pr edit "$PR_NUMBER" --title "$PR_TITLE" --body-file "$PR_BODY_FILE"
    echo "Updated release PR #${PR_NUMBER}"
  else
    gh pr create \
      --head "${RELEASE_BRANCH}" \
      --base main \
      --title "$PR_TITLE" \
      --body-file "$PR_BODY_FILE" \
      --label release
    echo "Created new release PR"
  fi
  echo "::endgroup::"
else
  echo "::group::Creating initial release PR"
  git checkout -b "${RELEASE_BRANCH}"

  prepare_release_candidate
  commit_release_candidate
  git push origin "${RELEASE_BRANCH}"
  gh pr create \
    --head "${RELEASE_BRANCH}" \
    --base main \
    --title "$PR_TITLE" \
    --body-file "$PR_BODY_FILE" \
    --label release
  echo "Created new release PR"
  echo "::endgroup::"
fi
