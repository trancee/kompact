# Getting started

In this tutorial, we build a two-byte telemetry frame, check its bytes, read
its fields back, and watch a truncated frame fail. We call the runtime
directly, so you see the wire layout without setting up generated schemas.

## What we will build

The frame holds three fields in 16 bits:

| Bits | Width | Field | Meaning |
| --- | ---: | --- | --- |
| 0-3 | 4 | `batteryStatus` | Unsigned value from 0 to 15 |
| 4-13 | 10 | `speed` | Unsigned value from 0 to 1023 |
| 14 | 1 | `isMalfunctioning` | Boolean flag |
| 15 | 1 | Reserved | Left at zero |

Kompact numbers bits from the least-significant bit of the first byte. With
`batteryStatus = 5`, `speed = 10`, and `isMalfunctioning = true`, the frame is
`[0xA5, 0x40]`.

## Before you start

Add the runtime to your project. The latest published release on Maven
Central is `ch.trancee.kompact:kompact:0.10.0`. If you need the Gradle setup,
follow the [consumer setup guide](how-to/consume-from-another-project.md).
This tutorial does not need the KSP processor or the code-generation plugin.

## Step 1: Write the frame

First, create a `KompactWriter` and append the fields in wire order. Then call
`build()` to get the bytes written so far:

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

Both checks pass. The first four bits hold `5`, the next ten bits hold `10`,
and bit 14 is set. We did not write bit 15, so it stays zero.

The test
[`GettingStartedTest`](../kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/GettingStartedTest.kt)
asserts these exact bytes. If a change to the wire format moves a bit, that
test fails before this page goes out of date.

## Step 2: Read the fields

Now read each field from its bit offset. Each checked read returns a typed
result. `getOrThrow()` returns the value when the read succeeds:

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

All three checks pass. `readScalar` returns an `IntResult`, and `readBool`
returns a `BooleanResult`.

## Step 3: Read a truncated frame

Finally, read `speed` from a frame that lost its second byte. The read needs
bits 4 to 13, but the buffer holds only 8 bits:

```kotlin
import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.ScalarType

val truncated = byteArrayOf(0xA5.toByte())
val result = KompactRuntime.readScalar(truncated, 4, ScalarType.of(10, signed = false))

check(result.isFailure)
check(result.error == KompactDecodeError.BoundsError)
```

Both checks pass. The read does not throw. It returns a failure that carries
`KompactDecodeError.BoundsError`. If you call `result.getOrThrow()`, it throws
a `KompactDecodeException` that holds the same error.

Data from outside your process can be truncated like this. To decide what your
code does with a failed read, see
[How to handle malformed input](how-to/handle-decode-errors.md).

## Where to go next

You wrote a frame, read it back, and saw how a short buffer fails. Continue
with one of these pages:

- [Add Kompact to a project](how-to/consume-from-another-project.md)
- [Define a fixed-layout model](how-to/define-message.md)
- [Define a sequential framed schema](how-to/define-framed-schema.md)
- [Read strings, blobs, nested data, and repeats](how-to/long-form-payloads.md)
- [Look up a public API](api-reference.md)
- [Understand the design trade-offs](architecture.md)
