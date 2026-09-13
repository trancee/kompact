Type: grilling
Status: resolved
Blocked by: —  (unblocked 2026-09-05 by ticket 01 — the parameter shape and the result-type shape are independent; 01 settled the parameter only)

# 04 — Result ergonomics layer: how do consumers recover from failure?

## Question

The seven specialized result value classes (`ByteResult`,
`ShortResult`, `IntResult`, `LongResult`, `FloatResult`,
`DoubleResult`, `BooleanResult`) are the central ergonomic choice in
the runtime: they make the success path zero-alloc on the JVM
(`@JvmInline` over a `Long`) and on iOS (plain value class over
`Long`). A consumer today writes:

```kotlin
val battery: Int = KompactRuntime
    .readScalar(bytes, 0, 4, signed = false)
    .getOrThrow()
```

For a consumer who wants to *handle* failure (not throw), the
`getOrThrow()` call is wrong, and the alternative is verbose:

```kotlin
val result = KompactRuntime.readScalar(bytes, 0, 4, signed = false)
when (val err = result.error) {
    null -> { /* success: use result.getOrThrow() */ }
    KompactDecodeError.BoundsError -> { /* ... */ }
    KompactDecodeError.BadLengthPrefix -> { /* ... */ }
    KompactDecodeError.TruncatedNested -> { /* ... */ }
    is KompactDecodeError.UnknownEnumCode -> { /* ... */ }
}
```

The decision: do we add a higher-level ergonomics layer to make the
common patterns cheaper, and if so what is it? Options to grill:

- **A `decodeOrThrow` / `tryDecode` extension on each result type** —
  e.g. `val battery: Int = KompactRuntime.readScalar(...).getOrThrow()`
  becomes `val battery: Int = KompactRuntime.readScalarOrThrow(...)`
  (function-level, returns the primitive, throws on failure). Cheaper
  at the call site, but loses the explicit "this returns a typed
  result" signal.
- **A single `Result<T>` alias or namespace** that re-exports the
  seven result types under one umbrella, so consumers
  `import kompact.Result` and get `Result.Int`, `Result.Long`, etc.
  Reduces the seven-name surface to one import.
- **A `KompactResult` *sealed* companion type** that wraps the seven
  result types behind one type parameter, with a `fold` /
  `when`-friendly `getOrElse { error -> ... }` API. Centralizes the
  recovery pattern but reintroduces a generic — which is what
  ticket 08 in the v1 spec explicitly *rejected* (the rejection
  rationale: a generic `KompactDecodeResult<T>` boxes on the success
  path on the JVM, breaking the zero-alloc contract).
- **A function-shape ergonomics helper** that does the
  `if (result.isFailure) TypedError else use(...)` pattern in one
  call, e.g. `KompactFraming.readNestedTyped(raw, off, w): NestedRegionResult`
  (see ticket 05). Each framing helper that currently returns
  `null` gets a typed-result companion.
- **Nothing — the per-kind specialization *is* the ergonomics.**
  The seven result types are the right call; the recovery pattern
  is verbose but explicit; this ticket is the user pushing back
  on the verbosity only to confirm that it is what we want.

## Context for the claiming session

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactResult.kt` —
  the seven `expect value class` declarations and the
  `KompactDecodeError` sealed class (`BoundsError`,
  `BadLengthPrefix`, `TruncatedNested`,
  `UnknownEnumCode(rawCode: Int)`).
- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactDecodeError.kt` —
  the sealed error taxonomy.
- `docs/api-reference.md#typed-result-value-classes` — current
  reference text for the result types.
- `docs/getting-started.md` — the consumer-facing tutorial that
  demonstrates the verbose recovery pattern. Any ergonomics layer
  should be reflected there.
- `GettingStartedTest` in `kompact/src/commonTest/.../runtime/` —
  the executable journey; if a new helper is added, the test
  should also exercise it.
- The v1 spec map's ticket 08 notes ("Runtime error model —
  specialized zero-alloc result value classes") — the rejection
  rationale for a generic `KompactDecodeResult<T>` is the
  zero-alloc-on-success contract. Any new ergonomics layer must
  not regress that.

## Open sub-questions

1. **What is the *common* recovery pattern in the wild?** The
   v1 spec assumes `getOrThrow()` is the success path and the
   `error` property is the failure path. If a survey of the
   `commonTest` cases shows that 80% of consumers want to
   short-circuit on failure, an extension function or helper that
   does that in one call is justified. If the survey shows the
   pattern is 50/50, two helpers (`getOrThrow` + a
   `fold`/`map`/`getOrElse`) is the right answer.
2. **Is the seven-class surface actually confusing for newcomers?**
   A new visitor reading `docs/api-reference.md` sees seven types
   with the same four members and asks "why seven?" The answer
   (zero-alloc on success on both JVM and iOS) is in
   `docs/architecture.md`, but the *first impression* is friction.
   A single namespace (`Result.Int`, `Result.Long`, …) is cheaper
   to learn. Is the friction worth the zero-alloc guarantee?
3. **The writer side** — does the writer surface need an
   ergonomics layer? The writer currently throws
   `IllegalArgumentException` from `writeLengthPrefix` when the
   prefix width is not in `VALID_PREFIX_WIDTHS` (lines ~48–57 of
   `KompactFraming.kt`). Should the writer's failure modes also
   become typed results? (Out of scope: the writer is not on the
   zero-alloc hot path, so throwing is acceptable. The
   architectural call here matters for symmetry.)

## What "resolved" looks like

- The chosen ergonomics layer (or "nothing") is recorded under
  `## Answer` with a one-line rationale and a sketch of the new
  surface (3–10 lines of Kotlin).
- If a new layer is added, the call-site shape is shown so the
  implementation commit can apply it to `KompactRuntime`,
  `KompactFraming`, and the platform actuals.
- The downstream ticket 05 (`readNested` typed-result companion)
  is updated to align with the chosen layer.

## Answer

**Decision: adopt all three ergonomics additions — `…OrThrow` function-level
wrappers, a `Kompact.Result` namespace re-export, and `getOrElse` / `map`
extension functions on the seven result value classes. All three are
additive (no public-API break, no zero-alloc regression, no BCV regen
needed for the extensions — only the `Kompact.Result` namespace adds
a new top-level public symbol).**

### What gets added

1. **`…OrThrow` function-level wrappers** (5 new public functions on
   `KompactRuntime`): each returns the primitive directly and throws
   `KompactDecodeException` on bounds / encoding failure. The spec's
   ticket 08 already contemplated this for Java interop; the
   decision is to keep it for all callers (Kotlin and Java).
   ```kotlin
   public fun readBoolOrThrow(raw: ByteArray, bitOffset: Int): Boolean
   public fun readScalarOrThrow(raw: ByteArray, bitOffset: Int, type: ScalarType): Int
   public fun readScalarAsLongOrThrow(raw: ByteArray, bitOffset: Int, type: ScalarType): Long
   public fun readFloatOrThrow(raw: ByteArray, bitOffset: Int): Float
   public fun readDoubleOrThrow(raw: ByteArray, bitOffset: Int): Double
   ```
   The internals do the same `fits` check the typed-result accessors
   do, then call `readBits` / `readBitsLong` directly and return the
   primitive. The `…OrThrow` form is one call site (`KompactRuntime
   .readScalarOrThrow(bytes, 0, ScalarType.UINT_8)`); the typed-result
   form is two (`KompactRuntime.readScalar(bytes, 0, ScalarType.UINT_8)
   .getOrThrow()`). Both are zero-alloc on the success path (the
   `…OrThrow` form avoids the value-class construction altogether
   but the cost of the value-class was already zero on both JVM and
   iOS — the win is call-site ergonomics, not allocation).

2. **`Kompact.Result` namespace re-export** (1 new top-level public
   symbol). `object ch.trancee.kompact.Kompact.Result` exposes the
   seven result types as members: `Kompact.Result.Int`,
   `Kompact.Result.Long`, `Kompact.Result.Boolean`, etc. The seven
   top-level declarations stay (no breaking change); the namespace
   is a one-stop import for newcomers who prefer a single import
   path. Cost: 7 lines in `KompactResult.kt` (a `typealias`-style
   re-export, not new types).
   ```kotlin
   public object Result {
       public typealias Byte = ByteResult
       public typealias Short = ShortResult
       public typealias Int = IntResult
       public typealias Long = LongResult
       public typealias Float = FloatResult
       public typealias Double = DoubleResult
       public typealias Boolean = BooleanResult
   }
   ```

3. **`getOrElse` / `map` extension functions** on the seven result
   value classes (14 new top-level public functions in
   `commonMain/.../KompactResult.kt`). Mirror stdlib's
   `Result<T>` pattern: `getOrElse` takes a `(KompactDecodeError)
   -> T` fallback; `map` takes a `(T) -> T` success transform
   and returns the same result kind (so the failure propagates
   unchanged). Both are pure extension functions; no value-class
   change; no zero-alloc regression.
   ```kotlin
   public inline fun IntResult.getOrElse(fallback: (KompactDecodeError) -> Int): Int
   public inline fun IntResult.map(transform: (Int) -> Int): IntResult
   // …same for ShortResult, LongResult, FloatResult, DoubleResult, ByteResult, BooleanResult
   ```

### What does NOT change

- **The seven specialized result value classes** stay as
  `expect value class` in `commonMain` + `@JvmInline actual` on
  JVM + plain `actual` on iOS. The spec's ticket 08 zero-alloc
  contract is preserved: no generic, no boxing, no throw on
  the success path. The new extensions and the namespace do not
  touch the encoding or the layout.
- **The four-member public surface** of each result class
  (`isSuccess`, `isFailure`, `error`, `getOrThrow()`) stays.
  `getOrElse` and `map` are additive; `getOrThrow()` is still
  the throw-on-failure recovery call.
- **No `KompactDecodeResult<T>` generic** — explicitly rejected by
  the spec's ticket 08 (boxes on the success path on the JVM).
  The ergonomics layer is *around* the seven specialized types,
  not *instead of* them.

### Sketch (for the implementation commit)

```kotlin
// KompactResult.kt — additions to the existing file

// 1. Namespace re-export
public object Result {
    public typealias Byte = ByteResult
    public typealias Short = ShortResult
    public typealias Int = IntResult
    public typealias Long = LongResult
    public typealias Float = FloatResult
    public typealias Double = DoubleResult
    public typealias Boolean = BooleanResult
}

// 2. getOrElse / map extensions (one per result kind)
public inline fun IntResult.getOrElse(fallback: (KompactDecodeError) -> Int): Int =
    if (isSuccess) (packed and RESULT_VALUE_MASK).toInt() else fallback(error!!)
public inline fun IntResult.map(transform: (Int) -> Int): IntResult =
    if (isSuccess) IntResult.success(transform((packed and RESULT_VALUE_MASK).toInt()))
    else this
// …repeated for ShortResult, LongResult, FloatResult, DoubleResult, ByteResult, BooleanResult

// KompactRuntime.kt — `…OrThrow` wrappers (additive)
public inline fun readBoolOrThrow(raw: ByteArray, bitOffset: Int): Boolean {
    if (!fits(raw, bitOffset, 1)) throwBounds(BoundsError())
    return readBitsBoolean(raw, bitOffset)
}
public inline fun readScalarOrThrow(raw: ByteArray, bitOffset: Int, type: ScalarType): Int {
    val w = type.bitWidth
    if (!fits(raw, bitOffset, w)) throwBounds(BoundsError())
    val raw_ = readBits(raw, bitOffset, w)
    return if (type.signed) signExtend(raw_, w) else raw_
}
// …similar for readScalarAsLongOrThrow, readFloatOrThrow, readDoubleOrThrow
```

### Propagation

- **Ticket 05** (`readNested` typed-result companion) — **unblocked**.
  The ergonomics layer is now settled; 05 picks a name and applies
  the same pattern. The recommended shape for 05 is:
  - `KompactFraming.readNested(raw, off, prefixBitWidth): NestedRegionResult`
    (typed result, with `getOrElse`/`map` extensions on
    `NestedRegionResult` mirroring the result-class pattern).
  - `KompactFraming.readNestedOrThrow(raw, off, prefixBitWidth): Pair<Int, Int>`
    (primitive pair, throws on failure).
  - `KompactFraming.nestedRegionOrNull(...)` stays as the nullable
    hot-path internal (zero-alloc; what the new functions call
    internally). Deprecation cycle: the library has not been
    released, so it's removed immediately (no deprecation cycle
    per the map's "Blast radius" rule).
  Note in 05's body; do not resolve.
- **Ticket 09** (Test file split) — note the new public symbols
  (`…OrThrow`, `getOrElse`, `map`) need their own test coverage.
  If 09 splits per-accessor, the new test files include
  `KompactRuntimeReadScalarOrThrowTest.kt` etc.
- **Docs** — `docs/api-reference.md` adds a new subsection under
  the runtime for `…OrThrow`, `getOrElse`/`map`, and the
  `Kompact.Result` namespace. The `KompactResult` section
  (current `## Typed result value classes`) is reorganized to
  list the seven types and the new extensions.
- **Public ABI** — adds 1 top-level symbol (`Kompact.Result`),
  5 `…OrThrow` functions, 14 extension functions (7 `getOrElse`
  + 7 `map`). BCV will catch the drift; klib golden regen on
  macOS via the existing `regen-goldens.yml` workflow.

## Comments

**Implementation outcome (2026-09-06).** Resolved as decided.

- Registered `Kompact.Result` namespace at `ch/trancee/kompact/Kompact.kt`
  (`object Kompact { object Result { 7 typealias } }`); additive, no
  zero-alloc regression, scoped inside `Kompact.Result` (no shadowing of
  `kotlin.Result`).
- 5 `…OrThrow` wrappers on `KompactRuntime` + 14 `getOrElse`/`map`
  extensions (7 + 7) on the result value classes.
- ABI: `kompact.api` (JVM) regenerated via `:kompact:jvmApiDump`, green
  via `:kompact:jvmApiCheck`; byte-identical on the macOS regen.
  `kompact.klib.api` (merged iOS klib) regenerated on macOS via
  `regen-goldens.yml` (run `34021875744`, +4 lines for Kompact/Result).
- `:kompact:jvmTest` → BUILD SUCCESSFUL (187 tests).
- CI `34022123785` (`feat/laguna` @ `cf7dde57`) → `jvmTest (Linux)`:
  success + `apiCheck (macOS)`: success.
- Commits `13f2f5b` (Kompact.kt + kompact.api + test split) +
  `cf7dde5` (klib golden) on `feat/laguna`.
