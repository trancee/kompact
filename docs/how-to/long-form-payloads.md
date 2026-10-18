# How to write and read variable-length fields

Use `KompactWriter` and `KompactFrame` when you encode strings, byte arrays,
nested records, or repeats by hand. To get generated properties for the same
fields, follow [How to define a framed model](define-framed-schema.md)
instead.

The writer and frame calls in this guide work with the published `0.9.0`
runtime. The cursor section at the end needs `0.10.0-SNAPSHOT`. To add the
runtime, follow
[How to add Kompact to a project](consume-from-another-project.md).

## Write and read strings, blobs, and repeats

Write the fields in wire order, then read them back in the same order with the
same prefix widths:

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

`readRepeated` checks the count and every element boundary first. It decodes
an element when you access it.

The `writeRepeated` block does not receive an element index, so every
iteration writes the same thing. To write a different value for each element,
write the count with `writeBits` and loop over your elements yourself.

## Write and read a nested record

`writeNested` writes the child and then fills in its byte length.
`readNested` returns a slice that covers only the child. Decode that slice
with its own `KompactFrame.decode` call so that the child reader stops at the
child's end:

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

Inside the block, `read*` calls throw `KompactDecodeException` on malformed
input. The block overload of `KompactFrame.decode` turns that exception into a
`KompactFrameResult.Failure`. Other exceptions from your block, such as the
`check` failure above, pass through unchanged. To choose what to do with a
failure, follow [How to handle malformed input](handle-decode-errors.md).

## Choose prefix widths

`countWidth`, `lengthPrefixWidth`, and `prefixWidth` are widths in bits. Use
`8`, `16`, or `32`:

| Prefix width | Largest count or byte length |
| ---: | ---: |
| 8 | 255 |
| 16 | 65,535 |
| 32 | `Int.MAX_VALUE` |

Pick a width that holds your largest valid value. The writer rejects an
invalid width, or a value that does not fit, before it appends the field. Use
the same width on the writing and reading sides.

Start each string, blob, and nested record on a byte boundary. Keep the
backing `ByteArray` unchanged while you use a slice or a lazy repeat that
borrows it.

## Read a UTF-8 range into caller-owned storage

This section needs `0.10.0-SNAPSHOT`. In `0.9.0`, `readUtf8Range` returns an
`Int` code instead of a `KompactStatus`.

Use `KompactCursor` when you want to reuse your own objects instead of
receiving a new result. `readUtf8Range` checks the whole length-prefixed
string and points a `KompactByteRange` that you created at the bytes in the
original buffer:

```kotlin
import ch.trancee.kompact.runtime.KompactByteRange
import ch.trancee.kompact.runtime.KompactCursor
import ch.trancee.kompact.runtime.KompactStatus

val encoded = byteArrayOf(2, 0xC3.toByte(), 0xA9.toByte())
val cursor = KompactCursor(encoded)
val textBytes = KompactByteRange(ByteArray(0))

check(cursor.readUtf8Range(prefixWidth = 8, range = textBytes) == KompactStatus.OK)
check(textBytes.size == 2)

val ownedCopy = ByteArray(textBytes.size)
check(textBytes.copyTo(ownedCopy))
check(ownedCopy.contentEquals(byteArrayOf(0xC3.toByte(), 0xA9.toByte())))
```

`textBytes` borrows `encoded`, so keep `encoded` unchanged while you use it.
`copyTo` writes into the array that you pass and does not allocate one. For
generated holders, nested payloads, and repeat workspaces, see the
[API reference](../api-reference.md).

Do not assume that this code is allocation-free. Native probes found no
allocation for some scalar and cursor calls on Android Arm64 and iOS Arm64.
Those probes did not cover the conversions or repeat calls in this guide. See
the [allocation research note](../research/allocation-boxing-measurement.md).

## Next steps

- To get generated properties for these fields, follow
  [How to define a framed model](define-framed-schema.md).
- To handle malformed input, follow
  [How to handle malformed input](handle-decode-errors.md).
- To send the bytes over BLE, follow
  [How to pass Kompact frames over BLE](integrate-ble.md).
