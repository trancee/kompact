# ADR-0007 — Pre-1.0 breaking-change versioning

- **Status:** Accepted
- **Date:** 2026-09-27
- **Tags:** governance, release, semver
- **Amends:** Constitution Q6 and ADR-0004

## Context

The `0.4.0-SNAPSHOT` development cycle contains a deliberate breaking API
change to `LongResult`. The release script previously treated every
Conventional Commit breaking marker as a major bump, which changed the
generated release PR from `0.4.0` to `1.0.0`.

Kompact is still explicitly pre-stable. Advancing to `1.0.0` would communicate
a stable compatibility commitment that the project is not ready to make.

## Decision

While the current major version is `0`, a breaking public or serialized
contract increments the MINOR version and resets PATCH to zero. Starting with
`1.0.0`, a breaking contract increments MAJOR. Additive changes remain MINOR
and compatible fixes remain PATCH in both phases.

The release version script enforces this rule and its CI regression test covers
both breaking-marker forms, both sides of the `1.0.0` boundary, additive
features, and compatible fixes.

## Alternatives

1. **Release `1.0.0`.** Rejected because it would prematurely declare API
   stability.
2. **Remove the breaking marker from history.** Rejected because the changelog
   must continue to identify the compatibility break accurately.
3. **Add a one-off `0.4.0` override.** Rejected because an undocumented bypass
   would leave future pre-stable releases ambiguous.

## Risks

- Pre-`1.0` consumers must inspect MINOR release notes for migration
  requirements.
- Tooling that assumes every breaking marker means a major increment must use
  the repository release script as the source of truth.
- Once `1.0.0` is released, the automation switches to major increments for
  breaking changes without another policy change.

## Migration

This policy was applied when `0.4.0` was released; future releases continue to
use the same pre-`1.0` rule. No artifact or wire-format migration is introduced
by the policy itself.

## Approval

The repository maintainer approved the pre-`1.0` MINOR policy on 2026-09-27.
The Constitution version advances from `2.0.1` to `3.0.0` because this changes
a normative release obligation.
