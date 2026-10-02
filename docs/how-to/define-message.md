# How to define a message model

## Generate a sequential framed schema (0.5.0)

For variable-length fields, use a separate framed declaration rather than
assigning offsets to fields after a payload:

```kotlin
@file:OptIn(KompactPreview::class)

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview

@KompactModel(framed = true)
public class Packet {
    @KompactField(order = 0, lengthPrefixWidth = 8)
    public val label: String = ""

    @KompactField(order = 1, lengthPrefixWidth = 16)
    public val payload: ByteArray = byteArrayOf()

    @KompactField(order = 2, bitWidth = 16, repeatCountWidth = 8)
    public val samples: List<Int> = emptyList()
}
```

### Generate common KMP sources

For a Kotlin Multiplatform consumer, apply the Kompact Gradle plugin after the
Kotlin Multiplatform plugin. Do not also apply the standard KSP plugin for
Kompact schemas; the Kompact plugin runs common processing once and registers
the generated expect and platform actual declarations:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("ch.trancee.kompact.codegen") version "0.5.0-SNAPSHOT"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    androidNativeArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.5.0-SNAPSHOT")
            }
        }
    }
}
```

The plugin supports JVM, Android JVM, iOS Arm64, iOS Simulator Arm64, and
Android Native Arm64 targets. Framed schemas are ordinary annotated classes
in `commonMain`; fixed-layout schemas retain their handwritten common
`expect value class` declaration, while the plugin generates their encoder
and platform actuals.

For the current unpublished snapshot, first publish the runtime, processor,
and Gradle plugin locally:

```shell
./gradlew :kompact:publishToMavenLocal :kompact-ksp:publishToMavenLocal :kompact-gradle-plugin:publishToMavenLocal
```

Then add `mavenLocal()` to both `pluginManagement.repositories` and
`dependencyResolutionManagement.repositories` in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
```

Released versions resolve from Maven Central; keep `mavenCentral()` in both
repository lists and remove `mavenLocal()` after using a released version.

The generated view shape is a regular expect/actual `PacketView` with
`create(label, payload, samples)`, `copy(...)`, and
`decode(raw, start, end)` returning `KompactFrameResult<PacketView>`.
Source declarations use `List<Int>`
for repeated fields; generated properties expose the read-only
`KompactRepeatedView<Int>` instead. Its elements decode lazily; the count and
element boundaries are checked when decoding. `payload` explicitly copies on
each access; `payloadSlice` exposes the original array and bounded
`start`/`end` without copying. Nested framed model fields use
`isNested = true` and borrow the same backing array. Variable-length elements
in a repeated `List<String>`, `List<ByteArray>`, or `List<NestedModel>` use
`lengthPrefixWidth`; nested elements also set `isNested = true`.
For repeated variable-width values, `field.getElementSlice(index)` returns a
typed-result borrowed slice of the element payload, excluding its prefix.
This avoids copying a repeated `ByteArray` element.
Parameterized nested schema classes and mutable framed fields are not supported.
Use `samples.getResult(index)` to inspect a typed error from a lazily decoded
element; ordinary `samples[index]` is the throwing convenience.

Order values must be contiguous from zero. Scalar fields use `bitWidth`;
variable payloads require a byte-aligned start. Untrusted input must go through
`decode`: inspect its typed `error` or call `getOrThrow()` explicitly. Generated
views validate every declared field and reject unread trailing bytes. The
backing array is borrowed. Keep it unchanged while any decoded view or lazy
repeated field is in use: changing a variable-length prefix invalidates the
validated sparse index, and behavior after such mutation is unsupported. Some
changed prefixes are detected as typed failures, but callers must not rely on
that. Use `KompactByteSlice.toByteArray()` when independent ownership is needed.
Fixed-layout
`bitOffset` schemas below retain their scalar fast path.

**KMP generation:** the `ch.trancee.kompact.codegen` plugin provides the
common-source integration; the limitation applies to the standard KSP Gradle
plugin, not the Kompact plugin. The TestKit consumer compiles generated sources
for JVM, iOS Arm64, iOS Simulator Arm64, and Android Native Arm64. See
[KSP common-schema generation across targets](../research/ksp-kmp-generation.md)
for its implementation and compatibility constraints.

Goal: a Kotlin `value class` that reads, writes, and mutates a Kompact
frame — same shape as the bundled `VehicleTelemetry`, applied to your
own schema.

This guide walks you through defining a 4-byte sensor frame (32-bit
timestamp + 12-bit temperature + 4-bit humidity + 4-bit battery + 4-bit
status) and verifying it round-trips.
See the bundled [`VehicleTelemetry`](../api-reference.md#vehicletelemetry-example-model)
for the full pattern.

## 1. Lay out the bits

Pick a schema, then write the bit layout table. LSB-first packing:
field 0 occupies the low bits, field N occupies the next higher bits.

| Bits    | Width | Field         | Type           | Notes |
| ------- | ----- | ------------- | -------------- | ----- |
| 0..3    | 4     | `status`      | `Int` (0–15)   | enum ordinal |
| 4..7    | 4     | `battery`     | `Int` (0–15)   | percent / 6.25 |
| 8..19   | 12    | `temperature` | `Int` (−2048..2047) | signed; offset = -40 °C |
| 20..31  | 12    | `timestamp`   | `Int` (0–4095) | seconds / 16 |

> **Tip.** Keep the schema in a comment next to the value class — the
> `bitOffset` numbers in the annotations are the only place this lives
> in code, so a future reader needs the table.

## 2. Declare the `expect value class` in `commonMain`

Kompact models are `expect value class` declarations in `commonMain` —
no `@JvmInline` in common, no body. Each annotation just describes
where the field lives; the actual read/write code is in a single shared
helper, so the per-platform actuals are one-liners.

Create `src/commonMain/kotlin/your/package/SensorFrame.kt`:

```kotlin
@file:OptIn(KompactPreview::class)
package your.package

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

// Layout (LSB-first, 32 bits total → 4 bytes):
//   [0..3]    (4)  status
//   [4..7]    (4)  battery
//   [8..19]  (12)  temperature (signed, -40 °C offset)
//   [20..31] (12)  timestamp
@KompactModel
public expect value class SensorFrame(public val raw: ByteArray) {

    public companion object {
        /** Encode a fully-specified frame from the four field values. */
        public fun create(
            status: Int,
            battery: Int,
            temperature: Int,
            timestamp: Int,
        ): SensorFrame
    }

    @KompactField(bitOffset = 0,  bitWidth = 4)  public val status: Int
    @KompactField(bitOffset = 4,  bitWidth = 4)  public val battery: Int
    @KompactField(bitOffset = 8,  bitWidth = 12) public val temperature: Int
    @KompactField(bitOffset = 20, bitWidth = 12) public val timestamp: Int
}
```

> **Default view is read-only.** Fields are `val`; there are no setters.
> In-place mutation is opt-in: set `mutable = true` on the schema's
> `@KompactModel`, which emits a `MutableSensorFrame` sibling whose `var`
> fields write through to the same `raw` buffer. See
> [ADR-0006](../../docs/adr/0006-immutable-default-models.md)
> and the bundled `VehicleTelemetry` / `MutableVehicleTelemetry` pair.

```kotlin
/** Shared encoder used by the platform `create` actuals. */
internal fun encodeSensorFrame(
    status: Int, battery: Int, temperature: Int, timestamp: Int,
): ByteArray {
    val w = KompactWriter()
    w.writeScalar(ScalarType.of(4,  signed = false), status.toLong())
    w.writeScalar(ScalarType.of(4,  signed = false), battery.toLong())
    w.writeScalar(ScalarType.of(12, signed = true),  temperature.toLong())
    w.writeScalar(ScalarType.of(12, signed = false), timestamp.toLong())
    return w.build()  // 4 bytes
}
```

**Why `expect` with no body.** The value-class representation
(`@JvmInline` on JVM, plain `value class` on iOS) differs per platform;
the `expect` declaration hides that. You provide the platform actual
once per target.

**Why a shared encoder.** Each platform's `create` would otherwise be
three near-identical lines; sharing the encoder keeps the wire format
in one place.

## 3. Add the JVM and iOS actuals

The per-platform actuals are one-liners — the wire format is
identical, the only thing that differs is the `@JvmInline` annotation.

**`src/jvmMain/kotlin/your/package/SensorFrame.kt`:**

```kotlin
@file:OptIn(KompactPreview::class)
package your.package

import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

@KompactModel
public actual value class SensorFrame(public actual val raw: ByteArray) {

    public actual companion object {
        public actual fun create(
            status: Int, battery: Int, temperature: Int, timestamp: Int,
        ): SensorFrame = SensorFrame(
            encodeSensorFrame(status, battery, temperature, timestamp)
        )
    }

    public actual val status: Int
        get() = KompactRuntime.readScalar(raw, 0, ScalarType.of(4,  signed = false)).getOrThrow()

    public actual val battery: Int
        get() = KompactRuntime.readScalar(raw, 4, ScalarType.of(4,  signed = false)).getOrThrow()

    public actual val temperature: Int
        get() = KompactRuntime.readScalar(raw, 8, ScalarType.of(12, signed = true)).getOrThrow()

    public actual val timestamp: Int
        get() = KompactRuntime.readScalar(raw, 20, ScalarType.of(12, signed = false)).getOrThrow()

    init {
        require(raw.size >= 4) { "SensorFrame needs 4 bytes; got ${raw.size}" }
    }
}
```

**`src/iosMain/kotlin/your/package/SensorFrame.kt`** — identical to
the JVM actual except the class declaration drops `@JvmInline`:

```kotlin
@KompactModel
public actual value class SensorFrame(public actual val raw: ByteArray) {
    // ...rest of the body identical to the JVM actual...
}
```

See the bundled [`VehicleTelemetry`](../api-reference.md#vehicletelemetry-example-model)
for the full pattern.

## 4. Round-trip it

```kotlin
// Encode
val frame = SensorFrame.create(
    status = 2,
    battery = 13,
    temperature = 525,    // raw 12-bit signed value (e.g. 525 = 565 °C with -40 °C offset)
    timestamp = 1024,
)
println(frame.raw.toHexString())   // → e.g. "d20d0240" (4 bytes, LSB-first field packing)

// Decode (e.g. from BLE)
val received = SensorFrame(bleCharacteristic.value)
println("status=${received.status} battery=${received.battery} " +
        "temp=${received.temperature} ts=${received.timestamp}")

// For zero-alloc in-place writes, opt into the MutableSensorFrame
// sibling (emitted when the schema is annotated @KompactModel(mutable = true)):
val mutable = MutableSensorFrame(received.raw)
mutable.battery = 7
// Send the same buffer back over BLE:
bleCharacteristic.value = received.raw
```

`MutableSensorFrame` is the write-through companion to the read-only
default view — same `raw` buffer, `var` setters that write each bit-field
in place. See [ADR-0006](../adr/0006-immutable-default-models.md) and the
bundled `MutableVehicleTelemetry` for the full pattern.

**Sanity check.** `frame.raw.size == 4` and the same value class
re-rendered produces the same bytes. If you change a `bitOffset` or
`bitWidth` in the annotations, also change the shared encoder and the
getter/setter bit offsets to match — they are independent, so a typo
there silently shifts the wire format.

## 5. Add a pinned test

Treat the wire bytes as part of the contract. A test like this fails
before the framework ships a change that breaks it:

```kotlin
@OptIn(KompactPreview::class)
class SensorFrameTest {
    @Test
    fun round_trip() {
        val frame = SensorFrame.create(status = 2, battery = 13, temperature = 525, timestamp = 1024)
        assertEquals(4, frame.raw.size)
        val read = SensorFrame(frame.raw)
        assertEquals(2,    read.status)
        assertEquals(13,   read.battery)
        assertEquals(525,  read.temperature)
        assertEquals(1024, read.timestamp)
    }
}
```

## Common pitfalls

- **Annotation vs runtime offset drift.** `@KompactField(bitOffset = N)`
  is metadata — the actual offset is whatever you pass to
  `readScalar` / `writeBits`. Keep them adjacent in the file; review
  both when you touch either.
- **Field width in the annotations vs `ScalarType.of`.** Both must
  match. A 4-bit field is `bitWidth = 4` AND `ScalarType.of(4, …)`.
- **Signed bit interpretation.** A 12-bit signed field has range
  `−2048..2047`. Pass `signed = true` in *both* the encoder and the
  getter.
- **Value truncation.** Kompact trusts your value to fit the width —
  passing `battery = 16` to a 4-bit field silently truncates the high
  bits. Validate at the write site if the input is untrusted.
- **Forgetting `@OptIn(KompactPreview::class)`.** The annotations and
  the value class are preview-API; without the opt-in the file does
  not compile.

## Using the KSP processor (optional)

The `kompact-ksp` processor generates fixed-layout scalar views and framed
views from `@KompactModel` / `@KompactField` declarations. It selects output
for each processing invocation with the `kompact.generate` option:

| Mode | Output |
| --- | --- |
| `common` | `expect` declaration |
| `jvm` | JVM `actual` declaration |
| `ios` | iOS `actual` declaration |
| `androidArm64` | Android Native `actual` declaration |
| `all` (default) | common, JVM, and iOS files |

KSP's `ksp { arg(...) }` block applies one option value to every processing
task in the module. A multiplatform consumer that needs different outputs per
target must configure each KSP task with its own processor argument.

**Common-source limitation:** the standard KSP2 Gradle integration still does
not connect `kspCommonMainMetadata` output to every target's `commonMain`.
Use `ch.trancee.kompact.codegen` for Kompact's KMP generation path. The
`kompact-gradle-plugin` TestKit fixture verifies common and platform source
generation, compilation, build-cache relocation, and configuration-cache
reuse; `kompact-ksp-integration:test` separately exercises JVM generated-code
round trips.

### What the processor generates

The processor has separate output shapes for fixed and framed schemas:

| Schema | Common declaration | Platform declaration |
| --- | --- | --- |
| Fixed layout | `expect value class <Name>` | JVM `@JvmInline actual value class`; Native plain `actual value class` |
| Framed | `expect class <Name>View` | JVM/Native regular `actual class <Name>View` |

Fixed-layout schemas support `Boolean`, `Int`, `Long`, `Float`, and
`Double`; their generated getters use raw `KompactRuntime` reads after
compile-time layout validation. Framed schemas support those scalar types,
`String`, `ByteArray`, nested framed schemas, and `List<T>` repeats over
supported element types. Their generated readers use the bounded
`KompactFrame`; `defaultValue` is not currently supported by generation.

The framed input is a regular schema declaration with a distinct generated
`<Name>View` type, as shown at the top of this guide. The generated view is
not the annotated input class. Fixed-layout value-class generation is
documented in the architecture reference, but its common-source wiring has
the same KSP2 limitation described above.

## What's next

- Manual low-level framing with strings, blobs, nested regions, and repeats:
  [`long-form-payloads.md`](long-form-payloads.md).
- How to recover from a bad wire buffer without throwing:
  [`handle-decode-errors.md`](handle-decode-errors.md).
- Send / receive this frame over BLE:
  [`integrate-ble.md`](integrate-ble.md).
