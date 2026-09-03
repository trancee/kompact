# How to use Kompact

Build a schema, write it to a `ByteArray`, and read it back. Three steps.

## What you need

A Kotlin/JVM project (or a KMP project with `jvm` + `iosArm64` + `iosSimulatorArm64` targets) that depends on `:kompact`. The runtime artifact is published under `ch.trancee.kompact:kompact`.

## 1. Define a schema

Mark a class with `@KompactModel` and annotate each field with `@KompactField`. The fields are LSB-first bit-packed in declaration order. The class doesn't need to be a `value class` — the processor turns it into one in the generated view:

```kotlin
import ch.trancee.kompact.annotation.KompactField
import ch.trancee.kompact.annotation.KompactModel

@KompactModel
class VehicleTelemetry {
    @KompactField(bitOffset = 0, bitWidth = 4)
    val batteryStatus: Int = 0

    @KompactField(bitOffset = 4, bitWidth = 10)
    val speed: Int = 0

    @KompactField(bitOffset = 14, bitWidth = 1)
    val isMalfunctioning: Boolean = false
}
```

This packs 15 bits into 2 bytes: 4 bits for `batteryStatus`, 10 bits for `speed`, 1 bit for
`isMalfunctioning`, 1 bit unused. The bit offsets are absolute positions inside the
buffer; the processor validates that they don't overlap and that the total width is
positive.

## 2. Write bytes

Use `KompactWriter` to build the buffer. It owns a growable byte array and writes fields
sequentially:

```kotlin
import ch.trancee.kompact.writer.KompactWriter

val w = KompactWriter()
w.writeUInt4(0xC)        // batteryStatus = 12
w.writeUInt10(677)        // speed = 677
w.writeBool(true)         // isMalfunctioning = true
val bytes: ByteArray = w.build()
```

`build()` snapshots the result. The writer is **not** zero-allocation — it grows the buffer
as needed — but the resulting `ByteArray` is a plain JVM array you can hand to any
transport.

## 3. Read bytes

The KSP processor generates a `VehicleTelemetryView` value class (a per-schema
companion) with one accessor per `@KompactField`. Wrap your `ByteArray` and read:

```kotlin
import ch.trancee.kompact.example.VehicleTelemetryView

val view = VehicleTelemetryView(bytes)
val batt = view.batteryStatus     // 12
val speed = view.speed             // 677
val malfunctioning = view.isMalfunctioning  // true
```

The view's accessors are zero-allocation bit-shifts over the caller's `ByteArray` — no
defensive copy, no boxing, no `Byte`/`Int` conversions on the hot path. The full round-trip
has zero heap allocations on the read side.

## 4. (Optional) Prefix a version

If you need forward-compat support, write a 4-byte version prefix at the start of every
stream and read it first:

```kotlin
import ch.trancee.kompact.runtime.KompactVersionedStream

val out = ByteArray(64)
KompactVersionedStream.writeVersion(out, version = 1u)
val schemaBytes = KompactWriter().apply { writeUInt4(0xC); writeUInt10(677) }.build()
System.arraycopy(schemaBytes, 0, out, 4, schemaBytes.size)

when (val v = KompactVersionedStream.readVersion(out)) {
    is IntResult.Success -> { /* parse with v.value */ }
    is IntResult.Failure -> when (v.errorCode) {
        KompactError.UnsupportedSchemaVersion -> error("unknown version")
        KompactError.BoundsError               -> error("truncated")
        else                                  -> error("read failed")
    }
}
```

Older readers see an unknown version as a typed `UnsupportedSchemaVersion` failure, not a
silent misread.

## Things that go wrong (and how to recover)

| Symptom | Cause | Recovery |
|---|---|---|
| `KSP error: overlapping fields in @KompactModel Foo` | Two `@KompactField` annotations point to overlapping bit ranges | Adjust the `bitOffset` values so ranges don't overlap. |
| `KSP error: invalid length-prefix width 12` | `lengthPrefixBits` is not in `{8, 16, 32}` | Use 8, 16, or 32. |
| `IllegalArgumentException: read at [32, 64) exceeds buffer (16 bits)` | A read accessor is called on a buffer that's too short for the field's offset + width | Make sure the writer actually wrote this field before the read, or supply a larger buffer. |
| `IntResult.Failure` with `UnsupportedSchemaVersion` | The version prefix is outside the supported set | `KompactVersionedStream.setSupportedVersions(...)` on the reader, or migrate the writer. |
| iOS test runner says "no main entry found" | KSP `#567` (open upstream) prevents the processor from emitting per-target actuals into the right source set | v1 hand-writes the per-target `actual` for each KMP target. See `:kompact-example` for the pattern. |

## See also

- [Runtime reference](reference/runtime.md) — every public function in `KompactRuntime`, `KompactRead`, `KompactWriter`, `KompactVersionedStream`, `AllocationCounter`.
- [Result types reference](reference/result-types.md) — the packed value classes and `KompactError` codes.
- [Annotations and processor reference](reference/annotations-and-processor.md) — `@KompactModel`, `@KompactField`, `KompactProcessor`, `LayoutModel`.
- [Design rationale](../.scratch/kompact-spec/map.md) — the locked wayfinder map that drove every decision.
