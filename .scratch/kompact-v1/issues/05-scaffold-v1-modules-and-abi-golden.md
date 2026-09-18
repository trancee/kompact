---
Type: task
Status: resolved
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

## Resolution

**Baseline verified green** on `wayfinder/kompact-v1` (= `main` + wayfinder docs;
the `kompact`/`kompact-ksp` source is the integrated v1 baseline from
`feat/laguna`, released 0.1.0–0.1.7).

- `./gradlew :kompact:checkKotlinAbi` → **BUILD SUCCESSFUL** (exit 0; 18 tasks,
  3 executed, 5 from cache, 10 up-to-date) — JVM + `iosArm64` + `iosSimulatorArm64`
  klib + JVM ABI golden verified locally (Java 25 / Xcode present; CI gate confirmed
  runnable macOS arm64).
- `./gradlew :kompact-ksp:checkKotlinAbi :kompact-ksp:koverVerifyJvm :kompact-ksp:test`
  → **BUILD SUCCESSFUL** (exit 0; 20 tasks, 2 executed, 18 up-to-date; kover 100%,
  tests green).
- Module layout = `:kompact` (JVM + iosArm64 + iosSimulatorArm64) + `:kompact-ksp`
  (JVM), matching kompact-spec [ticket 12](../../kompact-spec/issues/12-module-split-and-publication.md).
- Alignment: verified against `main` (not stale branches), per [research 04](../research/04-remote-branch-inspection.md).

**Conclusion:** the v1.0 baseline in `main` is locked + green on the
ABI/kover/test gates and the v1 shape (01–03) is finalized. Caveat: whether
`main`'s *current* read-API/views match the newly-ratified shape (01/02) is not
yet checked — graduated into [ticket 06](06-audit-main-impl-vs-ratified-shape.md).
