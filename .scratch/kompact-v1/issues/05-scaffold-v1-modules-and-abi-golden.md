---
Type: task
Status: ready-for-agent
Labels:
  - wayfinder:task
  - scope:build
Blocked by:
  - "none (tickets 01/02/03 resolved → v1 shape finalized)"
Decides:
  - "v1.0 baseline module layout + ABI golden locked in main"
---

## Question

Verify and lock the **existing** v1.0 baseline in `main` (the implementation was
already integrated from `feat/laguna` and released as 0.1.0–0.1.7; `main` is now
`0.2.0-SNAPSHOT`). Now that the v1 shape is finalized (tickets 01/02/03 resolved),
confirm the baseline that the locked spec ships against is stable and green.

## Acceptance

- Module layout present in `main` matches kompact-spec
  [ticket 12](../../kompact-spec/issues/12-module-split-and-publication.md):
  `:kompact` (JVM + `iosArm64` + `iosSimulatorArm64`) + `:kompact-ksp` (JVM-only).
- `binary-compatibility-validator` goldens are **committed** for v1.0 and
  `checkKotlinAbi` green on the v1 shape:
  `./gradlew :kompact:checkKotlinAbi :kompact-ksp:checkKotlinAbi` exits 0
  (locally: JVM + kompact-ksp; klib iosArm64 ABI gate is CI-only / needs the iOS
  toolchain — verified via CI, not local).
- v1 merge gates green on the baseline: `:kompact-ksp:koverVerifyJvm` 100%,
  `:kompact-ksp:test` passing (zero-alloc assertions, tickets 10/11).
- **Alignment:** verified against `main` (where `feat/laguna` was integrated and
  released) — **not** against stale branches.

## Notes

- The v1 shape is now finalized by tickets 01 (tiered results) + 02 (immutable
  views) + 03 (fixed-width LE framing) — all resolved on the recommended-default
  path. This ticket is **unblocked** and is the current agent frontier.
- If the ratified shape requires a codegen change in `main` (e.g. immutable
  views / tiered results), that becomes a follow-on implementation ticket —
  scoped to the v1 build, out of this planning map's scope.
