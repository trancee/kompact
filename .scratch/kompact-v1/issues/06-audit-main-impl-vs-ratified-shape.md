---
Type: research
Status: resolved
Labels:
  - wayfinder:research
  - scope:impl
Blocked by:
  - "05 verify/lock v1.0 baseline"
Decides:
  - "whether main's v1 impl matches the ratified shape (ADR-0005/0006/0003)"
Resolved by:
  - "source-level audit of kompact/ + committed ABI golden (see Resolution)"
---

## Question

Does the v1 implementation already in `main` match the shape just ratified by
tickets 01–03?

- [01](01-ratify-fail-path-zero-alloc.md): **tiered results** — zero-alloc on the
  read **success** path, allocating `DecodeError(value, offset, kind, rawCode)` on
  failure (NOT the 7 packed-`Long` `*Result` types of ticket 08 that 0.1.x shipped).
- [02](02-ratify-immutable-default-models.md): **immutable-by-default** views
  (`val` + builder), opt-in `Mutable*` (NOT ADR-0001 mutable write-through).
- [03](03-arbitrate-framing-prefix-widths.md): **fixed-width LE** {8,16,32}
  framing (ticket 05 — unchanged).

## Resolution

**Outcome (b) — MISMATCH.** The v1 baseline in `main` is green + ABI-locked
(see [ticket 05](05-scaffold-v1-modules-and-abi-golden.md)), but its **current
shape does not match the ratified v1 shape** on results + views; framing matches.

### Results (01 / ADR-0005) — MISMATCH

`main` implements **ticket 08**, not ADR-0005. Evidence:

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactResult.kt`
  defines the **7 packed-`Long` result value classes** — `ByteResult`, `ShortResult`,
  `IntResult`, `FloatResult`, `BooleanResult`, `LongResult`, `DoubleResult` (+
  `NestedRegionResult`) — each packing success + error into one `Long` via
  `encodeSmallFailure` / `encodeLongFailure` / `encodeDoubleFailure` +
  `decode*Error` helpers.
- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactResultExtensions.kt`
  adds **per-type `getOrElse`/`map` on all seven** ("getOrElse / map extensions on
  the seven result value classes").
- Committed ABI golden confirms the 7 packed value classes:
  `kompact/api/jvm/kompact.api` (`ByteResult`…`DoubleResult`, `box-impl (J)`) and
  `kompact/api/kompact.klib.api` (`final value class …/BooleanResult` …).
- → **No** tiered/single result type and **no** `getOrDefault`; failures are
  still packed into the `Long`, not allocated as a `DecodeError`. **Refactor to
  ADR-0005 required.**

### Views (02 / ADR-0006) — MISMATCH

`main` implements **ADR-0001**, not ADR-0006. Evidence:

- Generated `kompact/src/jvmCommon/kotlin/generated/VehicleTelemetry.kt`
  exposes `public actual var batteryStatus: Int`, `var speed: Int`,
  `var isMalfunctioning: Boolean` — `var` write-through (reads use
  `KompactRuntime.readScalar(raw, …, ScalarType.of(…)).getOrThrow()`).
- `kompact-ksp/src/main/kotlin/ch/trancee/kompact/ksp/gen/ValueClassGenerator.kt`
  comment at line 37: `- \`var\` with write-through setters (ADR-0001)`; lines
  356/382: "Per-type in-place write call builders for value-class setters (ADR-0001
  write-through)".
- → **No** immutable-by-default + opt-in `Mutable*`. **Refactor to ADR-0006
  (codegen + ABI) required.**

### Framing (03) — MATCH

`main` keeps ticket 05 fixed-width LE {8,16,32} length prefixes — the ratified
decision (reject the change). No refactor.

### Conclusion

v1.0 **cannot ship from current `main` as-is**: the ratified shape (01/02) is
not implemented; collapsing the 7 result types → tiered + mutable `var` views →
immutable is a **pre-1.0 Major** refactor of the read-API + codegen, with a full
ABI-golden re-lock + re-green of the gates. This graduates to
[ticket 07](07-decide-v1-shape-resolution.md).
