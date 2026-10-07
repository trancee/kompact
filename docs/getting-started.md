# Getting started

Build a two-byte frame, inspect the bytes, and read its fields back. This
tutorial uses the runtime directly so you can see the wire layout without
setting up generated schemas.

## What you will build

| Bits | Width | Field | Meaning |
| --- | ---: | --- | --- |
| 0-3 | 4 | `batteryStatus` | Unsigned value from 0 to 15 |
| 4-13 | 10 | `speed` | Unsigned value from 0 to 1023 |
| 14 | 1 | `isMalfunctioning` | Boolean flag |
| 15 | 1 | Reserved | Left at zero |

Kompact numbers bits from the least-significant bit of the first byte.
For these values, the frame is `[0xA5, 0x40]`.

## Before you start

The runtime is available from Maven Central as
`ch.trancee.kompact:kompact:0.8.0`. Add it to your project and follow the
[consumer setup guide](how-to/consume-from-another-project.md) if you need
Gradle configuration. No KSP processor or code-generation plugin is needed
for this tutorial.

## 1. Write the frame

Append fields in wire order. `build()` returns the bytes written so far.

```kotlin
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

val writer = KompactWriter()
writer.writeScalar(ScalarType.of(4, signed = false), 5L)
writer.writeScalar(ScalarType.of(10, signed = false), 10L)
writer.writeBool(true)

val bytes = writer.build()
check(bytes.size == 2)
check(bytes.contentEquals(byteArrayOf(0xA5.toByte(), 0x40.toByte())))
```

The first four bits contain `5`; the next ten contain `10`; bit 14 is set.
The unused final bit remains zero. The executable
[`GettingStartedTest`](../kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/GettingStartedTest.kt)
pins these bytes so a wire-format change cannot silently make the example
stale.

## 2. Read the fields

Checked read methods return typed results. `getOrThrow()` gives you the value
when the input is valid and throws a `KompactDecodeException` otherwise.

```kotlin
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

val batteryStatus =
    KompactRuntime.readScalar(bytes, 0, ScalarType.of(4, signed = false)).getOrThrow()
val speed =
    KompactRuntime.readScalar(bytes, 4, ScalarType.of(10, signed = false)).getOrThrow()
val isMalfunctioning = KompactRuntime.readBool(bytes, 14).getOrThrow()

check(batteryStatus == 5)
check(speed == 10)
check(isMalfunctioning)
```

The buffer and offsets above are valid, so each call returns its decoded value.
When reading data from outside your process, preserve and handle the typed
failure instead of assuming the buffer is complete. See
[How to handle malformed input](how-to/handle-decode-errors.md).

## Where to go next

- [Add Kompact to a project](how-to/consume-from-another-project.md)
- [Define a fixed-layout model](how-to/define-message.md)
- [Define a sequential framed schema](how-to/define-framed-schema.md)
- [Read strings, blobs, nested data, and repeats](how-to/long-form-payloads.md)
- [Look up a public API](api-reference.md)
- [Understand the design trade-offs](architecture.md)
