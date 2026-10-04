---
Type: grilling
Status: resolved
---

## Question

What version and documentation policy should identify the first supported
release, given Maven Central already contains `0.7.0`, the repository is at
`0.8.0-SNAPSHOT`, and consumer docs still say `0.6.1`? Decide how to avoid
overwriting any published coordinates, select the next supported version
strategy, and establish one source of truth plus an automated drift check for
release documentation.

## Answer

- Treat `0.8.0-SNAPSHOT` as the version for the first supported release:
  publish `0.8.0` only after the roadmap's release gates pass. Do not restart
  the version sequence or rewrite published coordinates.
- Leave the already-published `0.7.0` artifact immutable and in the release
  history. Correct consumer documentation to identify it as the latest
  published version until `0.8.0` is actually published; do not claim that
  `0.7.0` was supported or retroactively relabel it.
- Make the version in the root `build.gradle.kts` the single source of truth
  for the current development/release candidate version. Release automation
  and changelog generation must use that value, stripping `-SNAPSHOT` for the
  release artifact rather than independently deriving a different release.
  The existing `version-bump.sh extract-release` currently returns `0.7.1`
  from this checkout, so aligning that script and adding a regression test for
  the `0.8.0-SNAPSHOT` -> `0.8.0` case is part of the implementation roadmap.
- Keep `CHANGELOG.md` as the generated release-history record. Add a CI drift
  check that verifies current-checkout version references in maintained
  consumer docs against the Gradle version and published-version references
  against the newest release entry in the changelog. Release automation must
  update the changelog and affected docs in the human-reviewed release PR
  before publication; after publication, the same release entry becomes the
  docs' latest-published version.
