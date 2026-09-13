Type: grilling
Status: resolved

# 01 — Data clump: `bitWidth` + `signed` should be one domain concept

## Question

`KompactRuntime.readScalar(raw, bitOffset, bitWidth, signed)` and
`KompactWriter.writeScalar(bitWidth, value)` take `bitWidth` and
`signed` as two separate parameters. They always travel together and
encode a single domain concept — the integer type descriptor (i8, u16,
i32, u64, …). This is Fowler's *Data Clump* smell, and it scored P2 in
the prior code-review of `feat/laguna`. The repo's own value-class
pattern (`KompactResult`, `KompactField`) is the natural place to
absorb it.

The decision: do we replace the `(bitWidth: Int, signed: Boolean)`
parameter pair with a single value-class argument (a `ScalarType` or
`IntegerKind` or similar)? If yes, what does the call site look like
(`readScalar(buf, off, ScalarType.INT_16)` vs an enum
`readScalar(buf, off, IntegerKind.SIGNED, 16)`), and does the change
also collapse `readScalar` and `readScalarLong` (touched by ticket
02) into one parametric function with the value class as the
discriminator?

This ticket is the front of the frontier. Ticket 02 (`readScalarLong`
naming) and ticket 04 (Result ergonomics layer) are blocked on this
decision because the rename ergonomics and the result-API surface
both depend on what a "scalar type" looks like at the call site.

## Context for the claiming session

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactRuntime.kt` —
  `readScalar` (lines ~138–160), `readScalarLong` (lines ~162–176).
- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactWriter.kt` —
  `writeScalar` (lines ~53–61), which has `bitWidth` but no `signed`
  flag (the writer doesn't need to know sign because the bits are
  stored as a two's-complement magnitude regardless of the caller's
  intent — but see the open question below).
- The packed-Long error encoding in `KompactResult.kt` already
  reserves `bits 59..48` for `rawEnumCode` — a future `ScalarType`
  might benefit from reserving a small range for an enum ordinal
  rather than the current `signed: Boolean` (decide as part of this
  ticket).
- `docs/api-reference.md#checked-typed-read-accessors` and
  `docs/getting-started.md` (the 16-bit tutorial) both document the
  current `readScalar(buf, off, width, signed)` shape; both will
  need to be re-aligned with whatever this ticket decides.
- `GettingStartedTest` in `kompact/src/commonTest/.../runtime/` is
  the executable journey that pins the tutorial wire bytes
  (`0xA5 0x40`); it must be updated alongside any signature change
  so the tutorial's `readScalar(bytes, 0, 4, signed = false)` example
  still compiles and still asserts the same wire bytes.

## Open sub-questions (answer as part of this ticket)

1. **Value class shape** — `ScalarType(bitWidth: Int, signed: Boolean)`
   with named constants (`ScalarType.UINT_8`, `ScalarType.INT_32`,
   …), or a sealed hierarchy of value classes (`UIntScalar`,
   `IntScalar` parameterized by width), or an enum (less idiomatic for
   parameterized data)?
2. **The writer side** — does `writeScalar(bitWidth, value)` also
   take a `ScalarType` (for symmetry) or keep its current `Long`
   value parameter and only the reader adopts the value class?
3. **The `signed: Boolean` on the writer** — the writer currently
   takes a `value: Long` and writes the low `bitWidth` bits as a
   two's-complement magnitude. Should the value class also carry the
   sign intent so a `writeScalar(ScalarType.INT_8, -1L)` and a
   `writeScalar(ScalarType.UINT_8, 255L)` produce the same wire bits
   (currently they do, because the writer just stores the low bits
   without sign information)?
4. **The `readScalar` / `readScalarLong` split** — does this ticket
   collapse them (one `readScalar(buf, off, type: ScalarType): Result`
   where the result kind is decided by `type`) or keep them separate
   for the 1..32 vs 1..64 width band? See ticket 02.
5. **Test file split** — if the reader signature changes, ticket
   09 (test file split) is unblocked; this ticket should record the
   intended new accessor shape so ticket 09 can plan its file
   layout.

## What "resolved" looks like

- A single design choice is recorded under `## Answer` (the value
  class shape, the writer-side question, and the
  `readScalar`/`readScalarLong` outcome).
- The chosen shape is sketched in 5–15 lines of Kotlin (a type
  declaration and 2–3 example call sites) so a follow-up
  implementation ticket can apply it directly.
- The downstream tickets (02, 04, 09) are updated if the chosen
  shape changes their scope (e.g. if 01 collapses `readScalar` and
  `readScalarLong`, ticket 02 closes as completed and the gist is
  moved to "Decisions so far" in the map).

## Answer

**Decision: single value class `ScalarType(bitWidth, signed)` with named companion
constants; both `readScalar` / `readScalarLong` (readers) and `writeScalar` (writer)
adopt it. Keep the reader split. No enum axis — enums are read via `readScalar` and
the `UnknownEnumCode` typed error is inspected on the result.**

The four sub-questions, settled one at a time:

- **Shape (Q1)** — single `value class ScalarType` over a `Long` (zero-alloc on
  both JVM and iOS per ticket 03), constructed via an `internal` constructor and
  exposed through named factory constants in the companion for the common
  JVM/Kotlin primitive lanes (`INT_8`, `INT_16`, `INT_32`, `INT_64`, `UINT_8`,
  `UINT_16`, `UINT_32`, `UINT_64`, `BOOL`) plus a generic `of(bitWidth, signed)`
  factory for the rare 1..63 widths in between. The value class is
  `@JvmInline` on the JVM and plain `value class` on iOS (ticket 03's
  representation rule).
- **Reader split (Q2)** — keep `readScalar` (1..32 → `IntResult`) and
  `readScalarLong` (1..64 → `LongResult`) as two functions. Collapsing would
  either box on the success path (violates ticket 03's zero-alloc read
  contract) or force the caller to cast the result. The split is intrinsic to
  the JVM/Kotlin primitive lanes; the value class does not change it. Ticket
  02 (`readScalarLong` naming) is now *unblocked* but does *not* auto-resolve:
  the long-variant function stays, and its name still needs work.
- **Writer side (Q3)** — adopt the value class: `writeScalar(type: ScalarType,
  value: Long)`. The writer is not zero-alloc-constrained (per
  `docs/architecture.md` and ticket 07), so the allocation of constructing a
  `ScalarType` at the call site is acceptable. Symmetry with the reader is
  the win — the call site uses the same `ScalarType.INT_16` constant for both
  `readScalar` and `writeScalar`.
- **Enum axis (Q4)** — *no* `ScalarKind` field on the value class. The packed
  result `Long` already reserves `bits 59..48` for the raw enum code (per
  `KompactResult.kt`'s `RESULT_RAW_ENUM_SHIFT = 48`); the reader returns
  `IntResult` and the caller inspects `result.error is KompactDecodeError.UnknownEnumCode`
  for the fail-closed behavior ticket 04 mandates. Keeping the enum case on
  the same `readScalar` access keeps the API small; the alternative (a
  separate `readEnum`) is a separate ticket's decision.

### Sketch (for the implementation commit)

```kotlin
// kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/ScalarType.kt
@JvmInline
public value class ScalarType internal constructor(private val packed: Long) {
    public val bitWidth: Int  get() = (packed ushr 32).toInt() and 0x3F  // 1..64
    public val signed: Boolean get() = (packed and 0x8000_0000L) != 0L

    public companion object {
        public val INT_8:  ScalarType = ScalarType(8,  signed = true)
        public val INT_16: ScalarType = ScalarType(16, signed = true)
        public val INT_32: ScalarType = ScalarType(32, signed = true)
        public val INT_64: ScalarType = ScalarType(64, signed = true)
        public val UINT_8:  ScalarType = ScalarType(8,  signed = false)
        public val UINT_16: ScalarType = ScalarType(16, signed = false)
        public val UINT_32: ScalarType = ScalarType(32, signed = false)
        public val UINT_64: ScalarType = ScalarType(64, signed = false)
        public val BOOL:    ScalarType = ScalarType(1,  signed = false)
        public fun of(bitWidth: Int, signed: Boolean): ScalarType =
            ScalarType(bitWidth, signed)
    }
}

// KompactRuntime — new signatures
public inline fun readScalar(raw: ByteArray, bitOffset: Int, type: ScalarType): IntResult
public inline fun readScalarLong(raw: ByteArray, bitOffset: Int, type: ScalarType): LongResult

// KompactWriter — new signature
public fun writeScalar(type: ScalarType, value: Long)
```

### Call sites (matching the current `GettingStartedTest` / `docs/getting-started.md`)

```kotlin
// Reader — was: readScalar(bytes, 0, 4, signed = false)
val battery: Int = KompactRuntime.readScalar(bytes, 0, ScalarType.UINT_8).getOrThrow()

// Writer — was: writeScalar(bitWidth = 4, value = 5L)
val w = KompactWriter()
w.writeScalar(ScalarType.UINT_8, 5L)
w.writeScalar(ScalarType.UINT_16, 10L)
w.writeBool(true)
```

### Propagation

- **Ticket 02** (`readScalarLong` naming) — **unblocked**. The function stays;
  the naming decision is its own. Note in 02's body.
- **Ticket 04** (Result ergonomics layer) — **unblocked**. The result types are
  unaffected by the parameter shape. 04's recovery-pattern decision is
  independent.
- **Ticket 09** (Test file split) — **unblocked**. The test file can now plan
  against the new accessor signature; per-accessor file names will follow
  the new shape (e.g. `KompactRuntimeReadScalarTest` exercises
  `readScalar(raw, off, type: ScalarType)` for the common 8/16/32 lanes).
- **Ticket 07** (`VehicleTelemetry` example alignment) — **not auto-resolved**,
  but the "teach the public API" choice becomes the natural one: if the
  example getters use the new `readScalar` signature, the example teaches
  the public API a newcomer will actually use. 07's "raw `readBits` shape"
  option is still valid (codegen-output reference) but is less central.
  Note in 07's body; do not resolve.

### Implementation steps (for the follow-up commit)

1. Add `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/ScalarType.kt`
   with the value class and the named constants.
2. Update `KompactRuntime.readScalar` / `readScalarLong` signatures to take
   `type: ScalarType`. The internals (`readBits` / `readBitsLong` calls)
   use `type.bitWidth` and `type.signed`.
3. Update `KompactWriter.writeScalar` signature to take `type: ScalarType`.
   The internal dispatch (`writeBits` vs `writeBitsLong`) uses
   `type.bitWidth`.
4. Update `KompactRuntimeCheckedReadTest.kt` (441 lines) to use the new
   signature. This naturally sets up the ticket 09 split.
5. Update `GettingStartedTest.kt` to use the new signature; re-run
   `:kompact:jvmTest` to confirm green and the wire bytes (`0xA5 0x40`)
   still match.
6. Update `docs/getting-started.md` and `docs/api-reference.md` to use the
   new signature. `VehicleTelemetry` getters stay on the raw `readBits`
   path until ticket 07 resolves.
7. Regenerate the iOS klib golden on macOS via the `regen-goldens.yml`
   workflow (the public ABI shifts: every `(Int, Boolean)` accessor
   signature becomes `(Int, ScalarType)`, plus the new `ScalarType` class
   and its `Companion`).
8. Push to `feat/laguna`; CI (`apiCheck` macOS + `jvmTest` Linux) should
   stay green after the golden regen.

## Comments
