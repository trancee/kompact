# ADR-0004: Human-reviewed release PR automation

- Status: Accepted
- Date: 2026-09-13
- Deciders: kompact maintainer
- Tags: release, ci, github-actions, maven-central, pgp
- Amended by: [ADR-0007](0007-pre-1-release-versioning.md) (breaking changes
  before `1.0.0` increment MINOR)

## Context

Kompact publishes the runtime, the KSP processor, and the Gradle plugin through
its own Central Portal Publisher integration. Release automation must keep
Constitution G1: protected `main` receives changes only through reviewed PRs.
The version in the root `build.gradle.kts` is the canonical release candidate.
Before publication, the maintained consumer documentation and `CHANGELOG.md`
must describe the same candidate.

Earlier automation committed the release version and the next SNAPSHOT
directly to `main`. That conflicts with G1, and it leaves release metadata
outside the reviewed change. The release pipeline must also protect the
publishing credentials, require approval before the publication that cannot be
undone, and support safe retries after a partial failure.

## Decision

The release-version change, the changelog update, and the next-SNAPSHOT change
go through separate pull requests that a human reviews. Automation pushes only
managed topic branches and release tags. It never pushes a commit to protected
`main`.

### `release-pr.yml` prepares the candidate

- On a push to `main`, the workflow does nothing unless the root version ends
  in `-SNAPSHOT`.
- It creates or updates `release/ongoing` from `main`.
- It sets the candidate to the stable version by removing `-SNAPSHOT`. It then
  generates the changelog for that version, updates the maintained
  documentation, and runs the documentation-version checker.
- It opens or updates a PR with the `release` label. Reviewers approve the
  version, the changelog, and the documentation together with the code changes
  collected since the last release.
- Conventional Commits only group changelog entries. They do not override the
  candidate in the root Gradle build.

### `release-publish.yml` runs the reviewed release

- The workflow runs only when a PR with the `release` label is merged into
  `main`.
- It uses `pull_request_target` for the trusted workflow definition. Before
  checkout, it checks that the trusted base-branch commit of the event exactly
  matches the merge commit of the PR. It checks out only the trusted event SHA.
  It never uses a ref that the PR controls, and never runs an unmerged PR head
  with release permissions.
- The merged root version must be stable and must match the changelog and the
  maintained docs. The workflow reruns the release fixtures, the Linux CI
  gates, the plugin ABI and coverage checks, the TestKit integration tests,
  and the Central Portal bundle dry run.
- After all checks pass, the workflow creates `v<version>` on the reviewed
  merge commit. A retry may reuse the tag only if the tag points to that exact
  commit. If a conflicting tag already exists, the workflow fails closed.
- The workflow builds, signs, and deploys all published artifacts to Central
  Portal. Publication stays behind the `release` GitHub Environment, which a
  maintainer approves by hand.
- After a successful publication, the workflow creates or updates a separate
  PR from a managed branch. That PR moves the root version and the development
  documentation to the next `-SNAPSHOT`. The workflow does not push this commit
  directly to `main`.

### Supporting scripts and secrets

- `version-bump.sh` reads the canonical root version. It removes `-SNAPSHOT`
  for the release candidate, advances the minor version for the next
  development cycle, and generates changelog sections from the commits since
  the latest tag.
- `sync-version-references.py` updates the maintained consumer docs for either
  a stable release candidate or a development snapshot.
- The docs-version checker compares development references with the root
  version, and published references with the newest `CHANGELOG.md` entry. It
  calls the read-only check mode of the synchronizer, so the two scripts always
  classify references the same way. Development references keep their role
  after a stable release. Wrapped prose and product coordinates under
  development or snapshot headings return to the next snapshot, while
  published references stay on the release.
- The check mode also rejects a paragraph that ties a Kompact version to a
  publication status or an API difference, such as "not on Maven Central" or
  "differs from". The release rewrites the version, so that claim would ship
  false in the tagged docs, as it did in the first `0.10.0` release PR.
- Central Portal tokens and the signing key stay secrets scoped to the
  environment. Automation uses `RELEASE_PAT` only where it must push a topic
  branch or a tag, and create a PR whose checks run normally.

## Alternatives considered

1. Push the release commit and the next-SNAPSHOT commit directly to `main`.
   Rejected: it violates G1 and skips review of release metadata.
2. Derive release versions from Conventional Commits. Rejected: the derived
   version can disagree with the root candidate. Commits stay useful for
   grouping the changelog.
3. Migrate to release-please or a Maven publishing plugin. Rejected for this
   change: the current Portal Publisher already handles publication and hides
   credentials in its output. Replacing it does not help with the governance
   requirement.
4. Release by hand. Rejected: reproducible version, artifact, and publication
   gates belong in CI.

## Consequences

- Release metadata and next-development metadata are PRs that people can
  review and audit. No automation pushes directly to the protected default
  branch.
- Each release needs an extra next-SNAPSHOT PR after publication.
- If the release fails after the tag exists but before publication, inspect
  the tag and the Central Portal state before you retry. Reusing the tag is
  safe only when its commit matches the reviewed merge.
- If the release fails after publication but before the next-SNAPSHOT PR
  opens, the published release stays valid. Rerunning the follow-up job must
  create or update the PR without publishing again.
- Behavior tests and allocation evidence on physical iOS Arm64 and Android
  Native Arm64 devices stay independent release gates, documented in
  [`docs/ci.md`](../ci.md). Simulator results do not count as proof on a
  physical device. The project makes no zero-allocation claim without
  validated per-target counters and positive controls.

## Migration

Before the next release, replace the old post-merge version and changelog
commit, and its direct push to `main`, with the reviewed candidate PR. Before
you retry a release that is in flight, reconcile it against the root version,
the changelog, any existing tag, and the Central Portal state. This ADR does
not change external branch-protection settings. Maintainers must require the
actual macOS and Linux CI checks of the repository.
