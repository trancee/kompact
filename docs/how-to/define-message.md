# How to define a fixed-layout model

Use a fixed-layout model when each field has a known bit offset that does not
depend on earlier field values. If the message contains strings, blobs, nested
models, or repeats, follow
[How to define a framed model](define-framed-schema.md) instead.

This guide needs version `0.10.0` or later of the code-generation plugin and
runtime. To set them up, follow
[How to add Kompact to a project](consume-from-another-project.md#add-the-published-code-generation-plugin).

## 1. Choose the field positions

This example packs a 4-bit status and a signed 12-bit temperature into two
bytes. Bit 0 is the least significant bit of the first byte.

| Bit range | Width | Field | Type |
| --- | ---: | --- | --- |
| 0 to 3 | 4 | `status` | unsigned `Int` |
| 4 to 15 | 12 | `temperature` | signed `Int` |

You can leave gaps between fields. Fields must not overlap. Keep a table like
this one next to the schema so that reviewers can check the wire positions.

## 2. Declare the schema

In a Kotlin Multiplatform module, declare the schema as a regular class in
`commonMain`:

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

    @KompactField(bitOffset = 4, bitWidth = 12, signed = true)
    public val temperature: Int = 0
}
```

Give each field a `bitWidth`. Set `signed = true` for two's-complement
integers. The annotations are preview API, so opt in to `KompactPreview`.

The property initializers are placeholders. Kompact never writes them to the
wire. Do not mark the schema `expect`. The processor rejects an `expect`
schema because it generates the `SensorFrameView` declaration itself.

The plugin generates one common file, `SensorFrameView.kt`. It contains the
`SensorFrameView` value class with its `create`, `decode`, and `copy`
functions, and a `SensorFrameViewHolder` class for caller-owned cursor
decoding. The [API reference](../api-reference.md) describes holders.

## 3. Create, read, and update a frame

Generated views also carry `@KompactPreview`. Add
`@file:OptIn(KompactPreview::class)` to each file that uses them, or the
compiler warns.

`SensorFrameView.create` writes each field at its declared position, and the
generated properties read the same positions. `copy` returns a new frame with
the fields that you pass changed. The other fields keep their current values.

```kotlin
val frame = SensorFrameView.create(status = 5, temperature = -12)
check(frame.status == 5)
check(frame.temperature == -12)

val received = SensorFrameView(frame.raw.copyOf())
check(received.temperature == -12)

val updated = frame.copy(status = 2)
check(updated.status == 2)
check(updated.temperature == -12)
```

`create` and `copy` store only the low `bitWidth` bits of each value. A
`status` of `17` reads back as `1`. Check application values against the
field range before you pass them in.

## 4. Wrap bytes from another source

For bytes from a peer, a file, or any other untrusted source, call the checked
`decode` function instead of the constructor:

```kotlin
when (val result = SensorFrameView.decode(receivedBytes)) {
    is KompactFrameResult.Success -> handle(result.value)
    is KompactFrameResult.Failure -> reject(result.error)
}
```

If the array is shorter than the layout, `decode` returns
`KompactFrameResult.Failure(KompactDecodeError.BoundsError)`. Otherwise it
wraps the array without copying it. The `SensorFrameView` constructor throws
`IllegalArgumentException` for the same short array.

Add a `commonTest` round-trip test for each wire schema that you publish. Test
boundary values as well as the values that your application uses.

## Generate a mutable view

Views are immutable by default. To write fields in place, declare the schema
with `@KompactModel(mutable = true)`. The plugin then also generates
`MutableSensorFrameView`, whose `var` properties write through to the backing
array. Its setters store only the low bits of the assigned value, like
`create`. Check application input before you assign it if truncation would be
unsafe.

`MutableSensorFrameView` has the same `create` and `decode` functions and the
same constructor check as `SensorFrameView`.

Both views keep the `ByteArray` that you pass in. Code that holds another
reference to that array can still change the frame. For the ownership rules,
see [Design and trade-offs](../architecture.md).

## Next steps

- To add variable-length fields, follow
  [How to define a framed model](define-framed-schema.md).
- To send a generated model over BLE, follow
  [How to pass Kompact frames over BLE](integrate-ble.md).
- For the annotation and runtime signatures, see the
  [API reference](../api-reference.md).
