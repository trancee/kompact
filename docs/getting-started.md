# Getting started

This tutorial takes you from an empty buffer to a packed-and-decoded
Kompact frame in about ten lines. Everything you see here is
verified by [`GettingStartedTest`](../kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/GettingStartedTest.kt)
in `commonTest` — if the wire bytes or the decoded values in this doc
ever drift from reality, that test fails first and the doc gets
corrected with it.

## What you'll build

A 2-byte telemetry message with this layout (LSB-first packing):

| Bits   | Width | Field             | Range / meaning              |
| ------ | ----- | ----------------- | ---------------------------- |
| 0..3   | 4     | `batteryStatus`   | 0–15 (enum ordinal)           |
| 4..13  | 10    | `speed`           | 0–1023                       |
| 14     | 1     | `isMalfunctioning`| `Boolean`                    |
| 15     | 1     | _reserved_        | left zero                    |

Same shape as the bundled [`VehicleTelemetry`](../kompact/src/commonMain/kotlin/ch/trancee/kompact/generated/VehicleTelemetry.kt)
example, but read with the checked, typed API (`readScalar` /
`readBool`) rather than the raw zero-alloc view getters.

## Prerequisites

- A working Kotlin Multiplatform toolchain (the project is built with the
  Kotlin 2.4.x Gradle plugin and JDK 21).
- The `:kompact` module on your classpath. Until the first Maven Central
  release, either build from source:
  ```bash
  ./gradlew :kompact:publishToMavenLocal
  ```
  and add the local snapshot:
  ```kotlin
  // build.gradle.kts (consumer)
  implementation("ch.trancee.kompact:kompact:0.1.0-SNAPSHOT")
  ```
  …or include the project directly.

## Step 1 — write a frame

`KompactWriter` is a forward-only, growable bit buffer. Append fields
in the order they appear on the wire; `build()` returns the exact-length
`ByteArray` snapshot.

```kotlin
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

val w = KompactWriter()
w.writeScalar(ScalarType.of(4,  signed = false), 5L)   // batteryStatus  = 5
w.writeScalar(ScalarType.of(10, signed = false), 10L) // speed          = 10
w.writeBool(true)                                      // isMalfunctioning = true
// bit 15 (reserved) is left zero by default.
val bytes: ByteArray = w.build()
```

**Expected result.** `bytes` is exactly 2 bytes long. For
`battery=5, speed=10, malfunction=true` the LSB-first wire bytes are
`[0xA5, 0x40]` (pinned by the tutorial test — if a future change
shifts the wire format, the test will break and this doc gets
updated to match).

## Step 2 — read it back, checked
Every `KompactRuntime` read accessor that ends in a typed result
(`readBool`, `readScalar`, `readScalarAsLong`, `readFloat`, `readDouble`)
returns a **zero-allocation result value class** — not a primitive, not
a throw. The success hot path never throws.

```kotlin
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

val battery: Int =
    KompactRuntime.readScalar(bytes, 0,  ScalarType.of(4,  signed = false)).getOrThrow()
val speed: Int =
    KompactRuntime.readScalar(bytes, 4, ScalarType.of(10, signed = false)).getOrThrow()
val flag: Boolean =
    KompactRuntime.readBool(bytes, 14).getOrThrow()
```

**Expected result.** `battery == 5`, `speed == 10`, `flag == true`.

`getOrThrow()` is the explicit recovery call: it returns the decoded
primitive on success and throws `KompactDecodeException` (carrying
a `KompactDecodeError`) only on failure. The success path is allocation-
free on both the JVM (`@JvmInline value class`) and iOS (plain
`value class`).

## Step 3 — handle failure without throwing

If the buffer is truncated, the same accessor returns a failure result
instead of throwing:

```kotlin
import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

val truncated = byteArrayOf(0xA5.toByte())   // only 1 byte, layout needs 2
val speed = KompactRuntime.readScalar(truncated, 4, ScalarType.of(10, signed = false))

if (speed.isFailure) {
    when (val err = speed.error) {
        KompactDecodeError.BoundsError       -> println("buffer too short")
        KompactDecodeError.BadLengthPrefix   -> println("length prefix overruns buffer")
        KompactDecodeError.TruncatedNested   -> println("nested region truncated")
        is KompactDecodeError.UnknownEnumCode -> println("unknown enum code: ${err.rawCode}")
    }
}
```

This pattern (no exception on the hot path) is what the API is shaped
for — see [`docs/architecture.md`](architecture.md) for the why.
## Step 4 — add a length-prefixed string, a blob, a nested record, a repeated field

Fixed-width scalars are the first half of the wire. The other half
is length-delimited framing — strings, blobs, nested composites, and
repeated fields. All four share one contract: a fixed-width
little-endian byte-count prefix, then the payload. The legal prefix
widths are `8`, `16`, `32` bits
([`KompactFraming.VALID_PREFIX_WIDTHS`](api-reference.md#kompactframing)).

```kotlin
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

val w = KompactWriter()
w.writeScalar(ScalarType.of(8, signed = false), 1L)            // 1 byte: version = 1
w.writeString(countWidth = 8, value = "hello")                 // 1 + 5 = 6 bytes
w.writeBlob(countWidth = 16, bytes = byteArrayOf(0x89, 0x50, 0x4E, 0x47))  // 2 + 4 = 6 bytes
w.writeRepeated(count = 3, countWidth = 8) {                   // 1 byte count + 3 x 2-byte elements
    writeScalar(ScalarType.of(16, signed = true), 100L)        // each block re-runs against the same writer
}
w.writeNested(lengthPrefixWidth = 16) {                        // 2 byte length prefix + payload
    writeScalar(ScalarType.of(8, signed = false), 1L)
    writeScalar(ScalarType.of(32, signed = true), 1700000000L)
}
val mixed: ByteArray = w.build()
println(mixed.size)                                            // total bytes
```

**Expected result.** A single contiguous `ByteArray` carrying all
four payloads back-to-back in the order they were written. The
reader walks the same shape forward — read the prefix, consume
that many bytes, repeat. See the
[how-to guide for long-form payloads](how-to/long-form-payloads.md)
for the full reader-side walkthrough and a worked `readNested`
example.


## Where to go next

- **API surface** — every function, parameter, and result class:
  [`docs/api-reference.md`](api-reference.md).
- **Design rationale** — why LSB-first, why zero-alloc, why value
  classes over a packed `Long`: [`docs/architecture.md`](architecture.md).
- **How-to guides** — task-oriented recipes for common use cases
  (define your own message, handle decode errors, send over BLE,
  consume from another project): [`docs/how-to/README.md`](how-to/README.md).
- **Long-form payload** — strings, blobs, nested composites, and
  repeated fields in depth:
  [`docs/how-to/long-form-payloads.md`](how-to/long-form-payloads.md).
- **Run the tests yourself** — `./gradlew :kompact:jvmTest` (the tutorial
  test is part of the suite).
