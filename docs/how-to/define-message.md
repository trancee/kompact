# How to define a fixed-layout model

Use a fixed-layout model when each field has a known bit offset that does not
depend on earlier field values. If the message contains strings, blobs, nested
messages, or repeated values, use the
[sequential framed schema guide](define-framed-schema.md) instead.

The `commonMain` generation setup in this guide uses the published `0.6.1`
KMP plugin. Follow [Consume Kompact](consume-from-another-project.md) to
configure the plugin and runtime dependencies.

## 1. Choose the field positions

This example packs a 4-bit status and a signed 12-bit temperature into two
bytes. Fields are numbered from the least-significant bit of the first byte.

| Bit range | Width | Field | Type |
| --- | ---: | --- | --- |
| 0–3 | 4 | `status` | unsigned `Int` |
| 4–15 | 12 | `temperature` | signed `Int` |

Offsets may leave gaps, but fields must not overlap. Keep the layout beside
the schema so the wire positions are easy to review.

## 2. Add the common schema

In a Kotlin Multiplatform module, declare the schema in `commonMain`. The
Kompact Gradle plugin generates the platform actuals and encoder for this
`expect value class`; apply it after the Kotlin Multiplatform plugin. See
[Consume Kompact](consume-from-another-project.md) for repository and dependency
setup.

```kotlin
@file:OptIn(KompactPreview::class)

package example

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview

@KompactModel
public expect value class SensorFrame(public val raw: ByteArray) {
    public companion object {
        public fun create(status: Int, temperature: Int): SensorFrame
    }

    public fun copy(
        status: Int = this.status,
        temperature: Int = this.temperature,
    ): SensorFrame

    @KompactField(bitOffset = 0, bitWidth = 4)
    public val status: Int

    @KompactField(bitOffset = 4, bitWidth = 12, signed = true)
    public val temperature: Int
}
```

Use `bitWidth` for each field and set `signed = true` for two's-complement
integers. `@KompactPreview` is required while the annotation API is preview.

## 3. Create, read, and update a frame

The generated factory writes the fields in their declared positions. The
generated getters read those same positions, and `copy` returns a new frame
with the requested fields changed.

```kotlin
val frame = SensorFrame.create(status = 5, temperature = -12)
check(frame.status == 5)
check(frame.temperature == -12)

val updated = frame.copy(status = 2)
check(updated.status == 2)
check(updated.temperature == -12)
```

Add a `commonTest` round-trip test for each wire schema you publish. Test
representative boundary values as well as the values used by your application.

## Mutability

Views are immutable by default. If a workflow needs in-place writes, mark the
schema `@KompactModel(mutable = true)` to generate a separate `MutableSensorFrame`
with write-through `var` properties. Those setters store the low bits of the
assigned value; validate application input before writing if truncation would
be unsafe. Both views retain the supplied `ByteArray`, so code holding another
reference to that array can still change the frame.

## Next steps

- For variable-length fields, continue with
  [sequential framed schemas](define-framed-schema.md).
- To use a generated model in a BLE path, see
  [Integrate with BLE](integrate-ble.md).
- For the exact annotation and runtime APIs, see the
  [API reference](../api-reference.md).
