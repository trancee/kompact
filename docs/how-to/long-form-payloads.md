# How to encode and read long-form payloads

Use the writer and bounded frame reader when a message contains strings, byte
arrays, nested records, or repeated values. This guide uses the low-level
runtime API; for generated properties, see
[How to define a framed schema](define-framed-schema.md).

The `KompactFrame` reader used below is included in the published `0.8.0`
release. Follow [Consume Kompact](consume-from-another-project.md) to add the
runtime dependency.

## Encode strings, blobs, and repeated values

Write fields in wire order. Strings and blobs have a fixed-width little-endian
byte-count prefix. Repeats have a fixed-width count prefix followed by their
elements.

```kotlin
import ch.trancee.kompact.runtime.KompactFrame
import ch.trancee.kompact.runtime.KompactWriter

val payload = byteArrayOf(0x50, 0x4E, 0x47)
val writer = KompactWriter()
writer.writeString(countWidth = 8, value = "image")
writer.writeBlob(countWidth = 16, bytes = payload)
writer.writeRepeated(count = 2, countWidth = 8) {
    writeBits(bitWidth = 16, value = 42)
}
val bytes = writer.build()

val decoded = KompactFrame.decode(bytes) { frame ->
    val name = frame.readString(prefixWidth = 8)
    val image = frame.readBlob(prefixWidth = 16).toByteArray()
    val readings = frame.readRepeated(
        countWidth = 8,
        elementWidth = 16,
        elementPrefixWidth = 0,
    ) { element ->
        element.readBits(width = 16).toInt()
    }
    Triple(name, image, readings)
}.getOrThrow()

check(decoded.first == "image")
check(decoded.second.contentEquals(payload))
check(decoded.third.size == 2)
check(decoded.third[0] == 42)
```

The `readRepeated` call validates the count and element boundaries, then
decodes each element when you access it. Use a manual count prefix and loop
instead of `writeRepeated` when each element has a different value; its block
does not receive an element index.

## Encode and read a nested record

`writeNested` builds the child first, then writes its byte length and bytes.
`readNested` returns a borrowed slice bounded to that child payload. Decode the
slice separately so the nested reader cannot cross into the following field.

```kotlin
import ch.trancee.kompact.runtime.KompactFrame
import ch.trancee.kompact.runtime.KompactWriter

val writer = KompactWriter()
writer.writeNested(lengthPrefixWidth = 16) {
    writeBits(bitWidth = 8, value = 1) // child version
    writeBits(bitWidth = 16, value = 42)
}
val bytes = writer.build()

val childValue = KompactFrame.decode(bytes) { frame ->
    val child = frame.readNested(prefixWidth = 16)
    KompactFrame.decode(child.raw, child.start, child.end) { nested ->
        val version = nested.readBits(width = 8).toInt()
        val value = nested.readBits(width = 16).toInt()
        check(version == 1)
        value
    }.getOrThrow()
}.getOrThrow()

check(childValue == 42)
```

The block overload of `KompactFrame.decode` returns a typed failure for
malformed prefixes, truncated data, invalid UTF-8, or out-of-bounds reads.
Direct `read*` calls throw `KompactDecodeException` on malformed input; see
[Handle decode errors](handle-decode-errors.md) to choose a recovery path.

## Choose prefix widths

`countWidth` and `lengthPrefixWidth` are widths in bits. Kompact accepts `8`,
`16`, or `32`:

| Prefix width | Maximum count or byte length |
| ---: | ---: |
| 8 | 255 |
| 16 | 65,535 |
| 32 | `Int.MAX_VALUE` |

Choose a width that can represent the largest valid payload. The writer rejects
an invalid width or a value that does not fit before it appends that field.
Writer and reader must use the same width.

When reading variable-length values with `KompactFrame`, start each one at a
byte boundary. Keep the backing `ByteArray` unchanged while using a borrowed
slice or lazy repeated view.

## Read a borrowed UTF-8 range with caller-owned state

The current checkout also provides `KompactCursor` for checked operations
without returning wrapper results. `readUtf8Range` validates the complete
length-prefixed payload and binds a caller-created `KompactByteRange` to the
original buffer. The range borrows those bytes; copy them only when you need
independent ownership.

```kotlin
import ch.trancee.kompact.runtime.KompactByteRange
import ch.trancee.kompact.runtime.KompactCursor

val encoded = byteArrayOf(2, 0xC3.toByte(), 0xA9.toByte())
val cursor = KompactCursor(encoded)
val textBytes = KompactByteRange(ByteArray(0))

check(cursor.readUtf8Range(prefixWidth = 8, range = textBytes) == KompactCursor.STATUS_OK)
check(textBytes.size == 2)

val ownedCopy = ByteArray(textBytes.size)
check(textBytes.copyTo(ownedCopy))
check(ownedCopy.contentEquals(byteArrayOf(0xC3.toByte(), 0xA9.toByte())))
```

Keep `encoded` unchanged while `textBytes` is in use. `KompactByteRange.copyTo`
copies into storage you supply; it does not allocate a destination. For
generated reusable holders, nested payloads, and repeated-field workspaces,
see the [caller-owned codec API reference](../api-reference.md). The
caller-owned cursor API is available in this checkout and is not included in
the published `0.8.0` artifact. Bounded Native probes recorded no detectable
allocation delta for selected scalar and cursor operations on Android Arm64
and iOS Arm64 debug and release test binaries. These GC sweep-statistics
results are testing/debugging data; they do not cover every API or call shape,
including the payload conversions and repeat operations described here. Do
not infer a general allocation guarantee from this example; see the
[allocation research note](../research/allocation-boxing-measurement.md).

Probe-taking generated framed-holder operations use distinct cursors and
preflight the complete bounded input or output before committing. Scalar-only
framed schemas use the same checked call shape and validate scalar ranges on
the probe cursor. Variable-width repeats are traversed once for preflight and
again while building their reusable workspace during decode; this preserves
failure atomicity but has not been performance-measured.

## Next steps

- Define the same fields as generated view properties:
  [Framed schema guide](define-framed-schema.md).
- Choose a malformed-input recovery strategy:
  [Decode errors](handle-decode-errors.md).
- Pass the resulting `ByteArray` to a transport:
  [BLE integration](integrate-ble.md).
