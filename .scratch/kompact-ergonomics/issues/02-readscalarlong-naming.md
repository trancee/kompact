Type: grilling
Status: resolved
Blocked by: —  (unblocked 2026-09-05 by ticket 01 — the function stays; the rename is its own decision)

# 02 — `readScalarLong` name misleads (accepts 1..64, reads as 64-bit)

## Question

`KompactRuntime.readScalarLong(raw, bitOffset, bitWidth, signed)` takes
a `bitWidth: Int` in `1..64`, but the name suggests a fixed 64-bit
read (the way the legacy `readInt64` / `readUInt64` overloads used to
work). A caller passing `bitWidth = 8` gets a `LongResult` back, but
the name "Long" reads as a width guarantee, not a width range. This
scored P2 in the prior code-review of `feat/laguna` (Fowler's
*Mysterious Name*).

The decision: do we rename `readScalarLong` (and its companion
`writeScalar` on the writer, which has the same issue), or does
ticket 01's data-clump resolution collapse the two functions into one
parametric `readScalar` that returns a `Result` chosen by the value
class? If the answer to ticket 01 is "collapse," this ticket
auto-resolves; if the answer is "keep them separate," pick a name
that does not mislead (`readScalarWide`, `readScalar64`, or a
`ScalarType`-driven form).

## Context for the claiming session

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactRuntime.kt`
  — `readScalarLong` (lines ~162–176). Signature:
  `readScalarLong(raw: ByteArray, bitOffset: Int, bitWidth: Int, signed: Boolean): LongResult`.
- The result class for the 1..64 band is `LongResult`, which uses a
  sentinel range near `Long.MIN_VALUE` for failure (see
  `docs/architecture.md#runtime-error-encoding`). Renaming the
  *function* does not change the *result type*; the question is
  about the function's name only.
- `KompactRuntime.readBitsLong` (the raw primitive) has the same
  1..64 width range and is a *raw* accessor (no bounds check, no
  typed result). It is documented alongside the checked accessors
  in `docs/api-reference.md`. This ticket is about the *checked*
  accessor; the raw primitive's name is consistent with the legacy
  `readBits` / `readBitsLong` pair and is not in scope.
- The tutorial in `docs/getting-started.md` does not use
  `readScalarLong` (it only uses `readScalar` for the 4-bit battery
  and 10-bit speed). Renaming is therefore a public-API-only
  change with no tutorial edit required.

## Open sub-questions

1. If ticket 01's data-clump is resolved by collapsing the two
   functions into one parametric `readScalar` (with the result type
   chosen by `ScalarType`), this ticket is auto-resolved. Confirm
   or deny that dependency.
2. If kept as two functions, is the right rename `readScalarWide`
   (matches "wide" as the Kotlin term for ≥33-bit integers), or
   `readScalar64` (matches the legacy `Int64` / `UInt64` shape but
   is *also* misleading because the function still takes a
   `bitWidth` parameter), or some other name?
3. Does the writer's `writeScalar` (single function, takes 1..64
   bits) need a rename for consistency, or is the writer side
   fine because the `Long` value type already signals the range?

## What "resolved" looks like

- The chosen name is recorded under `## Answer` (or this ticket
  auto-resolves with a "see ticket 01" gist if the collapse is
  chosen).
- If a rename is chosen, the new name is noted along with the
  rationale (so the implementation commit can apply it).
- `docs/api-reference.md` is updated (or scheduled for the
  implementation commit) to use the new name.

## Answer

**Decision: rename `readScalarLong` → `readScalarAsLong`. The `As` prefix
makes the result type (`LongResult`, backed by a primitive `Long`)
unambiguous and matches Kotlin's "as" pattern (`getOrThrow`, `as`
casts, `asReversed`). The no-suffix `readScalar` (implicit-`Int`
variant, returns `IntResult`) stays — it is not misleading.**

The four sub-questions, settled:

- **Does 01's resolution auto-close 02?** No. Ticket 01 explicitly
  kept the reader split (`readScalar` 1..32 → `IntResult` vs.
  `readScalarLong` 1..64 → `LongResult`); the long-variant
  function stays and needs a name that doesn't mislead.
- **The right rename** — `readScalarAsLong` (the `As` prefix
  signals "viewed as the destination type," matching the
  stdlib-style suffix reading that Kotlin's `Int.toLong()`
  already establishes: the suffix is the *result* type, not a
  width guarantee). `readScalar` stays as the implicit-`Int`
  variant.
- **Writer side** — no rename. The writer's
  `writeScalar(type: ScalarType, value: Long)` takes a `Long`
  value (zero-extended for widths ≤32, true `Long` for
  32..64); the `Long` here is unambiguously the value type
  and is not misleading.
- **Pair symmetry (rejected)** — the alternative
  `readScalarInt` + `readScalarLong` (both suffixed) was
  considered and rejected: the no-suffix `readScalar` reads
  naturally as "the read-scalar function" and is not the one
  with a misleading name. Renaming `readScalar` to
  `readScalarInt` is a larger public-API break for no clarity
  gain.

### Sketch (for the implementation commit)

```kotlin
// KompactRuntime — rename only, signature unchanged from ticket 01
- public inline fun readScalarLong(raw: ByteArray, bitOffset: Int, type: ScalarType): LongResult
+ public inline fun readScalarAsLong(raw: ByteArray, bitOffset: Int, type: ScalarType): LongResult
```

The internal dispatch and the `LongResult` failure encoding
(sentinel near `Long.MIN_VALUE`, per `docs/architecture.md#runtime-error-encoding`)
are unchanged.

### Propagation

- **Ticket 09** (Test file split) — **unblocked (already was)**.
  If 09 splits per-accessor, the long-band test file is named
  `KompactRuntimeReadScalarAsLongTest.kt` (not
  `…ReadScalarLongTest.kt`). If 09 splits by result kind, the
  `LongResult`-coverage file name is unchanged. Note in 09's
  body; do not resolve.
- **Writer** — no rename. The writer's `writeScalar(type, value:
  Long)` is not misleading (the `Long` is the value type, not
  a width).
- **Docs** — `docs/api-reference.md#checked-typed-read-accessors`
  table mentions `readScalarLong` by name; the implementation
  commit updates it to `readScalarAsLong`. `docs/getting-started.md`
  does not use `readScalarLong` (only `readScalar` for the
  4-bit battery and 10-bit speed), so no tutorial edit.
- **Implementation commit scope** — pure rename. No behavior
  change. No klib golden regen needed (the *function name* is
  part of the public ABI but the change is a one-symbol rename;
  BCV's `apiCheck` will flag the diff and the macOS
  `regen-goldens.yml` workflow regenerates the klib golden in
  the same commit, matching the map's "Standing preference"
  rule ("regenerate the goldens in the same commit" — see
  `map.md#notes`).

## Comments
