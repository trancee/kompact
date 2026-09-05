Type: grilling
Status: resolved
Blocked by: —  (unblocked 2026-09-05 by ticket 04 — the ergonomics pattern is settled: `…OrThrow` function-level wrappers for the throw-on-failure case, typed-result companion + `getOrElse`/`map` extensions for the recovery case, and a nullable internal for the hot path. 05 picks a name and applies the same pattern.)

# 05 — `KompactFraming.nestedRegionOrNull` should have a typed-result companion

## Question

`KompactFraming.nestedRegionOrNull(raw, bitOffset, prefixBitWidth): Pair<Int, Int>?`
returns a nullable pair for a parse-forward nested region. The
nullable return is the right call for the framing hot path (no
allocation, no throw) — but it forces every consumer to repeat the
same `if (region == null) TypedError else use(start, length)`
boilerplate. The writer has a `writeNested` that mirrors the
`nestedRegionOrNull` *shape*; the reader should have a typed-result
companion that mirrors the writer's surface, e.g.
`KompactFraming.nestedRegion(raw, off, w): NestedRegionResult` with
`getOrThrow()` returning the `(startBit, bitLength)` pair.

The decision: do we add the typed-result companion, and if yes, is
it the same shape as the result ergonomics layer chosen in ticket 04
(a single `NestedRegionResult` with the `success` / `failure` /
`error` / `getOrThrow` pattern), or does ticket 04's resolution
automatically provide it (e.g. if ticket 04 picks a single
namespace, the framing helper adopts the namespace's `NestedRegion`
result)?

## Context for the claiming session

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactFraming.kt`
  — `nestedRegionOrNull` (lines ~66–84). The function reads the
  byte-count length prefix at `bitOffset` (width `prefixBitWidth` ∈
  `{8, 16, 32}`), validates against `KompactRuntime.fits`, and
  returns the sub-region as `(startBit, bitLength)`. Returns
  `null` on overrun (which the caller maps to a typed
  `TruncatedNested` / `BadLengthPrefix`).
- The companion reader for the *count* prefix
  (`readLengthPrefix`, lines ~32–41) returns `Int` directly (with
  `-1` on failure) — also a "no allocation" hot-path pattern.
  Should the count-prefix reader also get a typed-result
  companion, or is `-1` the right shape for that one (since the
  count is a single `Int`, not a pair)?
- The writer's `writeNested` and `writeRepeated` in
  `KompactWriter.kt` (lines ~78–107) are the user-facing
  symmetric shape. A consumer writing a nested payload with
  `writeNested { ... }` and then reading it back with
  `nestedRegionOrNull` is crossing the "nullable pair vs.
  block" boundary twice; the typed-result companion makes the
  read symmetric with the write.
- The v1 spec map's ticket 05 ("Framing — sequential
  length-delimited") and ticket 08 ("Runtime error model") are
  the locked references. Neither forbids a typed-result framing
  helper; both require the success path to be allocation-free.

## Open sub-questions

1. **Naming** — `nestedRegion` (vs the current
   `nestedRegionOrNull`) is the natural symmetric name, with the
   `?` suffix signaling the nullable variant. Confirm or propose
   an alternative.
2. **The result type** — a new `NestedRegionResult` value class
   wrapping `(startBit: Int, bitLength: Int)` (a `Pair<…>`-shaped
   payload), or a different shape (e.g. a `Long` packing
   `startBit << 32 | bitLength`, but that's an internal encoding
   detail and would leak).
3. **Count-prefix companion** — should `readLengthPrefix`'s `-1`
   return also become a typed result, or is that out of scope for
   this ticket (the user-facing pair is what newcomers see first)?
4. **`nestedRegionOrNull` deprecation** — once the typed-result
   companion lands, does the nullable form stay (as a hot-path
   internal), get deprecated (Q7: 1-MINOR cycle), or get removed
   immediately? Per the v1 release status (not yet on Maven
   Central), the deprecation cycle is cheap; pick the
   user-friendliest path.

## What "resolved" looks like

- The chosen shape (or "no, the nullable pair is the right call")
  is recorded under `## Answer` with a sketch of the new
  companion's signature.
- If a new companion is added, the relationship to ticket 04's
  ergonomics layer is recorded (does it use the layer's
  namespace, or stand alone?).
- The deprecation question (keep / deprecate / remove
  `nestedRegionOrNull`) is answered.

## Answer

**Decision: full ergonomics pair, plus the count-prefix `…OrThrow`
companion. The nullable `nestedRegionOrNull` demotes to `internal`
(kept as the hot-path internal that the new public functions
delegate to). No deprecation cycle (the library has not been
released, per the map's "Blast radius" rule).**

### What gets added

1. **`KompactFraming.readNestedOrThrow(raw, bitOffset, prefixBitWidth): Pair<Int, Int>`**
   — the throw-on-failure companion. Throws
   `KompactDecodeException(BadLengthPrefix)` if `prefixBitWidth`
   is not in `VALID_PREFIX_WIDTHS` or the prefix overruns the
   buffer; throws `KompactDecodeException(TruncatedNested)` if
   the nested region's payload overruns the buffer; throws
   `KompactDecodeException(BoundsError)` if the byte count
   overflows `Int` (the F-003 boundary check at the original
   `nestedRegionOrNull`'s `byteCount > Int.MAX_VALUE / 8` line).
2. **`KompactFraming.readNested(raw, bitOffset, prefixBitWidth): NestedRegionResult`**
   — the typed-result companion. Returns a
   `NestedRegionResult` value class on success, a
   `KompactDecodeError` on failure (zero-alloc on both JVM and
   iOS, per the ticket 03 value-class pattern).
3. **`value class NestedRegionResult(val startBit: Int, val bitLength: Int)`**
   — declared in `commonMain/.../KompactFraming.kt` (or a new
   `NestedRegionResult.kt`). `@JvmInline` on the JVM actual;
   plain `value class` on iOS actual. Two `Int` fields; the
   field names are self-documenting (no `Pair.first` /
   `Pair.second` confusion). The four-member public surface
   mirrors the seven result classes: `isSuccess`, `isFailure`,
   `error`, `getOrThrow(): Pair<Int, Int>`.
4. **`KompactFraming.readLengthPrefixOrThrow(raw, bitOffset, bitWidth): Int`**
   — the count-prefix throw-on-failure companion. Throws
   `KompactDecodeException(BadLengthPrefix)` on invalid width
   or overrun. The `-1`-on-failure form stays for the hot-path
   internal; the `…OrThrow` form is the user-facing throw
   path. No typed result for the count prefix — a single `Int`
   is too small to justify a new value class (the spec's
   ticket 08 keeps the seven specialized result value classes
   per scalar kind; the count prefix is not a scalar).
5. **`Kompact.Result.NestedRegion` typealias** in
   `KompactResult.kt` (the `Kompact.Result` namespace from
   ticket 04). Mirrors the `Kompact.Result.Int` / `…Long` /
   etc. pattern.
6. **`NestedRegionResult.getOrElse(fallback: (KompactDecodeError) -> Pair<Int, Int>): Pair<Int, Int>`**
   and **`NestedRegionResult.map(transform: (Pair<Int, Int>) -> Pair<Int, Int>): NestedRegionResult`**
   extensions, mirroring the stdlib `Result<T>` pattern from
   ticket 04. Pure extensions; no value-class change.

### What gets demoted

- **`KompactFraming.nestedRegionOrNull`**: `public inline` →
  `internal inline`. The new public functions (`readNested` and
  `readNestedOrThrow`) delegate to it; it's the hot-path
  internal. No deprecation cycle (the library has not been
  released, per the map's "Blast radius" rule).

### Sketch (for the implementation commit)

```kotlin
// KompactFraming.kt — commonMain (additions)

public value class NestedRegionResult internal constructor(private val packed: Long) {
    public val startBit: Int get() = (packed ushr 32).toInt()
    public val bitLength: Int get() = packed.toInt() and 0xFFFFFFFF.toInt()
    // isSuccess / isFailure / error / getOrThrow() — see ticket 03 value-class pattern
    // Companion.success(startBit, bitLength) / failure(error)
}

public inline fun readNestedOrThrow(
    raw: ByteArray, bitOffset: Int, prefixBitWidth: Int
): Pair<Int, Int> {
    val region = nestedRegionOrNull(raw, bitOffset, prefixBitWidth)
        ?: throw KompactDecodeException(/* BadLengthPrefix or TruncatedNested or BoundsError */)
    return region.first to region.second
}

public inline fun readNested(
    raw: ByteArray, bitOffset: Int, prefixBitWidth: Int
): NestedRegionResult {
    val region = nestedRegionOrNull(raw, bitOffset, prefixBitWidth)
    return if (region != null) NestedRegionResult.success(region.first, region.second)
    else NestedRegionResult.failure(/* BadLengthPrefix or TruncatedNested or BoundsError */)
}

public inline fun readLengthPrefixOrThrow(
    raw: ByteArray, bitOffset: Int, bitWidth: Int
): Int {
    if (bitWidth !in VALID_PREFIX_WIDTHS) {
        throw KompactDecodeException(KompactDecodeError.BadLengthPrefix)
    }
    if (!KompactRuntime.fits(raw, bitOffset, bitWidth)) {
        throw KompactDecodeException(KompactDecodeError.BadLengthPrefix)
    }
    return when (bitWidth) {
        8 -> KompactRuntime.readBits(raw, bitOffset, 8)
        16 -> KompactRuntime.readBits(raw, bitOffset, 16)
        else -> KompactRuntime.readBitsLong(raw, bitOffset, 32).toInt()
    }
}

// KompactFraming.kt — demoted (was public, now internal)
- public inline fun nestedRegionOrNull(
+ internal inline fun nestedRegionOrNull(
    raw: ByteArray, bitOffset: Int, prefixBitWidth: Int
): Pair<Int, Int>? { … }

// KompactResult.kt — additions
+ public typealias NestedRegion = NestedRegionResult  // in the Kompact.Result namespace
+ public inline fun NestedRegionResult.getOrElse(fallback: (KompactDecodeError) -> Pair<Int, Int>): Pair<Int, Int>
+ public inline fun NestedRegionResult.map(transform: (Pair<Int, Int>) -> Pair<Int, Int>): NestedRegionResult
```

### Propagation

- **Test updates** (in the implementation commit):
  - `kompact/src/commonTest/.../KompactFramingTest.kt:88, 102, 124, 127, 131, 134`
    — these six call sites use `nestedRegionOrNull`. After the
    demotion, they switch to `readNested` (typed result) or
    `readNestedOrThrow` (throw). The two test methods named
    `nestedRegionOrNull_rejectsIntMaxByteCountPrefix` and
    `nestedRegionOrNull32_roundTripsLegitByteCount` rename to
    `readNested_rejectsIntMaxByteCountPrefix` and
    `readNested_roundTripsLegitByteCount` (the public accessor
    is `readNested` now).
  - `kompact/src/commonTest/.../KompactWriterTest.kt:105` — uses
    `nestedRegionOrNull` to read back the `writeNested` output.
    Switches to `readNested(buf, 0, 16).getOrThrow()`.
- **Docs**:
  - `docs/api-reference.md#kompactframing` — the public-surface
    table currently lists `nestedRegionOrNull`. After the
    demotion, the table lists the new public functions
    (`readNestedOrThrow`, `readNested`, `readLengthPrefixOrThrow`)
    plus the new `NestedRegionResult` value class (with its
    four-member surface). `nestedRegionOrNull` is removed
    from the public table; a note marks it as the hot-path
    internal.
  - `docs/architecture.md#framing-contract` — the reference to
    `nestedRegionOrNull` in the framing helpers sentence
    (`writeLengthPrefix / nestedRegionOrNull object`) updates
    to reference the new public surface.
  - `docs/getting-started.md` — does not currently use
    `nestedRegionOrNull`; the tutorial exercises fixed-width
    scalars + the writer's `writeNested` (no read-back of
    nested). The tutorial's "long-form payload" section (if
    it gets added in a follow-up) can use the new
    `readNested` as the symmetric reader. No edit required for
    this ticket.
- **Public ABI change** — adds 1 new value class
  (`NestedRegionResult`), 3 new public functions
  (`readNestedOrThrow`, `readNested`, `readLengthPrefixOrThrow`),
  2 new extensions (`getOrElse`, `map`). Removes 1 public
  function (`nestedRegionOrNull`, demoted to `internal`).
  Net: 5 new public symbols, 1 demoted. BCV will catch the
  drift; klib golden regen on macOS via the existing
  `regen-goldens.yml` workflow.
- **Ticket 09** (Test file split) — note the new
  `KompactFramingTest` tests for `readNested` /
  `readNestedOrThrow` / `readLengthPrefixOrThrow` /
  `NestedRegionResult` extensions may live in the framing
  test file or a new `KompactFramingNestedRegionTest.kt` per
  the split decision. Note in 09's body; do not resolve.

## Comments
