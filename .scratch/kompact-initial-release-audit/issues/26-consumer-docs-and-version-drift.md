---
Type: task
Blocked by: 22, 23, 24
---

## Objective

Bring maintained consumer documentation into alignment with the resolved API,
schema, platform, and release-version contracts.

## Decisions

- Follow [the versioning policy](10-schema-versioning-policy.md),
  [the version decision](21-published-version-and-first-release.md), and the
  relevant generated API and platform tasks.
- Kompact frames remain versionless; applications that need mixed-version
  handling define their own outer envelope and migration policy.
- The root `build.gradle.kts` version is the canonical current candidate.
  Published `0.7.0` remains immutable and historical; release `0.8.0` only
  after the roadmap gates pass.

## Acceptance criteria

- Update README, getting-started, API/how-to, security, agent quick-start, and
  other maintained consumer pages to remove stale `0.6.1` and `0.7.0-SNAPSHOT`
  claims and show the right published/development coordinates.
- Document borrowed/lazy defaults, explicit copy-to-own operations,
  unchecked APIs, identity equality, supported-platform guarantees, and the
  caller-owned allocation boundary consistently.
- Add the agreed caller-owned outer-envelope example without adding a
  Kompact-level version header/helper.
- Add an automated CI check that compares development-version references with
  the root Gradle version and latest-published references with the latest
  changelog release entry. Release PR updates to docs and changelog must happen
  before publication.
- Validate relative links, spelling/markup, and executable or generated
  examples with repository-supported checks.

## Execution status (2026-10-07)

The maintained consumer guides identify `0.7.0` as the latest published
version and `0.8.0-SNAPSHOT` as the current development version. The
application-owned version-envelope example is in
[`docs/how-to/define-framed-schema.md`](../../../docs/how-to/define-framed-schema.md).
The checked/lazy ownership, unchecked mutation, identity-equality, supported
platform, and caller-owned allocation contracts are documented across the
consumer guide and API reference.

`.github/scripts/docs/check-version-references.sh` compares maintained
consumer-version references with the root Gradle version and newest changelog
entry. CI exercises that check and its release fixture; macOS CI regenerates
the Dokka API Markdown and rejects drift.

The repository does not configure a general relative-link or spelling
checker, and the guide's standalone application-envelope snippet is not
executed as a CI example. `spotlessCheck` and generated API Markdown checks
pass, but those do not validate every Markdown link or prose spelling.
Those remaining documentation checks must be addressed or explicitly accepted
before claiming every acceptance criterion is verified.
