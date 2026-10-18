# ADR-0007: Pre-1.0 breaking-change versioning

- Status: Accepted
- Date: 2026-09-27
- Tags: governance, release, semver
- Amends: Constitution Q6 and [ADR-0004](0004-release-pr-automation.md)

## Context

The `0.4.0-SNAPSHOT` development cycle contains a deliberate breaking API
change to `LongResult`. The release script treated every Conventional Commit
breaking marker as a major bump. That changed the generated release PR from
`0.4.0` to `1.0.0`.

Kompact is still explicitly pre-stable. A `1.0.0` release would announce a
stable compatibility commitment that the project is not ready to make.

## Decision

While the major version is `0`, a breaking change to a public or serialized
contract increments the MINOR version and resets PATCH to zero. From `1.0.0`
on, a breaking contract change increments MAJOR. In both phases, additive
changes increment MINOR and compatible fixes increment PATCH.

The release version script enforces this rule. Its CI regression test covers
both forms of the breaking marker, both sides of the `1.0.0` boundary,
additive features, and compatible fixes.

## Alternatives considered

1. Release `1.0.0`. Rejected: it would declare API stability too early.
2. Remove the breaking marker from history. Rejected: the changelog must keep
   identifying the compatibility break correctly.
3. Add a one-off `0.4.0` override. Rejected: an undocumented bypass would leave
   future pre-stable releases unclear.

## Consequences

- Consumers before `1.0` must read the MINOR release notes for migration
  steps.
- Tooling that assumes every breaking marker means a major increment must
  treat the release script of the repository as the source of truth.
- After `1.0.0` is released, the automation switches to major increments for
  breaking changes with no further policy change.

## Migration

The `0.4.0` release applied this policy, and later releases keep the same
pre-`1.0` rule. The policy itself adds no artifact or wire-format migration.

## Approval

The repository maintainer approved the pre-`1.0` MINOR policy on 2026-09-27.
The Constitution version advanced from `2.0.1` to `3.0.0`, because this
changes a normative release obligation.
