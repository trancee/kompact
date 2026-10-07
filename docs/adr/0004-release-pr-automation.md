# ADR-0004: Human-Reviewed Release PR Automation

- **Status:** accepted
- **Date:** 2026-09-13
- **Deciders:** kompact maintainer
- **Tags:** release, ci, github-actions, maven-central, pgp

## Context

Kompact publishes the runtime, KSP processor, and Gradle plugin through its
custom Central Portal Publisher integration. Release automation must preserve
Constitution G1: protected `main` receives changes only through reviewed PRs.
The root `build.gradle.kts` version is the canonical release candidate, and
maintained consumer documentation and `CHANGELOG.md` must describe the same
candidate before publication.

Earlier automation proposed release-version and next-SNAPSHOT commits directly
to `main`. That conflicts with G1 and leaves release metadata outside the
reviewed change. The release pipeline must also protect publishing credentials,
require approval for irreversible publication, and support safe retries after
partial failure.

## Decision

Release-version changes, changelog updates, and next-SNAPSHOT changes are
separate human-reviewed pull requests. Automation may push only managed topic
branches and release tags; it never pushes a commit to protected `main`.

### `release-pr.yml` — candidate preparation

- On pushes to `main`, do nothing unless the root version ends in
  `-SNAPSHOT`.
- Create or synchronize `release/ongoing` from `main`.
- Set the candidate to the stable version by removing `-SNAPSHOT`, generate
  the changelog for that version, synchronize maintained documentation, and
  run the documentation-version checker.
- Open or update a PR labelled `release`. Reviewers approve the version,
  changelog, and documentation together with the accumulated code changes.
- Conventional Commits group changelog entries only; they do not override the
  root Gradle candidate.

### `release-publish.yml` — reviewed release execution

- Trigger only when a PR labelled `release` is merged into `main`.
- Use `pull_request_target` for the trusted workflow definition, then check
  that the event's trusted base-branch commit exactly matches the PR's merge
  commit before checkout. Check out only the trusted event SHA; never use a
  PR-controlled ref or execute an unmerged PR head with release permissions.
- Require the merged root version to be stable and consistent with the
  changelog and maintained docs. Rerun release fixtures, Linux CI gates,
  plugin ABI and coverage checks, TestKit integration tests, and the
  Central Portal bundle dry-run.
- Create `v<version>` on the reviewed merge commit after all checks pass. A
  retry may reuse the tag only if it points to that exact commit; a conflicting
  existing tag fails closed.
- Build, sign, and deploy all published artifacts to Central Portal. Publishing
  remains behind the manually approved `release` GitHub Environment.
- After successful publication, create or update a separate PR from a managed
  branch that advances the root version and development documentation to the
  next `-SNAPSHOT`. Do not push this commit directly to `main`.

### Supporting infrastructure

- `version-bump.sh` reads the canonical root version, strips `-SNAPSHOT` for
  the release candidate, advances the minor version for the next development
  cycle, and generates changelog sections from commits since the latest tag.
- `sync-version-references.py` updates maintained consumer docs for either a
  stable release candidate or a development snapshot.
- The docs-version checker compares development references to the root
  version and published references to the newest `CHANGELOG.md` entry.
- Central Portal tokens and the signing key remain environment-scoped secrets.
  `RELEASE_PAT` is used only where automation must push a topic branch or tag
  and create a PR whose checks should run normally.

## Alternatives

1. **Push release and next-SNAPSHOT commits directly to `main`.** Rejected
   because it violates G1 and bypasses review of release metadata.
2. **Derive release versions from Conventional Commits.** Rejected because it
   can disagree with the declared root candidate; commits remain useful for
   changelog grouping.
3. **Use a release-please or Maven publishing plugin migration.** Rejected for
   this change because the current custom Portal Publisher already handles
   publication and credential redaction; replacing it is unrelated to the
   governance requirement.
4. **Perform releases manually.** Rejected because reproducible version,
   artifact, and publication gates belong in CI.

## Consequences and risks

- Release metadata and next-development metadata are reviewable and auditable
  PRs; the protected default branch is never modified by a direct automation
  push.
- The release requires an additional next-SNAPSHOT PR after publication.
- A failure after tag creation but before publication requires inspecting the
  tag and Central Portal state before retry. Tag reuse is safe only when its
  commit matches the reviewed merge.
- A failure after publication but before opening the next-SNAPSHOT PR leaves
  the published release valid; rerunning the follow-up job must create or
  update the PR without republishing.
- Physical iOS Arm64 and Android Native Arm64 behavior tests and allocation
  evidence remain independent release gates documented in
  [`docs/ci.md`](../ci.md). Simulator results do not satisfy physical-device
  proof, and no zero-allocation claim is implied without validated
  per-target counters and positive controls.

## Migration

Replace the former post-merge version/changelog commit and direct `main` push
with the reviewed candidate PR before the next release. Any in-flight release
must be reconciled against the root version, changelog, existing tag, and
Central Portal state before retrying. External branch-protection settings are
not changed by this ADR; maintainers must require the repository's actual
macOS and Linux CI checks.
