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
- **Standing preference:** the 3 unratified review proposals are *decision-blockers
  for v1* — ratify them before any build work, because they change the ABI the
  v1 golden locks.
- **Skills:** wayfinder (this map), grill-me / domain-modeling (decision tickets), research (AFK investigations).
- **Tracker:** local markdown (`.scratch/`); `wayfinder:<type>` in frontmatter `Labels:`.

## Decisions so far

- **v1 destination = implement the locked kompact-spec end to end** (JVM + iosArm64 + iosSimulatorArm64 runtime, `:kompact-ksp`, Maven Central + locked klib ABI; no scope additions).
- **v1 scope is exactly the locked matrix** — Android-as-JVM-artifact; C emission, extra Native targets, watchOS, separate Android target all OUT.
- **Unratified review proposals (ADR-0005 fail-path allocation, ADR-0006 immutable-by-default, arbitrary framing widths) BLOCK v1** until ratified.
- **v1 merge gates are mandatory for v1** (zero-alloc assertion + per-platform alloc profiling + continuous fuzz + kover + ABI), not post-v1.
- **First takeable agent step = scaffold modules + v1.0 ABI golden** — blocked on the three ratifications above.

## Not yet specified (fog)

- v1.0 version number + release window.
- State of the remote branches `feat/kompact-v1`, `prototype/c99-interface`, `prototype/kotlin-interface`, `feat/minimax`, `feat/laguna` → [ticket 04](issues/04-inspect-v1-remote-branches.md) (research).
- Precise zero-alloc CI-gate harness details for the v1 build (see kompact-spec tickets 10/11).
- Exact Maven Central coordinates + signing infra (see kompact-spec ticket 14).

## Out of scope

- C emission / C reference spec (also out per kompact-spec `map.md`).
- macOS / linuxX64 / windows Native + watchOS targets.
- Separate Android library target.
- Arbitrary / LEB128 framing widths in v1 — wire-incompatible with ticket 05; stays deferred until the framing-width decision ([ticket 03](issues/03-arbitrate-framing-prefix-widths.md)) is resolved (if rejected, it remains out of v1 scope).

## Frontier

Open, unclaimed children of this map:
- [01 — ratify fail-path zero-alloc (ADR-0005 vs ticket 08)](issues/01-ratify-fail-path-zero-alloc.md) — grilling
- [02 — ratify immutable-by-default models (ADR-0006 vs ADR-0001)](issues/02-ratify-immutable-default-models.md) — grilling
- [03 — arbitrate framing prefix widths (vs ticket 05)](issues/03-arbitrate-framing-prefix-widths.md) — grilling
- [04 — inspect remote v1 branches](issues/04-inspect-v1-remote-branches.md) — research
