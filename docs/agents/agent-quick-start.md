# Agent Quick-Start: Kompact Library

A 2-minute reference for AI agents working with the Kompact binary
serialization library. Covers setup, core concepts, the most common
API calls, and the traps that cause compile errors or runtime
failures.

For sequential string/blob/nested/repeated fields in the published `0.8.0`
release, opt in with `@KompactModel(framed = true)` and contiguous
`@KompactField(order = ...)`.
The generated `SchemaView` exposes bounded `decode(raw, start, end)` typed
results, borrowed nested/blob slices and lazy repeated values. Fixed-layout
`bitOffset` fields retain the scalar fast path. See
[the framed schema guide](../how-to/define-framed-schema.md)
and [ADR-0008](../adr/0008-framed-generated-views.md).

## What is Kompact (10 s)

Kompact is a Kotlin Multiplatform library that serializes structured data into
**bit-packed, LSB-first** byte buffers. It supports JVM, Android JVM, iOS
Arm64, iOS Simulator Arm64, and Android Native Arm64, and was designed for
compact payloads such as BLE characteristics. Latency and allocation behavior
depend on the API, call shape, compiler, and platform; see the
[allocation research note](../research/allocation-boxing-measurement.md).

```text
┌───────────────┐  KompactWriter  ┌─────────┐  BLE  ┌──────────────┐
│  field values │ ──────────────► │  bytes  │ ────► │  0xA5 0x40   │
└───────────────┘                 └─────────┘       └──────────────┘
                                              ◄────  characteristic
┌───────────────┐  value class   ┌─────────┐  BLE  ┌────────────────┐
│  tel.battery  │ ◄───────────── │  bytes  │ ◄──── │  notification  │
└───────────────┘     (view)     └─────────┘       └────────────────┘
```

- **Write side:** `KompactWriter` — forward-only, growable,
  length-prefixed framing for strings/blobs/nested/repeated.
- **Read side:** typed results and generated views read the caller-owned
  `ByteArray`; borrowed slices and lazy repeated views avoid materializing
  values where their API documents borrowing. Some properties copy their
  payload, and no general zero-allocation guarantee is made.
- **Platform actuals:** common declarations use multiplatform value-class
  syntax; JVM actuals use `@JvmInline`, while Kotlin/Native actuals do not.

## Project structure (at a glance)

```text
build-logic/                    convention plugins (portal-publish, dokka-markdown)
├── src/main/kotlin/
│   ├── portal-publish.gradle.kts   ← Maven Central Portal API tasks
│   └── dokka-markdown.gradle.kts   ← GFM Markdown Dokka output
kompact/                        the runtime library (JVM, Android JVM, iOS, Android Native)
├── src/commonMain/kotlin/…/runtime/  ← KompactRuntime, KompactWriter, KompactFraming,
│                                     ←   KompactResult*, ScalarType, KompactDecodeError
├── src/commonMain/kotlin/…/generated/VehicleTelemetry.kt  ← example model (expect/actual)
├── src/jvmCommon/kotlin/…/         ← @JvmInline actuals (shared by JVM + Android)
├── src/nativeMain/kotlin/…/        ← plain actuals shared by iOS + Android Native
└── api/                              ← committed ABI goldens (jvm/ + android/ + kompact.klib.api)
kompact-ksp/                    the KSP code generator
├── src/main/kotlin/…/gen/ValueClassGenerator.kt  ← generates expect/actual from annotations
└── api/kompact-ksp.api             ← committed KSP ABI golden
kompact-gradle-plugin/           common-source KMP code-generation plugin
└── id: ch.trancee.kompact.codegen
```

> **Key constraint:** `kompact-ksp` is a JVM-only module targeting
> JVM 17 (not 21). The consumer's Kotlin compile daemon loads the
> processor; JVM 21 bytecode would exclude JDK 17 consumers.

## Setup (consumer)

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts (consumer module)
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("ch.trancee.kompact.codegen") version "0.8.0"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    androidNativeArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.8.0")
            }
        }
    }
}

```

The Kompact plugin must follow the Kotlin Multiplatform plugin in the `plugins`
block. It runs common processing once and adds generated common/platform
sources; do not add the standard KSP plugin for the same Kompact schemas.
To try unreleased changes, publish the current snapshot modules locally with
the commands in the
[consumer setup guide](../how-to/consume-from-another-project.md).

For the current checkout, run
`./gradlew :kompact:publishToMavenLocal :kompact-ksp:publishToMavenLocal :kompact-gradle-plugin:publishToMavenLocal`
from the Kompact checkout, then use `0.8.0` and `mavenLocal()`.

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
// Raw primitives (no bounds check — use only when you have proved the bounds)
val bits: Int = KompactRuntime.readBits(raw, bitOffset, bitWidth)    // 1..31 bits
val bitsL: Long = KompactRuntime.readBitsLong(raw, bitOffset, bitWidth)  // 1..64 bits
val bit: Boolean = KompactRuntime.readBitsBoolean(raw, bitOffset)

// Checked accessors (return typed results, never throw on success)
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
- `LongResult` is a regular class so every `Long` value, including
  `Long.MIN_VALUE`, is representable. Other scalar result types use value
  classes, which may box at nullable, generic, or interface boundaries.
  Do not infer allocation behavior from representation alone.
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

val bytes: ByteArray = w.build()  // exact-length snapshot; repeated calls preserve contents
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
- Prefix values must fit their selected width: byte lengths/counts are
  capped at 255 for 8-bit prefixes, 65,535 for 16-bit prefixes, and
  `Int.MAX_VALUE` for 32-bit prefixes.

### KompactFraming — the reader's counterpart

```kotlin
// Parse-forward: read a length prefix, then consume that many bytes
val n: Int = KompactFraming.readLengthPrefix(bytes, bitCursor, bitWidth = 8)
// Returns INVALID_LENGTH_PREFIX (-1) if the prefix is invalid or overruns the buffer.

// Nested regions: returns a typed result instead of throwing on malformed input
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

### KompactDecodeError — the 5 subtypes

```kotlin
KompactDecodeError.BoundsError              // object — buffer too short
KompactDecodeError.BadLengthPrefix          // object — prefix overruns buffer or invalid width
KompactDecodeError.TruncatedNested          // object — nested region truncated
KompactDecodeError.InvalidUtf8              // object — framed string payload is malformed UTF-8
KompactDecodeError.UnknownEnumCode(rawCode) // data class — unknown enum value, carries rawCode: Int
```

Checked scalar accessors return errors as result values. Direct `KompactFrame`
read methods throw `KompactDecodeException` on malformed input; wrap them in
the block overload of `KompactFrame.decode` to receive a typed result.
`getOrThrow()` / `readNestedOrThrow()` / `readOrThrow` also throw only on failure.

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
    @KompactField(bitOffset = 0,  bitWidth = 4)  public val status: Int
    @KompactField(bitOffset = 4,  bitWidth = 4)  public val battery: Int
    @KompactField(bitOffset = 8,  bitWidth = 12, signed = true) public val temperature: Int
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
    public actual val status: Int
        get() = KompactRuntime.readScalar(raw, 0, ScalarType.of(4, false)).getOrThrow()
    // … battery, temperature similarly (val readers; opt-in Mutable sibling for writes)
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
    @KompactField(bitOffset = 0,  bitWidth = 4)  public val status: Int
    @KompactField(bitOffset = 4,  bitWidth = 4)  public val battery: Int
    @KompactField(bitOffset = 8,  bitWidth = 12, signed = true) public val temperature: Int
}
```

The common-generation plugin processes schemas once and registers generated
common and platform sources for the selected targets. The runtime's JVM
actuals live in `jvmCommon`; plain Kotlin/Native actuals live in `nativeMain`.
The default view's fields are `val` (read-only). Fixed-layout getters use raw
bit readers, so the platform actual constructor's minimum-buffer check is
important; the getter itself does not bounds-check each field. Write-through
`var` setters live on the opt-in `Mutable<Name>` sibling emitted when
`@KompactModel(mutable = true)` (see
[ADR-0006](../../docs/adr/0006-immutable-default-models.md)).

**Supported field types:** fixed-layout schemas support `Boolean`, `Int`,
`Long`, `Float`, and `Double`. Sequential schemas declared with
`@KompactModel(framed = true)` also generate `String`, `ByteArray`, nested
framed views, and lazy repeated fields. See
[`define-message.md`](../how-to/define-message.md).

## @KompactField parameters

```kotlin
@KompactField(
    bitOffset: Int = 0,       // fixed-layout bit position from LSB-first start
    bitWidth: Int = 0,        // scalar width; 1..64
    signed: Boolean = false,  // true → two's-complement sign extension
    lengthPrefixWidth: Int = 8,   // String/blob/nested/variable repeat element
    isNested: Boolean = false,   // nested framed schema
    repeatCountWidth: Int = 8,   // repeat count prefix: 8, 16, or 32
    enumWidth: Int = 0,          // for enum-typed fields
    defaultValue: String = "",   // reserved; not supported by generation
    order: Int = -1,             // framed field order, contiguous from zero
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

5. **`LongResult` representation.** It is a regular class so it can preserve
   every signed `Long` value; other scalar result types use value classes.
   Boxing and allocation depend on call shape and platform, so do not treat
   the type distinction as a performance guarantee.

6. **`writeNested` vs `writeRepeated` receiver.** `writeNested`
   passes a **child** writer to the block; `writeRepeated` passes the
   **parent**. Don't mix them up.

7. **Prefix widths must be 8, 16, or 32.** `writeString(countWidth = 4, …)`
   compiles but throws at runtime (`countWidth must be 8, 16, or 32`).

8. **Float NaN canonicalization.** `FloatResult` canonicalizes wire NaN
   to quiet-NaN on success, so a NaN cannot masquerade as a failure.
   But `readBits` (raw) does not — use the checked accessors if you
   need NaN safety.

9. **`build()` returns a snapshot.** Repeated calls return the writer's
   current contents without consuming them.

10. **`@KompactPreview` opt-in.** All annotations require
    `@file:OptIn(KompactPreview::class)` per file. The opt-in is
    `Level.WARNING`, so missing it doesn't fail the build — but
    produces warnings.

## Focused verification

```bash
./gradlew :kompact:jvmTest
./gradlew :kompact-ksp:test
./gradlew :kompact-ksp-integration:test
./gradlew :kompact-gradle-plugin:test
./gradlew spotlessCheck
```

CI splits required checks across Linux and macOS. The
[CI guide](../ci.md) has the complete host-specific commands, and
[the workflow](../../.github/workflows/ci.yml) is authoritative; these focused
commands are not a CI-equivalent gate.

When you add or remove a public declaration, update the ABI goldens:
`./gradlew :kompact:updateKotlinAbi :kompact-ksp:updateKotlinAbi`
then commit the updated `*.api` files.

## Where to go deeper

| Topic | Doc |
|---|---|
| Write then read a frame end-to-end | [`docs/getting-started.md`](../getting-started.md) |
| Define your own model (hand-written or KSP) | [`docs/how-to/define-message.md`](../how-to/define-message.md) |
| Strings, blobs, nested, repeated (long-form payloads) | [`docs/how-to/long-form-payloads.md`](../how-to/long-form-payloads.md) |
| Recover from bad buffers without throwing | [`docs/how-to/handle-decode-errors.md`](../how-to/handle-decode-errors.md) |
| BLE send/receive integration | [`docs/how-to/integrate-ble.md`](../how-to/integrate-ble.md) |
| Consume from a separate project | [`docs/how-to/consume-from-another-project.md`](../how-to/consume-from-another-project.md) |
| Exact API signatures | [`docs/api-reference.md`](../api-reference.md) |
| Wire format, error encoding, value-class layout | [`docs/architecture.md`](../architecture.md) |
| KSP codegen internals | [`docs/research/ksp-kmp-generation.md`](../research/ksp-kmp-generation.md) |
| ABI validation, release automation | [`docs/ci.md`](../ci.md) |
| Design decisions | [`docs/adr/`](../adr/) |
