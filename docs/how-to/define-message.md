# How to define a fixed-layout model

Use a fixed-layout model when each field has a known bit offset that does not
depend on earlier field values. If the message contains strings, blobs, nested
messages, or repeated values, use the
[sequential framed schema guide](define-framed-schema.md) instead.

The regular-class schema in this guide requires the `0.10.0-SNAPSHOT`
code-generation plugin; the published `0.9.0` plugin used an earlier
hand-written declaration form. Follow
[Consume Kompact](consume-from-another-project.md) to configure the snapshot
plugin and runtime dependencies.

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

In a Kotlin Multiplatform module, declare the schema as a regular class in
`commonMain`. The Kompact Gradle plugin reads the annotated properties and
generates one common `SensorFrameView.kt` source containing the
`SensorFrameView` value class, its factory, `copy`, and encoder; apply it
after the Kotlin Multiplatform plugin. See
[Consume Kompact](consume-from-another-project.md) for repository and dependency
setup.

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

The schema class only describes the layout; its property initializers are
placeholders and are never used on the wire. Do not mark the schema `expect`:
the processor reports an error because it generates the `SensorFrameView`
declaration itself.

Use `bitWidth` for each field and set `signed = true` for two's-complement
integers. `@KompactPreview` is required while the annotation API is preview.

## 3. Create, read, and update a frame

The generated `SensorFrameView.create` factory writes the fields in their
declared positions. The generated getters read those same positions, and
`copy` returns a new frame with the requested fields changed; omitted
arguments default to the current values.

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

For bytes from a peer, file, or other untrusted source, use the checked
`decode` factory instead of the constructor. It returns
`KompactFrameResult.Failure(KompactDecodeError.BoundsError)` when the array is
shorter than the layout's byte length, and otherwise wraps the array without
copying:

```kotlin
when (val result = SensorFrameView.decode(receivedBytes)) {
    is KompactFrameResult.Success -> handle(result.value)
    is KompactFrameResult.Failure -> reject(result.error)
}
```

Add a `commonTest` round-trip test for each wire schema you publish. Test
representative boundary values as well as the values used by your application.

## Mutability

Views are immutable by default. If a workflow needs in-place writes, mark the
schema `@KompactModel(mutable = true)` to generate a separate
`MutableSensorFrameView` with write-through `var` properties. Those setters
store the low bits of the assigned value; validate application input before
writing if truncation would be unsafe. Both views retain the supplied `ByteArray`, so code holding another
reference to that array can still change the frame. The `SensorFrameView`
constructor requires at least the layout's byte length and throws
`IllegalArgumentException` otherwise; `SensorFrameView.decode` and
`MutableSensorFrameView.decode` report the same condition as a typed failure.

## Next steps

- For variable-length fields, continue with
  [sequential framed schemas](define-framed-schema.md).
- To use a generated model in a BLE path, see
  [Integrate with BLE](integrate-ble.md).
- For the exact annotation and runtime APIs, see the
  [API reference](../api-reference.md).
