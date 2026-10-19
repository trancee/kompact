# Agent quick start: Kompact library

This page is a short reference for AI agents that work with the Kompact
binary serialization library. It covers setup, the core concepts, the most
common API calls, and the mistakes that cause compile errors or runtime
failures.

The published `0.9.0` release supports sequential string, blob, nested, and
repeated fields. To use them, annotate the schema with
`@KompactModel(framed = true)` and number its fields with contiguous
`@KompactField(order = ...)` values. The generated view has a bounded
`decode(raw, start, end)` that returns a typed result. It borrows nested and
blob slices, and it reads repeated values lazily. Fixed-layout `bitOffset`
fields keep the fast scalar path. See
[the framed schema guide](../how-to/define-framed-schema.md) and
[ADR-0008](../adr/0008-framed-generated-views.md).

## What Kompact is

Kompact is a Kotlin Multiplatform library that serializes structured data into
bit-packed, LSB-first byte buffers. It supports JVM, Android JVM, iOS Arm64,
iOS Simulator Arm64, and Android Native Arm64. It is designed for compact
payloads such as BLE characteristics. Latency and allocation depend on the
API, the call shape, the compiler, and the platform. See the
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

- Write side: `KompactWriter` is a forward-only, growable writer. It frames
  strings, blobs, nested regions, and repeats with length prefixes.
- Read side: typed results and generated views read the caller-owned
  `ByteArray`. Borrowed slices and lazy repeated views avoid copying values
  where their API documents borrowing. Some properties copy their payload.
  Kompact makes no general zero-allocation guarantee.
- No `expect` or `actual`: the runtime result types and the fixed-layout views
  are common `@JvmInline value class` declarations. Kotlin/Native ignores
  `@JvmInline`. Each schema generates exactly one common source file.

## Project structure

```text
build-logic/                    included build with convention plugins
├── src/main/kotlin/
│   ├── portal-publish.gradle.kts     ← Maven Central Portal API tasks
│   ├── dokka-markdown.gradle.kts     ← GFM Markdown Dokka output
│   ├── kover-conventions.gradle.kts  ← Kover thresholds
│   ├── mutation-results.gradle.kts   ← MutFlow adapter and report tasks
│   └── ch/trancee/mutation/          ← typed mutation-results module
kompact/                        the runtime library (JVM, Android JVM, iOS, Android Native)
├── src/commonMain/kotlin/…/runtime/  ← KompactRuntime, KompactWriter, KompactFraming,
│                                     ←   KompactFrame, KompactCursor, KompactStatus,
│                                     ←   *Result types, ScalarType, KompactDecodeError
├── src/commonTest/kotlin/…/generated/VehicleTelemetry.kt  ← hand-written fixed-layout test fixture
└── api/                              ← committed ABI goldens (jvm/, android/, kompact.klib.api)
kompact-ksp/                    the KSP code generator
├── src/main/kotlin/…/gen/ValueClassGenerator.kt  ← generates <Name>View for fixed-layout schemas
└── api/kompact-ksp.api             ← committed KSP ABI golden
kompact-ksp-integration/        compiles generated consumers in tests (unpublished)
kompact-gradle-plugin/          common-source KMP code-generation plugin
└── id: ch.trancee.kompact.codegen
benchmarks/                     host benchmarks (unpublished)
└── holders/                    standalone consumer of the real plugin (unpublished)
```

The published JVM artifacts (`kompact-jvm`, `kompact-ksp`, and the Gradle
plugin) target JVM 17 bytecode, so JDK 17 consumers and compile daemons can
load them.

## Set up a consumer

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
    kotlin("multiplatform") version "2.4.21"
    id("ch.trancee.kompact.codegen") version "0.9.0"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    androidNativeArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.9.0")
            }
        }
    }
}
```

Apply the Kompact plugin after the Kotlin Multiplatform plugin in the
`plugins` block. The plugin runs common processing once and adds the
generated sources to `commonMain` only. Do not add the standard KSP plugin for
the same Kompact schemas. The plugin accepts any Kotlin release in the `2.4`
line and logs a warning for a release other than the one it was built with
([ADR-0012](../adr/0012-kotlin-minor-line-compatibility.md)).

To try unreleased changes, run this command from the Kompact checkout:

```bash
./gradlew :kompact:publishToMavenLocal :kompact-ksp:publishToMavenLocal :kompact-gradle-plugin:publishToMavenLocal
```

Then use version `0.10.0-SNAPSHOT` and add `mavenLocal()` to the
repositories. The [consumer setup guide](../how-to/consume-from-another-project.md)
has the full snapshot setup.

## Core API cheat sheet

### Packages

| Package | Contents |
|---|---|
| `ch.trancee.kompact.runtime` | `KompactRuntime`, `KompactWriter`, `KompactFraming`, `KompactFrame`, `KompactCursor`, `KompactStatus`, `ScalarType`, the `*Result` types, `KompactFrameResult`, `KompactDecodeError`, `KompactDecodeException`, `KompactByteSlice`, `KompactByteRange`, `KompactRepeatedView`, `KompactRepeatWorkspace` |
| `ch.trancee.kompact.annotations` | `@KompactModel`, `@KompactField`, `@KompactPreview` |

### KompactRuntime reads

```kotlin
// Raw reads: no bounds or width check. Use them only when you have proved the bounds.
val bits: Int = KompactRuntime.readBits(raw, bitOffset, bitWidth) // 0..32 bits
val bitsL: Long = KompactRuntime.readBitsLong(raw, bitOffset, bitWidth) // 0..64 bits
val bit: Boolean = KompactRuntime.readBitsBoolean(raw, bitOffset)

// Checked reads: return a typed result instead of throwing on a bounds error.
val speed: IntResult = KompactRuntime.readScalar(raw, 0, ScalarType.of(10, signed = false))
val flag: BooleanResult = KompactRuntime.readBool(raw, 14)
val temp: FloatResult = KompactRuntime.readFloat(raw, bitOffset)
val ts: LongResult = KompactRuntime.readScalarAsLong(raw, 20, ScalarType.of(32, signed = true))
val dbl: DoubleResult = KompactRuntime.readDouble(raw, bitOffset)

// Throwing reads: return the value or throw KompactDecodeException.
val speedValue: Int = KompactRuntime.readScalarOrThrow(raw, 0, ScalarType.of(10, signed = false))

// Recovery
if (speed.isSuccess) {
    val v: Int = speed.getOrThrow()
}
val v2: Int = speed.getOrElse { error -> 0 }
val mapped: IntResult = speed.map { it * 2 }
```

These rules apply to the reads:

- `KompactRuntime.readBits` and `KompactRuntime.writeBits` accept a `bitWidth`
  of `0..32`. `readBitsLong` and `writeBitsLong` accept `0..64`. A width of 0
  reads 0 and leaves the buffer unchanged.
- `readScalar` accepts `1..32`. Use `readScalarAsLong` for wider values.
- The throwing family is `readBoolOrThrow`, `readScalarOrThrow`,
  `readScalarAsLongOrThrow`, `readFloatOrThrow`, and `readDoubleOrThrow` on
  `KompactRuntime`, plus `KompactFraming.readLengthPrefixOrThrow`.
- `LongResult` is a regular class, so it can hold every `Long` value,
  including `Long.MIN_VALUE`. The other scalar result types are value classes,
  which may box at nullable, generic, or interface boundaries. Do not infer
  allocation behavior from the representation alone.
- `FloatResult` stores the raw 32-bit IEEE-754 bits in the low 48 bits of a
  packed `Long`, so NaN payloads round-trip. `DoubleResult` has no spare bits.
  It reserves the quiet-NaN payloads 1 to 5 for the five error kinds and turns
  every successful NaN into the quiet NaN with payload 0. If a `Double` NaN
  payload matters, call `readBitsLong(raw, offset, 64)`.

### KompactWriter writes

```kotlin
val w = KompactWriter()
w.writeScalar(ScalarType.of(4, signed = false), 5L) // 4-bit unsigned
w.writeScalar(ScalarType.of(10, signed = false), 10L) // 10-bit unsigned
w.writeBool(true) // 1 bit
w.writeString(countWidth = 8, value = "hello") // 8-bit LE length + UTF-8
w.writeBlob(countWidth = 16, bytes = byteArrayOf(1, 2, 3)) // 16-bit LE length + bytes

// Nested: the block writes into this same writer after a reserved length prefix.
w.writeNested(lengthPrefixWidth = 16) {
    writeScalar(ScalarType.of(8, signed = false), 1L)
    writeBitsLong(32, 1700000000L)
}

// Repeated: the writer writes the count, then runs the block once per element.
w.writeRepeated(count = 3, countWidth = 8) {
    writeScalar(ScalarType.of(16, signed = true), 100L)
}

val bytes: ByteArray = w.build() // exact-length snapshot
```

These rules apply to the writer:

- `writeBits` rejects a `bitWidth` outside `1..31` with
  `IllegalArgumentException`. Use `writeBitsLong` for 32-bit writes.
- `writeScalar` always writes through `writeBitsLong`, so it accepts the full
  `1..64` width that `ScalarType` allows.
- `writeNested` and `writeRepeated` both run their block against the same
  writer. `writeNested` reserves the prefix, pads the payload to whole bytes,
  and then writes the payload byte length into the prefix. If the block
  throws, or the length does not fit the prefix, the writer rolls back to its
  state before the call.
- `writeNested` also has overloads that copy an existing `KompactByteSlice`
  or a `ByteArray` region with its length prefix. `writeSlice` appends a
  `KompactByteSlice` without a prefix.
- To reuse a writer across messages, call `reset()`. It keeps the grown
  buffer.
- The `writeRepeated` block has no index parameter. If elements need
  different values, write the elements in a `for` loop inside the block or
  after a matching count.
- `writeString`, `writeBlob`, `writeNested`, and `writeRepeated` require
  `countWidth` or `lengthPrefixWidth` to be in
  `KompactFraming.VALID_PREFIX_WIDTHS`, which is `{8, 16, 32}`.
- A prefix value must fit its width. The maximum length or count is 255 for
  an 8-bit prefix, 65,535 for a 16-bit prefix, and `Int.MAX_VALUE` for a
  32-bit prefix.

### KompactFraming reads prefixes

```kotlin
// Read a length prefix. The caller then consumes that many bytes.
val n: Int = KompactFraming.readLengthPrefix(raw, bitOffset, bitWidth = 8)
// Returns INVALID_LENGTH_PREFIX (-1) if the prefix overruns the buffer.
// A width other than 8, 16, or 32 throws IllegalArgumentException.

// Nested regions: returns a typed result instead of throwing on malformed input.
val region = KompactFraming.readNested(raw, bitOffset, prefixBitWidth = 16)
if (region.isSuccess) {
    val startBit = region.startBit // throws KompactDecodeException on a failed result
    val bitLength = region.bitLength
} else {
    // readNested reports every malformed prefix as BadLengthPrefix.
    val error: KompactDecodeError? = region.error
}
```

### ScalarType carries width and signedness

```kotlin
ScalarType.of(12, signed = true) // 12-bit two's complement
ScalarType.UINT_8 // same as of(8, signed = false)
ScalarType.INT_16 // same as of(16, signed = true)
ScalarType.BOOL // same as of(1, signed = false)
```

`ScalarType` has two properties: `bitWidth` (1 to 64) and `signed`. One
`readScalar` call replaces the old per-width overloads (`readInt8`,
`readUInt16`, and so on). The constants are `INT_8`, `INT_16`, `INT_32`,
`INT_64`, `UINT_8`, `UINT_16`, `UINT_32`, `UINT_64`, and `BOOL`.
`ScalarType.of` throws `IllegalArgumentException` for a width outside 1 to 64.

### KompactDecodeError has five cases

```kotlin
KompactDecodeError.BoundsError // a read extends past the end of the input or region
KompactDecodeError.BadLengthPrefix // a length or count prefix is unreadable or too large
KompactDecodeError.TruncatedNested // a nested region ends before its declared content
KompactDecodeError.InvalidUtf8 // a framed string payload is malformed UTF-8
KompactDecodeError.TrailingData // unread bytes or non-zero padding after a framed decode
```

Each case is a `data object`. Checked scalar reads return the error in a
result value. Direct `KompactFrame` read methods throw
`KompactDecodeException` on malformed input. To get a typed
`KompactFrameResult` instead, wrap the reads in the block overload of
`KompactFrame.decode`.

Invalid caller arguments throw `IllegalArgumentException` instead. Examples
are a `KompactFrame.readBits` width outside 1 to 64, a prefix width other than
8, 16, or 32, an invalid repeat shape, and a `ScalarType` wider than 32 bits
passed to `readScalar`.

`KompactCursor` operations return a `KompactStatus` entry, for example
`KompactStatus.OK`, `BOUNDS_ERROR`, or `INVALID_WIDTH`. The generated holder
functions `decodeInto` and `encodeFrom` return the same type.
`KompactStatus.decodeError` maps each malformed-input status to its
`KompactDecodeError`. It is `null` for `OK` and for caller-contract statuses
([ADR-0013](../adr/0013-typed-status-and-caller-contract-errors.md)).

These calls throw `KompactDecodeException` only on failure: `getOrThrow()`,
`NestedRegionResult.startBit`, `NestedRegionResult.bitLength`, and the
`read*OrThrow` functions.

## Define a model

Declare a regular schema class in `commonMain`. The annotated properties
describe the wire layout. Their initializers are placeholders.

```kotlin
@file:OptIn(KompactPreview::class)

package example

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview

@KompactModel
public class SensorFrame {
    @KompactField(bitOffset = 0, bitWidth = 4)
    public val status: Int = 0

    @KompactField(bitOffset = 4, bitWidth = 4)
    public val battery: Int = 0

    @KompactField(bitOffset = 8, bitWidth = 12, signed = true)
    public val temperature: Int = 0
}
```

The processor writes one common file, `SensorFrameView.kt`. This outline
shows its contents:

```kotlin
@JvmInline
@KompactPreview
public value class SensorFrameView(
    public val raw: ByteArray,
) {
    init {
        require(raw.size >= 3) { "…" } // minimum buffer size of the 20-bit layout
    }

    @KompactField(bitOffset = 0, bitWidth = 4)
    public val status: Int
        get() = KompactRuntime.readBits(raw, 0, 4)

    // battery and temperature follow the same pattern; signed fields sign-extend.

    public fun copy(
        status: Int = this.status,
        battery: Int = this.battery,
        temperature: Int = this.temperature,
    ): SensorFrameView = SensorFrameView(encodeSensorFrameView(status, battery, temperature))

    public companion object {
        // Checked entry for untrusted input: Failure(BoundsError) if raw.size < 3.
        public fun decode(raw: ByteArray): KompactFrameResult<SensorFrameView>

        public fun create(
            status: Int,
            battery: Int,
            temperature: Int,
        ): SensorFrameView = SensorFrameView(encodeSensorFrameView(status, battery, temperature))
    }
}

// The same file also contains:
// - SensorFrameViewHolder, a class with one var per field,
// - SensorFrameViewHolder.decodeInto(cursor: KompactCursor): KompactStatus,
// - SensorFrameViewHolder.encodeFrom(cursor: KompactCursor): KompactStatus,
// - internal fun encodeSensorFrameView(...): ByteArray.
```

Use the view in one of three ways:

- `SensorFrameView.create(status = 1, battery = 9, temperature = -40)` builds
  a new buffer.
- `SensorFrameView.decode(receivedBytes)` checks untrusted input and returns a
  `BoundsError` failure for a short buffer.
- `SensorFrameView(trustedBytes)` throws `IllegalArgumentException` for a
  short buffer.

Framed schemas (`@KompactModel(framed = true)`) follow the same naming and
generate a regular `<Name>View` class. Marking a schema `expect` is a
processor error.

The default view's fields are read-only `val` properties. Fixed-layout getters
call the raw bit readers, so the minimum-buffer check in the view constructor
matters: the getter does not bounds-check each field. Write-through `var`
setters live on the `Mutable<Name>View` sibling that the processor generates
for `@KompactModel(mutable = true)`. Mutable framed models are rejected. See
[ADR-0006](../adr/0006-immutable-default-models.md) and
[ADR-0009](../adr/0009-common-only-code-generation.md).

Fixed-layout schemas support `Boolean`, `Int`, `Long`, `Float`, and `Double`
fields. Framed schemas also support `String`, `ByteArray`, nested framed
views, and lazy repeated `List<T>` fields. A field whose type is a framed
model is a nested region. The processor infers that from the type
([ADR-0011](../adr/0011-inferred-nested-fields-and-checked-fixed-decode.md)).
See [`define-message.md`](../how-to/define-message.md).

## @KompactField parameters

| Parameter | Default | Meaning |
|---|---|---|
| `bitOffset` | `0` | Fixed-layout start bit, LSB-first. Unused in framed models. |
| `bitWidth` | `0` | Scalar width, 1 to 64. |
| `signed` | `false` | `true` sign-extends the value as two's complement. |
| `lengthPrefixWidth` | `8` | Prefix width for a string, blob, nested model, or variable-length repeat element: 8, 16, or 32. |
| `repeatCountWidth` | `8` | Prefix width for the count of a repeated field: 8, 16, or 32. |
| `order` | `-1` | Position in a framed model, contiguous from zero. Unused in fixed layouts. |

## Gotchas

These mistakes break builds or corrupt data without an error:

1. Schemas are regular classes. Do not declare a schema as `expect`, and do
   not write its view by hand. The processor rejects `expect` schemas and
   generates `<Name>View` itself.

2. `writeBits(bitWidth = 32, …)` throws, because the limit is `1..31`. Use
   `writeBitsLong(32, …)` or `writeScalar(ScalarType.of(32, …), …)`.

3. Use the generated view type. Application code reads and writes
   `SensorFrameView`, not the schema class `SensorFrame`. Schema property
   initializers never reach the wire.

4. Setters do not check values. On `MutableSensorFrameView`, the `status`
   setter calls `KompactRuntime.writeBits(raw, 0, 4, value)`, which writes
   only the low 4 bits. Setting `status = 16` writes 0. If the input is
   untrusted, validate it before you write.

5. `LongResult` is a regular class so that it can hold every signed `Long`
   value. The other scalar result types are value classes. Boxing and
   allocation depend on the call shape and the platform, so the type
   difference is not a performance guarantee.

6. Do not call `build()` inside a `writeNested` block. The block writes into
   the enclosing writer, so `build()` would return the whole message with a
   prefix that is not written yet.

7. Prefix widths must be 8, 16, or 32. `writeString(countWidth = 4, …)`
   compiles but throws `IllegalArgumentException` at runtime with the message
   `length-prefix bit width must be 8, 16, or 32, was 4`.

8. `DoubleResult` turns every successful NaN into the quiet NaN with payload
   0, because NaN payloads 1 to 5 encode failures. `FloatResult` keeps the raw
   bits. If a `Double` NaN payload matters, call `readBitsLong(raw, offset, 64)`.

9. `build()` returns a snapshot. Repeated calls return the writer's current
   contents and do not consume them.

10. `@KompactModel`, `@KompactField`, and the generated declarations carry
    `@KompactPreview`. Add `@file:OptIn(KompactPreview::class)` to each file
    that uses them. The opt-in level is `WARNING`, so a missing opt-in does
    not fail the build, but the compiler reports warnings.

## Focused verification

```bash
./gradlew :kompact:jvmTest
./gradlew :kompact-ksp:test
./gradlew :kompact-ksp-integration:test
./gradlew :kompact-gradle-plugin:test
./gradlew spotlessCheck detekt
```

CI splits the required checks across Linux and macOS jobs and runs a separate
mutation-testing job. The [CI guide](../ci.md) has the complete commands for
each host, and [the workflow](../../.github/workflows/ci.yml) is
authoritative. The focused commands above do not replace the CI checks.

When you add or remove a public declaration, update the ABI goldens of each
module you changed and commit the updated `*.api` files:

```bash
./gradlew :kompact:updateKotlinAbi :kompact-ksp:updateKotlinAbi :kompact-gradle-plugin:updateKotlinAbi
```

Only a macOS host produces `kompact/api/kompact.klib.api`. Without a Mac, run
the `Regen Goldens` workflow described in the [CI guide](../ci.md).

## Where to go deeper

| Topic | Doc |
|---|---|
| Write then read a frame end to end | [`docs/getting-started.md`](../getting-started.md) |
| Define a fixed-layout model | [`docs/how-to/define-message.md`](../how-to/define-message.md) |
| Define a framed model | [`docs/how-to/define-framed-schema.md`](../how-to/define-framed-schema.md) |
| Strings, blobs, nested regions, and repeats | [`docs/how-to/long-form-payloads.md`](../how-to/long-form-payloads.md) |
| Recover from bad buffers without throwing | [`docs/how-to/handle-decode-errors.md`](../how-to/handle-decode-errors.md) |
| BLE send and receive integration | [`docs/how-to/integrate-ble.md`](../how-to/integrate-ble.md) |
| Consume from a separate project | [`docs/how-to/consume-from-another-project.md`](../how-to/consume-from-another-project.md) |
| Exact API signatures | [`docs/api-reference.md`](../api-reference.md) |
| Wire format, error encoding, and value-class layout | [`docs/architecture.md`](../architecture.md) |
| KSP code generation internals | [`docs/research/ksp-kmp-generation.md`](../research/ksp-kmp-generation.md) |
| ABI validation, release automation, and mutation testing | [`docs/ci.md`](../ci.md) |
| Mutation testing results | [`docs/mutation-testing-disposition.md`](../mutation-testing-disposition.md) |
| Design decisions | [`docs/adr/`](../adr/) |
