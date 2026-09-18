---
Type: map
Status: active
Labels:
  - wayfinder:map
---

# Wayfinder Map: Kompact v1.0 implementation

## Destination

Implement the locked Kompact spec end to end as **Kompact v1.0**: the
`:kompact` KMP runtime (JVM + `iosArm64` + `iosSimulatorArm64`), the
`:kompact-ksp` JVM processor, and Maven Central + klib publication with a
**locked ABI** (`binary-compatibility-validator` golden). v1 ships the locked
spec shape with **no additions**. Mandatory v1 merge gates: zero-alloc scalar
read (ticket 10), per-platform allocation profiling + continuous fuzzing
(tickets 10/11), `kover` coverage, and `checkKotlinAbi` on JVM + klibs. Android
ships via the JVM artifact only; C emission, watchOS, and macOS / linux /
windows Native targets are out of scope.

## Notes

- **Domain:** bit-packed, zero-allocation Kotlin Multiplatform serialization for
  BLE-style frames (`ByteArray` views, `Long`-packed result types, sequential
  length-delimited framing).
- **Source of truth:** the spec is locked in [`.scratch/kompact-spec/map.md`](../kompact-spec/map.md)
  (decisions 01–12). This map finds its way to *implementing* that locked spec,
  not re-deciding it — **except** the external-review proposals that the v1
  scope decision (below) made blocking.
- **Standing preference applied:** the 3 unratified review proposals that
  contradicted ratified decisions were resolved on the recommended-default path
  (per your "a=recommended" standing instruction; vetoes invited). The v1 shape
  is now **finalized**.
- **Skills:** wayfinder (this map), grill-me / domain-modeling (decision tickets), research (AFK investigations).
- **Tracker:** local markdown (`.scratch/`); `wayfinder:<type>` in frontmatter `Labels:`.

## Decisions so far

- **v1 destination = implement the locked kompact-spec end to end** (JVM + iosArm64 + iosSimulatorArm64 runtime, `:kompact-ksp`, Maven Central + locked klib ABI; no scope additions).
- **v1 scope is exactly the locked matrix** — Android-as-JVM-artifact; C emission, extra Native targets, watchOS, separate Android target all OUT.
- **v1 merge gates are mandatory for v1** (zero-alloc assertion + per-platform alloc profiling + continuous fuzz + kover + ABI), not post-v1.
- **[Inspect v1 remote branches](issues/04-inspect-v1-remote-branches.md): all 5 stale; `feat/laguna` (the v1 impl) already squash-merged into `main` and released 0.1.0–0.1.7; verify/lock the baseline on `main`, not on stale branches.**
- **v1 shape finalized (recommended-default), tickets 01–03 resolved:**
  - **01 — accept ADR-0005:** tiered results — zero-alloc on success (read hot path, tickets 10/11 unchanged), `DecodeError` allocates only on failure; the 7 packed-`Long` types drop out of v1. [ticket 01](issues/01-ratify-fail-path-zero-alloc.md)
  - **02 — accept ADR-0006:** immutable-by-default views (`val` + builder), opt-in `Mutable*`; supersedes ADR-0001. [ticket 02](issues/02-ratify-immutable-default-models.md)
  - **03 — reject framing-width change:** keep ticket 05 fixed-width LE ({8,16,32}) prefixes (wire-incompatible MAJOR for marginal gain; preserves skip-unknown-field evolution). [ticket 03](issues/03-arbitrate-framing-prefix-widths.md)
- **First takeable agent step = verify/lock the v1.0 baseline in `main`** (module layout + committed ABI goldens + green gates): ticket 05, now **unblocked**.

## Not yet specified (fog)

- v1.0 version number + release window.
- Precise zero-alloc CI-gate harness details for the v1 build (see kompact-spec tickets 10/11).
- Exact Maven Central coordinates + signing infra (see kompact-spec ticket 14).
- (If ratified shape needs a codegen change in main → follow-on implementation ticket, out of this planning map's scope.)

## Out of scope

- C emission / C reference spec (also out per kompact-spec `map.md`).
- macOS / linuxX64 / windows Native + watchOS targets.
- Separate Android library target.
- Arbitrary / LEB128 framing widths in v1 — **REJECTED** (ticket 03); v1 keeps ticket 05 fixed-width LE. Stays deferred.
- Greenfield scaffolding `:kompact` / `:kompact-ksp` from scratch — the v1 baseline already lives in `main` (see research 04); ticket 05 verifies/locks it.

## Frontier

- **[05 — verify/lock v1.0 baseline in `main`](issues/05-scaffold-v1-modules-and-abi-golden.md)** — task, **OPEN** (agent frontier). 01/02/03 resolved; 04 resolved. Next work-through ticket.
- 01 / 02 / 03 / 04 — **resolved**.
