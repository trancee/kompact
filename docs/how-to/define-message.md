# How to define a message model

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

> **Default view is read-only.** Fields are `val`; there are no setters.
> In-place mutation is opt-in: set `mutable = true` on the schema's
> `@KompactModel`, which emits a `MutableSensorFrame` sibling whose `var`
> fields write through to the same `raw` buffer. See
> [ADR-0006](../../docs/adr/0006-immutable-default-models.md)
> and the bundled `VehicleTelemetry` / `MutableVehicleTelemetry` pair.

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
in place. See [ADR-0006](adr/0006-immutable-default-models.md) and the
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

The hand-written pattern above — `@KompactField` annotations + manual
`readScalar`/`writeBits` bodies in each getter/setter — is the v1
reference implementation. In production, the `@KompactModel` / `@KompactField`
annotations are consumed by the **KSP processor** (`kompact-ksp`),
which generates the `expect`/`actual` value-class stubs, the `create()`
factories, and the getter/setter bodies automatically from your
annotation metadata alone. See [ADR-0003](../adr/0003-kmp-consumer-enablement.md)
for the publication pipeline.

### Applying the processor

In your consumer module's `build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("com.google.devtools.ksp") version "2.3.12"
}

kotlin {
    jvm()
    iosArm64()
    androidNativeArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.3.0-SNAPSHOT")
            }
        }
    }
}

dependencies {
    // Use kspCommonMainMetadata so generated sources land in the
    // common source set shared by all KMP targets (not per-target).
    // The processor generates expect/actual stubs from your annotations.
    kspCommonMainMetadata("ch.trancee.kompact:kompact-ksp:0.3.0-SNAPSHOT")
}
```

### Selecting the generation mode (`kompact.generate`)

The processor emits up to one file per model: an `expect value class`
(commonMain), a `@JvmInline actual value class` (jvmMain/androidMain), and a
plain `actual value class` (iosMain). Which of these the processor writes in a
given KSP invocation is selected by the **`kompact.generate`** processor option.
Pass it as a module-level KSP argument:

```kotlin
ksp {
    arg("kompact.generate", "all")   // one of: common | jvm | ios | all
}
```

| `kompact.generate` value | Emits | Source-set fit |
| --- | --- | --- |
| `common` | `expect value class` only | `commonMain` (shared by all targets) |
| `jvm` | `@JvmInline actual value class` only | `jvmMain` / `androidMain` |
| `ios` | plain `actual value class` only | `iosMain` |
| _(omitted)_ / `all` | all three files above | non-KMP or single-source builds |

For a **Kotlin Multiplatform** module, route generated sources into the
matching source set with KSP's per-source-set dependency configurations
(`kspCommonMainMetadata`, `kspJvm`, `kspIosArm64`, …). These controls decide
*where* generated code lands — `common` files into `commonMain`, `jvm` files
into `jvmMain`/`androidMain`, `ios` files into `iosMain` — but they do **not**
set the `kompact.generate` value; that stays module-wide (the `ksp { arg(…) }`
block above applies to every target).

```kotlin
dependencies {
    add("kspCommonMainMetadata", "ch.trancee.kompact:kompact-ksp:<version>")
    add("kspJvm", "ch.trancee.kompact:kompact-ksp:<version>")
    add("kspIosArm64", "ch.trancee.kompact:kompact-ksp:<version>")
}
// Select which file set the processor emits for this module:
ksp {
    arg("kompact.generate", "all")   // default when omitted
}
```

If `kompact.generate` is omitted, the processor defaults to `all` (generates
every file) — convenient for non-KMP modules and single-source builds. An
**unrecognised** value (e.g. a typo like `"cmomn"`) **fails the build**: the
processor throws `IllegalArgumentException` naming the illegal value and the
accepted set `common | jvm | ios | all`, rather than silently mis-routing
expect/actual stubs into the wrong source set. See
[`ADR-0003`](../adr/0003-kmp-consumer-enablement.md) for the full Android +
publication wiring.

### What the processor generates

Given a model annotated with `@KompactModel` + `@KompactField` (same
annotations as the hand-written example), the processor emits three
files:

| Output | Source set | Contents |
| --- | --- | --- |
| `<Name>.kt` | `commonMain` | `expect value class` + `@KompactPreview` + `internal encodeXxx()` helper |
| `<Name>JvmActual.kt` | `jvmMain` | `@JvmInline actual value class` with init guard, `@Actual` companion `create()` |
| `<Name>IosActual.kt` | `iosMain` | plain `actual value class` (no `@JvmInline`) |

The generated getters use the **raw** `KompactRuntime.readBits` /
`readBitsBoolean` path (not the checked `readScalar`/`readBool`),
because the processor proves bounds at compile time — see the
[codegen output reference](../architecture.md#codegen-output-reference)
for the full shape. The default view's fields are `val` (read-only); write-
through `var` setters live on the opt-in `Mutable<Model>` sibling emitted
when `@KompactModel(mutable = true)` — `writeBits` /
`writeBitsBoolean`, just like the hand-written example.

**Supported types.** The processor handles `Boolean`, `Int`, `Long`,
`Float`, `Double`. Variable-length types (`String`, `ByteArray`, nested
composites, repeated fields) are declared via `@KompactField` metadata
(`lengthPrefixWidth`, `isNested`, `repeatCountWidth`) but are not yet
fully generated — use the hand-written [`KompactWriter`](long-form-payloads.md)
path for those until the v2 codegen lands.

### Writing your annotation-based model

After applying KSP, you write **only the annotations** — the processor
generates the boilerplate:

```kotlin
@file:OptIn(KompactPreview::class)
package your.package

@KompactModel
public expect value class SensorFrame(public val raw: ByteArray) {
    public companion object {
        public fun create(
            status: Int,
            battery: Int,
            temperature: Int,
            timestamp: Int,
        ): SensorFrame
    }

    @KompactField(bitOffset = 0,  bitWidth = 4)  public val status: Int
    @KompactField(bitOffset = 4,  bitWidth = 4)  public val battery: Int
    @KompactField(bitOffset = 8,  bitWidth = 12, signed = true) public val temperature: Int
    @KompactField(bitOffset = 20, bitWidth = 12) public val timestamp: Int
}
```

The `expect` declaration is all you write — the `create()` bodies,
the `@JvmInline actual` (JVM), the plain `actual` (iOS), and every
getter body are generated. To also emit the opt-in write-through
`Mutable<Model>` sibling, set `mutable = true` on the schema's
`@KompactModel` (the processor then generates the matching
`MutableSensorFrame` with `var` setters). The processor also validates the layout
at compile time (overlapping fields, invalid widths, bad prefix
widths) and fails the build on violations.

## What's next

- Variable-length fields (string, blob, nested, repeated):
  [`long-form-payloads.md`](long-form-payloads.md).
- How to recover from a bad wire buffer without throwing:
  [`handle-decode-errors.md`](handle-decode-errors.md).
- Send / receive this frame over BLE:
  [`integrate-ble.md`](integrate-ble.md).
