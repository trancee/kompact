Type: grilling
Status: resolved
Blocked by: —  (note 2026-09-05: ticket 01 settled the parameter shape as `ScalarType(bitWidth, signed)` with no enum axis. Ticket 06 settled the annotation visibility: the example's `@KompactModel` / `@KompactField` annotations are now gated by `@KompactPreview` (`@RequiresOptIn(WARNING)`); the example opts in via `@file:OptIn(KompactPreview::class)`. The "keep the raw `readBits` shape" option remains valid as a codegen-output reference. The "realign to the public `readScalar`" option now has the annotations as a clear `expect` shape (KSP processor's future output) — the example is closer to the codegen target either way. 07's decision is still its own.)
# 07 — `VehicleTelemetry` example: teach the public API or the codegen shape?

## Question

The bundled example
`kompact/src/commonMain/kotlin/ch/trancee/kompact/generated/VehicleTelemetry.kt`
is the `expect value class` that the README and `docs/getting-started.md`
reference. Its getter bodies are hand-written using the **raw**
`KompactRuntime.readBits` path:

```kotlin
@KompactField(bitOffset = 0, bitWidth = 4)
public val batteryStatus: Int

@KompactField(bitOffset = 4, bitWidth = 10)
public val speed: Int

@KompactField(bitOffset = 14, bitWidth = 1)
public val isMalfunctioning: Boolean
```

…with the platform actuals (`jvmMain` + `iosMain` `VehicleTelemetry.kt`)
implementing the getters as `KompactRuntime.readBits(raw,
bitOffset, bitWidth)` (the raw zero-alloc primitive). This is
the *codegen-output* pattern: the KSP processor that ticket 06
is about would emit exactly this shape.

A newcomer reading the example sees two patterns at once: the
**annotation surface** (the `@KompactField` properties) and the
**raw readBits path** (the getter bodies, which are an internal
detail the consumer never writes by hand once the processor
lands). The newcomer may then write a hand-written value class
and not realize they're doing what a future KSP processor will
do for them, OR they may try to use the public
`readScalar` / `readBool` checked accessors and get a different
result (a `KompactResult`, not a primitive).

The decision: which example teaches the right thing?

- **Realign the example to use the public checked accessors**
  (`readScalar` for the 4-bit and 10-bit fields, `readBool` for
  the 1-bit flag). The getters then do
  `.getOrThrow()` and return primitives. This teaches the
  public API a newcomer will actually use (and what a hand-written
  model would look like if the KSP processor never ships). It
  costs the zero-alloc read path: the value-class getter now
  allocates a `Long` for the result on every access (the
  `@JvmInline`/`actual` value class still makes it zero-alloc
  on the heap, but the primitive is extracted via
  `getOrThrow` which… does not throw on success and returns
  inline).

  Wait — the result is inline (`@JvmInline` value class over
  `Long`), so the getter is still effectively zero-alloc. The
  trade is between the *zero-alloc internal pattern* (raw
  `readBits`) and the *public API pattern* (checked
  `readScalar`/`readBool` with `.getOrThrow()`).

- **Keep the raw `readBits` shape and document it as the
  codegen-output pattern.** The example then teaches the
  internal pattern, with a note "this is what the KSP processor
  will emit; hand-write it now only if you want the zero-alloc
  read path on the success hot path." The newcomer who doesn't
  need the zero-alloc read path writes a different
  hand-written model using the public checked accessors; the
  docs explain the difference.

- **Add a second example** (e.g. `VehicleTelemetryChecked.kt`)
  that demonstrates the public checked accessors, alongside
  the existing raw example. The raw example stays as the
  codegen-output reference; the new example is the
  newcomer-friendly path. Two examples, two readers.

## Context for the claiming session

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/generated/VehicleTelemetry.kt`
  — the `expect value class` declaration with the
  `@KompactField` annotations (lines ~22–40).
- `kompact/src/jvmMain/kotlin/ch/trancee/kompact/generated/VehicleTelemetry.kt`
  — the JVM actual with the raw `KompactRuntime.readBits`
  bodies.
- `kompact/src/iosMain/kotlin/ch/trancee/kompact/generated/VehicleTelemetry.kt`
  — the iOS actual.
- `kompact/src/commonTest/kotlin/ch/trancee/kompact/generated/VehicleTelemetryTest.kt`
  — the test that pins the 16-bit wire bytes
  (`0xA5 0x40`) and the round-trip values.
- The README and `docs/getting-started.md` both reference
  `VehicleTelemetry` by name. Any example change ripples to
  both.
- The `GettingStartedTest` in
  `kompact/src/commonTest/.../runtime/` is the
  executable-journey test for the tutorial; it uses the
  **public** checked accessors and pins its own wire bytes
  (`0xA5 0x40`). If the example is realigned to the public
  API, the tutorial and the example converge; if the example
  keeps the raw shape, the two diverge and the docs need to
  explain both.

## Open sub-questions

1. **Is the example the right place to teach the codegen-output
   pattern?** A newcomer landing on the repo for the first
   time is not the audience for "this is what the KSP processor
   will emit." The audience for the codegen-output pattern is
   the *implementer of the KSP processor* (ticket 06) and
   *maintainers of the runtime's zero-alloc contract*. A
   separate doc (or research note) might be a better home.
2. **Convergence with the tutorial.** `docs/getting-started.md`
   uses the public checked accessors and pins the wire bytes.
   The example uses the raw path and pins the same wire bytes.
   Converging on one shape makes the docs simpler; diverging
   (with a clear "this is the internal pattern" note) preserves
   the codegen-output reference.
3. **The annotation question (ticket 06).** If ticket 06
   decides to ship a processor, the raw example becomes the
   "what the processor will emit" reference. If ticket 06
   decides to hide the annotations, the example either
   drops the annotations (and becomes a plain `expect value class`
   over a `ByteArray`) or goes away entirely.

## What "resolved" looks like

- The chosen shape (realign / keep / add a second example) is
  recorded under `## Answer`.
- The decision is propagated to ticket 06 (annotation
  visibility) and to the README and `docs/getting-started.md`
  (which currently reference `VehicleTelemetry` by name).
- The wire bytes (`0xA5 0x40`) and the test
  (`VehicleTelemetryTest`) stay green regardless of which shape
  is chosen.

## Answer

**Decision: realign the `VehicleTelemetry` example to use the new
public checked accessors (`readScalar` + `readBool` from ticket 01,
`getOrThrow()` for the success path). The annotations stay on the
example (via `@file:OptIn(KompactPreview::class)` from ticket 06).
The codegen-output reference (raw `readBits` shape) moves to prose
in `docs/architecture.md`. Single canonical model.**

### Why this option

The example is the only annotated value class in the source
tree. A newcomer who reads the README → `docs/getting-started.md`
(the tutorial) → `docs/api-reference.md` → and then *opens* the
example sees a different shape from everything else: raw
`readBits` getter bodies, the internal zero-alloc pattern. They
wonder "is *this* the API?" The answer is no — the tutorial and
the API reference use the public checked accessors; the example
is the codegen-output pattern. Two stories, one for the
consumer, one for the future KSP processor implementer.

The "challenge everything" mandate points at the single
canonical model: the example matches the tutorial. The
codegen-output pattern (raw `readBits` + annotations) is
preserved as prose in `docs/architecture.md` for the
implementer of the future processor; it does not need to live
as runnable source code in the example.

### Sketch (for the implementation commit)

```kotlin
// commonMain/.../generated/VehicleTelemetry.kt — the expect value class
@file:OptIn(KompactPreview::class)

@KompactModel
public expect value class VehicleTelemetry(public val raw: ByteArray) {

    @KompactField(bitOffset = 0, bitWidth = 4)
    public val batteryStatus: Int

    @KompactField(bitOffset = 4, bitWidth = 10)
    public val speed: Int

    @KompactField(bitOffset = 14, bitWidth = 1)
    public val isMalfunctioning: Boolean
}

// jvmMain/.../generated/VehicleTelemetry.kt — the JVM actual
@file:OptIn(KompactPreview::class)

@JvmInline
@KompactModel
public actual value class VehicleTelemetry(public actual val raw: ByteArray) {

    init {
        require(raw.size >= 2) { /* F-001 fail-fast on truncated buffer */ }
    }

    @KompactField(bitOffset = 0, bitWidth = 4)
    public actual val batteryStatus: Int
        get() = KompactRuntime.readScalar(raw, 0, ScalarType.UINT_8).getOrThrow()

    @KompactField(bitOffset = 4, bitWidth = 10)
    public actual val speed: Int
        get() = KompactRuntime.readScalar(raw, 4, ScalarType.UINT_16).getOrThrow()

    @KompactField(bitOffset = 14, bitWidth = 1)
    public actual val isMalfunctioning: Boolean
        get() = KompactRuntime.readBool(raw, 14).getOrThrow()
}

// iosMain/.../generated/VehicleTelemetry.kt — the iOS actual: same getter
// shape, no @JvmInline (per the ticket 03 value-class pattern).
```

### What changes in the test

`VehicleTelemetryTest.kt` continues to:
- build the buffer with `KompactRuntime.writeBits` (the
  write-side stays raw; the write path is not the example's
  teaching target),
- assert the wire bytes (`0xA5 0x40`),
- round-trip the values (5, 10, true) through
  `VehicleTelemetry(buf)`,
- pin the F-001 constructor validation.

The test's *read side* (the getters) is unchanged from the
consumer's perspective — the getters still return the same
primitives. The test passes with no edits.

### What changes in the docs

- **`docs/architecture.md`** — the "what is in this repository
  today" section updates: the example's getter bodies are
  the new public-API shape. The *codegen-output* pattern
  (raw `readBits` + `@KompactField` annotations) moves to a
  new prose section, e.g. a "Codegen output reference" block
  that the future KSP processor implementer reads alongside
  ticket 02's research notes.
- **`docs/api-reference.md`** — the `## annotations` section's
  note that `VehicleTelemetry` "demonstrates the annotation
  surface" is unchanged (the annotations are still on the
  example). A new line notes "the example's getter bodies
  use the public API; the codegen-output shape is documented
  in `docs/architecture.md#codegen-output-reference`."
- **`README.md`** — no change (the README does not point at
  the example as a learning artifact; the tutorial handles
  the consumer's view).

### What does NOT change

- The annotations stay on the example (`@KompactModel`,
  `@KompactField` × 3). They are gated by `@KompactPreview`
  from ticket 06; the example opts in via
  `@file:OptIn(KompactPreview::class)`.
- The `expect value class` declaration's `raw: ByteArray`
  property and the three `@KompactField` properties stay the
  same shape.
- The F-001 constructor validation in the platform actuals
  (the `init` block that `require(raw.size >= 2)`) stays.
- The wire bytes (`0xA5 0x40`) and the round-trip values
  stay. The test passes with no edits.
- The annotations are still SOURCE-retained (per
  `KompactAnnotations.kt`); `@KompactPreview` is
  BINARY-retained (required by `@RequiresOptIn`).

### Propagation

- **Ticket 09** (Test file split) — the test for the example
  is currently `VehicleTelemetryTest.kt` (1 file, ~100 lines,
  well within the D9 500-line limit). No split needed.
- **Ticket 10** (Docs layer structure review) — the codegen-
  output reference in `docs/architecture.md` is a small prose
  addition; ticket 10's "is the layering right?" question is
  not affected.

## Comments
