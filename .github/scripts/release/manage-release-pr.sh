#!/usr/bin/env bash
# Manages the release PR lifecycle: creates or syncs a release/ongoing branch
# and opens/updates a release PR against main. Called by release-pr.yml.
set -euo pipefail

RELEASE_BRANCH="release/ongoing"
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
cat > "$PR_BODY_FILE" <<EOF
## Release PR

Accumulating changes from \`main\` for release **${VERSION}**.

_Merge this PR to trigger the release pipeline (version bump + Central Portal publish)._

### Changes since last release

\`\`\`
${LOG}
\`\`\`

### Next steps after merge

1. The \`Release Publish\` workflow will run quality gates on the merged code.
2. Version will be bumped from \`${VERSION}-SNAPSHOT\` to **${VERSION}** and tagged \`v${VERSION}\`.
3. Artifacts will be built, signed, and deployed to Central Portal. *(requires approval)*
4. After approval, the bundle is published to Maven Central.
5. Version is bumped to the next \`-SNAPSHOT\` and the release branch is deleted.
EOF

if git ls-remote --heads origin "${RELEASE_BRANCH}" | grep -q "${RELEASE_BRANCH}"; then
  echo "::group::Syncing release branch with main"
  git fetch origin "${RELEASE_BRANCH}:${RELEASE_BRANCH}"
  git checkout "${RELEASE_BRANCH}"
  git merge "origin/main" --ff-only
  git push origin "${RELEASE_BRANCH}"

  PR_NUMBER="$(gh pr list --head "${RELEASE_BRANCH}" --base main --state open --label release --json number --template '{{range .}}{{.number}}{{end}}')"
  if [ -n "$PR_NUMBER" ]; then
    gh pr edit "$PR_NUMBER" --title "$PR_TITLE" --body-file "$PR_BODY_FILE"
    echo "Updated release PR #${PR_NUMBER}"
  fi
  echo "::endgroup::"
else
  echo "::group::Creating initial release PR"
  git checkout -b "${RELEASE_BRANCH}"
  git push origin "${RELEASE_BRANCH}"
  gh pr create \
    --head "${RELEASE_BRANCH}" \
    --base main \
    --title "$PR_TITLE" \
    --body-file "$PR_BODY_FILE" \
    --label release
  echo "::endgroup::"
fi

rm -f "$PR_BODY_FILE"
