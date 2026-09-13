# Agent Quick-Start: Kompact Library

A 2-minute reference for AI agents working with the Kompact binary
serialization library. Covers setup, core concepts, the most common
API calls, and the traps that cause compile errors or runtime
failures.

## What is Kompact (10 s)

Kompact is a Kotlin Multiplatform (JVM + iOS) library that serializes
structured data into **bit-packed, LSB-first** byte buffers for
low-latency, zero-allocation reads — originally built for BLE
characteristics where every byte and every micro-allocation matters.

```
┌───────────────┐  KompactWriter  ┌─────────┐  BLE  ┌──────────────┐
│  field values │ ──────────────► │  bytes  │ ────► │  0xA5 0x40   │
└───────────────┘                 └─────────┘       └──────────────┘
                                              ◄────  characteristic
┌───────────────┐  value class   ┌─────────┐  BLE  ┌────────────────┐
│  tel.battery  │ ◄───────────── │  bytes  │ ◄──── │  notification  │
└───────────────┘  (zero-alloc)  └─────────┘       └────────────────┘
```

- **Write side:** `KompactWriter` — forward-only, growable,
  length-prefixed framing for strings/blobs/nested/repeated.
- **Read side:** value-class getters over a raw `ByteArray` —
  zero-copy, zero-allocation on the success hot path.
- **No JVM-specific annotations** (`.kt`/`.kts` source files use the
  multiplatform `value class` keyword — `@JvmInline` only appears in
  platform `actual`s, which you do not write by hand if you use the
  KSP processor).

## Project structure (at a glance)

```
build-logic/                    convention plugins (portal-publish, dokka-markdown)
├── src/main/kotlin/
│   ├── portal-publish.gradle.kts   ← Maven Central Portal API tasks
│   └── dokka-markdown.gradle.kts   ← GFM Markdown Dokka output
kompact/                        the runtime library (KMP: JVM + Android + iOS)
├── src/commonMain/kotlin/…/runtime/  ← KompactRuntime, KompactWriter, KompactFraming,
│                                     ←   KompactResult*, ScalarType, KompactDecodeError
├── src/commonMain/kotlin/…/generated/VehicleTelemetry.kt  ← example model (expect/actual)
├── src/jvmCommon/kotlin/…/         ← @JvmInline actuals (shared by JVM + Android)
├── src/iosMain/kotlin/…/           ← plain value class actuals
└── api/kompact.api                 ← committed ABI golden
kompact-ksp/                    the KSP code generator
├── src/main/kotlin/…/gen/ValueClassGenerator.kt  ← generates expect/actual from annotations
└── api/kompact-ksp.api             ← committed KSP ABI golden
```

> **Key constraint:** `kompact-ksp` is a JVM-only module targeting
> JVM 17 (not 21). The consumer's Kotlin compile daemon loads the
> processor; JVM 21 bytecode would exclude JDK 17 consumers.

## Setup (consumer)

```kotlin
// build.gradle.kts (consumer module)
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("com.google.devtools.ksp") version "2.3.12"   // only if using codegen
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.2.0-SNAPSHOT")
            }
        }
    }
}

// Only if you use the KSP processor (recommended for production):
dependencies {
    kspCommonMainMetadata("ch.trancee.kompact:kompact-ksp:0.2.0-SNAPSHOT")
}
```

Until the first Maven Central release, the snapshot is only available
via `./gradlew :kompact:publishToMavenLocal` + `mavenLocal()` in your
`repositories` block.

## Core API cheat sheet

### Packages

| Package | What lives here |
|---|---|
| `ch.trancee.kompact.runtime` | `KompactRuntime`, `KompactWriter`, `KompactFraming`, `ScalarType`, all `*Result` value classes, `KompactDecodeError` |
| `ch.trancee.kompact.annotations` | `@KompactModel`, `@KompactField`, `@KompactPreview` |
| `ch.trancee.kompact` | `Kompact.Result` — typealias namespace (`Kompact.Result.Int`, etc.) |
| `ch.trancee.kompact.generated` | The bundled `VehicleTelemetry` example |

### KompactRuntime — the 80/20 reads

```kotlin
// Raw primitives (zero-alloc, no bounds check — use when you proved bounds)
val bits: Int = KompactRuntime.readBits(raw, bitOffset, bitWidth)    // 1..31 bits
val bitsL: Long = KompactRuntime.readBitsLong(raw, bitOffset, bitWidth)  // 1..64 bits
val bit: Boolean = KompactRuntime.readBitsBoolean(raw, bitOffset)

// Checked accessors (return typed result value classes, never throw on success)
val speed: IntResult = KompactRuntime.readScalar(raw, 0, ScalarType.of(10, signed = false))
val flag: BooleanResult = KompactRuntime.readBool(raw, 14)
val temp: FloatResult = KompactRuntime.readFloat(raw, bitOffset)
val ts: LongResult = KompactRuntime.readScalarAsLong(raw, 20, ScalarType.of(32, signed = true))
val dbl: DoubleResult = KompactRuntime.readDouble(raw, bitOffset)

// Recovery
if (speed.isSuccess) { val v: Int = speed.getOrThrow() }
val v2: Int = speed.getOrElse { err -> logger.warn(err); 0 }
val mapped: IntResult = speed.map { it * 2 }
```

**Critical constraints:**
- `readBits` / `writeBits` accept `bitWidth` in `1..31` only.
- `readBitsLong` / `writeBitsLong` accept `1..64`.
- `readScalar` accepts `1..32`; use `readScalarAsLong` for wider.
- `LongResult` has a **sentinel band**: values in
  `Long.MIN_VALUE .. Long.MIN_VALUE + (1L shl 58) - 1` are treated as
  failure. They are not representable as success.
- `Float` uses the packed-Long layout (32-bit IEEE-754 in the low 48
  bits). `Double` uses a NaN-payload scheme (canonical quiet-NaN =
  success, non-zero NaN payload = failure).

### KompactWriter — the 80/20 writes

```kotlin
val w = KompactWriter()
w.writeScalar(ScalarType.of(4,  signed = false), 5L)    // 4-bit unsigned
w.writeScalar(ScalarType.of(10, signed = false), 10L)   // 10-bit unsigned
w.writeBool(true)                                        // 1 bit
w.writeString(countWidth = 8, value = "hello")           // 8-bit LE count + UTF-8
w.writeBlob(countWidth = 16, bytes = byteArrayOf(...))  // 16-bit LE count + bytes

// Nested: block runs against a CHILD writer; result is length-prefixed
w.writeNested(lengthPrefixWidth = 16) {
    writeScalar(ScalarType.of(8, signed = false), 1L)
    writeBitsLong(32, 1700000000L)
}

// Repeated: block runs against the PARENT writer (no child)
w.writeRepeated(count = 3, countWidth = 8) {
    writeScalar(ScalarType.of(16, signed = true), 100L)
}

val bytes: ByteArray = w.build()  // exact-length snapshot; writer is single-shot
```

**Critical constraints:**
- `writeBits` rejects `bitWidth > 31` (throws `IllegalArgumentException`).
  Use `writeBitsLong` for 32-bit writes.
- `writeScalar` dispatches to `writeBits` (≤31) or `writeBitsLong` (32–64)
  internally — prefer it over raw `writeBits` unless you proved the width.
- `writeNested` creates a **child** `KompactWriter`; `writeRepeated`
  writes to the **parent**. Do not confuse them.
- `writeRepeated`'s block has **no index parameter** — use a manual
  `for` loop if elements need different values.
- `writeString`, `writeBlob`, `writeNested`, `writeRepeated` all
  require `countWidth` / `lengthPrefixWidth` to be in
  `KompactFraming.VALID_PREFIX_WIDTHS` = `{8, 16, 32}`.

### KompactFraming — the reader's counterpart

```kotlin
// Parse-forward: read a length prefix, then consume that many bytes
val n: Int = KompactFraming.readLengthPrefix(bytes, bitCursor, bitWidth = 8)
// Returns INVALID_LENGTH_PREFIX (-1) if the prefix is invalid or overruns the buffer.

// Nested regions: returns a NestedRegionResult (zero-alloc, never throws on hot path)
val region = KompactFraming.readNested(bytes, bitCursor, prefixBitWidth = 16)
if (region.isSuccess) {
    val (startBit, bitLength) = region.getOrThrow()  // NestedRegion = Pair<Int, Int>
} else {
    when (val err = region.error) {
        KompactDecodeError.BadLengthPrefix -> …
        KompactDecodeError.TruncatedNested -> …
        else -> …
    }
}
```

### ScalarType — the width-and-signedness carrier

```kotlin
ScalarType.of(12, signed = true)    // 12-bit two's-complement
ScalarType.UINT_8                   // = of(8, signed = false)
ScalarType.INT_16                   // = of(16, signed = true)
ScalarType.BOOL                     // = of(1, signed = false)
```

`ScalarType.bitWidth` (1–64) and `ScalarType.signed` are the only two
fields — this collapses the old 8-per-width overload set
(`readInt8`, `readUInt16`, …) into one accessor per band.

### Kompact.Result — convenience typealiases

```kotlin
import ch.trancee.kompact.Kompact.Result.*

val r: Int = KompactRuntime.readScalar(raw, 0, ScalarType.of(10, false)).getOrThrow()
//   Kompact.Result.Int  →  IntResult
//   Kompact.Result.Long →  LongResult
//   Kompact.Result.Boolean → BooleanResult
//   … etc. (7 typealiases total)
```

### KompactDecodeError — the 4 subtypes

```kotlin
KompactDecodeError.BoundsError              // object — buffer too short
KompactDecodeError.BadLengthPrefix          // object — prefix overruns buffer or invalid width
KompactDecodeError.TruncatedNested          // object — nested region truncated
KompactDecodeError.UnknownEnumCode(rawCode) // data class — unknown enum value, carries rawCode: Int
```

`KompactDecodeException(error)` is the thrown variant — only
`getOrThrow()` / `readNestedOrThrow()` / `readOrThrow` can produce it,
and only on the **failure** path. The success path never throws.

## Defining a model

### Hand-written (v1 reference)

```kotlin
@file:OptIn(KompactPreview::class)
package ch.trancee.kompact.generated

@KompactModel
public expect value class SensorFrame(public val raw: ByteArray) {
    public companion object {
        public fun create(status: Int, battery: Int, temperature: Int): SensorFrame
    }
    @KompactField(bitOffset = 0,  bitWidth = 4)  public var status: Int
    @KompactField(bitOffset = 4,  bitWidth = 4)  public var battery: Int
    @KompactField(bitOffset = 8,  bitWidth = 12, signed = true) public var temperature: Int
}
```

Then in `jvmMain`:
```kotlin
@KompactModel @JvmInline
public actual value class SensorFrame(public actual val raw: ByteArray) {
    init { require(raw.size >= 2) }  // F-001: validate minimum buffer size
    public actual companion object {
        public actual fun create(status: Int, battery: Int, temperature: Int): SensorFrame =
            SensorFrame(encodeSensorFrame(status, battery, temperature))
    }
    @KompactField(bitOffset = 0, bitWidth = 4)
    public actual var status: Int
        get() = KompactRuntime.readScalar(raw, 0, ScalarType.of(4, false)).getOrThrow()
        set(v) { KompactRuntime.writeBits(raw, 0, 4, v) }
    // … battery, temperature similarly
}
```

```kotlin
internal fun encodeSensorFrame(status: Int, battery: Int, temperature: Int): ByteArray {
    val w = KompactWriter()
    w.writeScalar(ScalarType.of(4, signed = false), status.toLong())
    w.writeScalar(ScalarType.of(4, signed = false), battery.toLong())
    w.writeScalar(ScalarType.of(12, signed = true), temperature.toLong())
    return w.build()  // 3 bytes
}
```

```kotlin
@KompactModel
public actual value class SensorFrame(public actual val raw: ByteArray) {
    // NO @JvmInline here — Kotlin/Native doesn't support it
    init { require(raw.size >= 2) }
    // … identical bodies
}
```

### KSP-generated (production)

When the KSP processor is on the classpath, you write **only the `expect`
declaration** — the processor generates the `actual` class bodies, the
`create()` factory, and an `encodeSensorFrame()` helper:

```kotlin
@KompactModel
public expect value class SensorFrame(public val raw: ByteArray) {
    public companion object {
        public fun create(status: Int, battery: Int, temperature: Int): SensorFrame
    }
    @KompactField(bitOffset = 0,  bitWidth = 4)  public var status: Int
    @KompactField(bitOffset = 4,  bitWidth = 4)  public var battery: Int
    @KompactField(bitOffset = 8,  bitWidth = 12, signed = true) public var temperature: Int
}
```

The processor emits three files: `<Name>.kt` (commonMain),
`<Name>JvmActual.kt` (jvmMain), `<Name>IosActual.kt` (iosMain).
Generated getters use the **raw** `readBits` / `readBitsBoolean`
(zero-alloc, no bounds check) because the processor proves bounds at
compile time. Setters are write-through via `writeBits` /
`writeBitsBoolean`.

**Supported field types:** `Boolean`, `Int`, `Long`, `Float`, `Double`.
Variable-length types (`String`, `ByteArray`, nested, repeated) are
declared via `@KompactField` metadata but are not yet codegen'd —
use the hand-written `KompactWriter` path for those.

## @KompactField parameters

```
@KompactField(
    bitOffset: Int,           // required — bit position from LSB-first start
    bitWidth: Int,            // required — 1..64 for scalars
    signed: Boolean = false,  // true → two's-complement sign extension
    lengthPrefixWidth: Int = 8,   // for String/ByteArray: 8, 16, or 32
    isNested: Boolean = false,    // for nested composites
    repeatCountWidth: Int = 8,    // for repeated fields: 8, 16, or 32
    enumWidth: Int = 0,           // for enum-typed fields
    defaultValue: String = "",    // for versioning fallback
)
```

## Gotchas (the ones that break builds or cause silent data corruption)

1. **`@JvmInline` on expect classes.** Never use it in `commonMain`.
   `@JvmInline` is JVM-only — use plain `value class`. The
   annotation only belongs on the JVM `actual`.

2. **`writeBits(bitWidth = 32, …)` throws.** The limit is 1..31.
   Use `writeBitsLong(32, …)` or `writeScalar(ScalarType.of(32, …), …)`.

3. **`expect` properties don't carry `actual`.** The `actual` keyword
   on `raw`, `create()`, and field getters goes on the **actual** class
   only. The `expect` declaration just declares the shape.

4. **Setters are unchecked.** `set(v) { writeBits(raw, 0, 4, v) }` writes
   the low 4 bits only — `v = 16` silently truncates to 0. Validate
   before writing if the input is untrusted.

5. **`LongResult` sentinel band.** Values in
   `Long.MIN_VALUE..Long.MIN_VALUE + (1L shl 58) - 1` look like
   failures. Don't store Long values that might land in that range.

6. **`writeNested` vs `writeRepeated` receiver.** `writeNested`
   passes a **child** writer to the block; `writeRepeated` passes the
   **parent**. Don't mix them up.

7. **Prefix widths must be 8, 16, or 32.** `writeString(countWidth = 4, …)`
   compiles but throws at runtime (`countWidth must be 8, 16, or 32`).

8. **Float NaN canonicalization.** `FloatResult` canonicalizes wire NaN
   to quiet-NaN on success, so a NaN cannot masquerade as a failure.
   But `readBits` (raw) does not — use the checked accessors if you
   need NaN safety.

9. **`build()` is single-shot.** Calling `w.build()` twice returns an
   empty array the second time — the writer is forward-only by design.

10. **`@KompactPreview` opt-in.** All annotations require
    `@file:OptIn(KompactPreview::class)` per file. The opt-in is
    `Level.WARNING`, so missing it doesn't fail the build — but
    produces warnings.

## CI gates (what must stay green)

```bash
# Local
./gradlew spotlessCheck                              # ktlint formatting check
./gradlew :kompact:checkKotlinAbi                     # ABI golden check (jvmMain)
./gradlew :kompact-jvmTest                            # unit tests
./gradlew :kompact:koverVerify                        # 100% line + branch coverage
./gradlew :kompact:bundleAndroidMainAar               # Android AAR assembly
./gradlew :kompact:dokkaGeneratePublicationMarkdown   # regenerate docs/api/

# KSP module
./gradlew :kompact-ksp:checkKotlinAbi
./gradlew :kompact-ksp:test
./gradlew :kompact-ksp:koverVerify

# Full CI-equivalent chain
./gradlew clean spotlessCheck \
  :kompact:checkKotlinAbi :kompact:jvmTest :kompact:koverVerify \
  :kompact:bundleAndroidMainAar :kompact:dokkaGeneratePublicationMarkdown \
  :kompact:publishAllPublicationsToBundleDirRepository \
  :kompact:generateChecksums :kompact:assembleCentralBundle \
  :kompact-ksp:checkKotlinAbi :kompact-ksp:test :kompact-ksp:koverVerify \
  :kompact-ksp:generateChecksums :kompact-ksp:assembleCentralBundle \
  :kompact-ksp:publishAllPublicationsToBundleDirRepository --no-daemon --rerun-tasks
```

When you add or remove a public declaration, update the ABI goldens:
`./gradlew :kompact:updateKotlinAbi :kompact-ksp:updateKotlinAbi`
then commit the updated `*.api` files.

## Where to go deeper

| Topic | Doc |
|---|---|
| Write then read a frame end-to-end | [`docs/getting-started.md`](getting-started.md) |
| Define your own model (hand-written or KSP) | [`docs/how-to/define-message.md`](how-to/define-message.md) |
| Strings, blobs, nested, repeated (long-form payloads) | [`docs/how-to/long-form-payloads.md`](how-to/long-form-payloads.md) |
| Recover from bad buffers without throwing | [`docs/how-to/handle-decode-errors.md`](how-to/handle-decode-errors.md) |
| BLE send/receive integration | [`docs/how-to/integrate-ble.md`](how-to/integrate-ble.md) |
| Consume from a separate project | [`docs/how-to/consume-from-another-project.md`](how-to/consume-from-another-project.md) |
| Exact API signatures | [`docs/api-reference.md`](api-reference.md) |
| Wire format, error encoding, value-class layout | [`docs/architecture.md`](architecture.md) |
| KSP codegen internals | [`docs/research/ksp-kmp-generation.md`](research/ksp-kmp-generation.md) |
| ABI validation, release automation | [`docs/ci.md`](ci.md) |
| Design decisions | [`docs/adr/`](adr/) (4 ADRs) |
