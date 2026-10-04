# How to define a sequential framed schema

Use a framed schema when a message contains strings, byte arrays, nested
messages, or repeated values. Fields are read in order, so a variable-length
field does not require guessed offsets for the fields that follow it.

The framed runtime and code-generation plugin are available from Maven Central
at `0.6.1`. Follow [Consume Kompact](consume-from-another-project.md) to
configure the published dependencies.

## 1. Declare the schema

Put the schema in `commonMain`. Each field has a unique `order` starting at
zero. Scalar fields declare `bitWidth`; variable-length fields declare a
length-prefix width in bits. Repeated fields declare a count-prefix width and,
for variable-length elements, a length-prefix width as well.

```kotlin
@file:OptIn(KompactPreview::class)

package example

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel
import ch.trancee.kompact.annotations.KompactPreview

@KompactModel(framed = true)
public class PayloadSchema {
    @KompactField(order = 0, bitWidth = 16)
    public val value: Int = 0
}

@KompactModel(framed = true)
public class PacketSchema {
    @KompactField(order = 0, bitWidth = 8)
    public val id: Int = 0

    @KompactField(order = 1, lengthPrefixWidth = 8)
    public val title: String = ""

    @KompactField(order = 2, lengthPrefixWidth = 8)
    public val payload: ByteArray = byteArrayOf()

    @KompactField(order = 3, lengthPrefixWidth = 8, isNested = true)
    public val child: PayloadSchema = PayloadSchema()

    @KompactField(order = 4, bitWidth = 16, repeatCountWidth = 8)
    public val samples: List<Int> = emptyList()

    @KompactField(order = 5, lengthPrefixWidth = 8, repeatCountWidth = 8)
    public val titles: List<String> = emptyList()

    @KompactField(order = 6, lengthPrefixWidth = 8, repeatCountWidth = 8)
    public val blobs: List<ByteArray> = emptyList()

    @KompactField(order = 7, lengthPrefixWidth = 8, repeatCountWidth = 8, isNested = true)
    public val children: List<PayloadSchema> = emptyList()
}
```

`lengthPrefixWidth` and `repeatCountWidth` are bit widths; valid values are
`8`, `16`, and `32`. A repeated scalar uses `bitWidth` and
`repeatCountWidth`. A repeated string, blob, or nested model also uses
`lengthPrefixWidth`; nested models set `isNested = true`.

## 2. Enable common-source generation

Apply the Kompact Gradle plugin after Kotlin Multiplatform and add the runtime
to `commonMain`. The plugin processes common schemas once and registers their
generated common and platform sources. Do not also run the standard KSP plugin
for the same schemas.

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("ch.trancee.kompact.codegen") version "0.6.1"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    androidNativeArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.6.1")
            }
        }
    }
}
```

To try unreleased changes from the current `0.7.0-SNAPSHOT` checkout, publish
the runtime, KSP processor, and Gradle plugin to Maven Local. The
[consumer setup guide](consume-from-another-project.md) has the exact commands
and repository blocks.

The supported plugin targets are JVM, Android JVM, iOS Arm64, iOS Simulator
Arm64, and Android Native Arm64. Add only the targets your application uses.

## 3. Use the generated view

The schema above generates `PacketSchemaView` and `PayloadSchemaView`. The
view exposes a factory, a typed-result `decode`, and properties in schema
order. Repeated properties are lazy `KompactRepeatedView` instances.

```kotlin
package example

val child = PayloadSchemaView.create(value = 42)
val originalPayload = byteArrayOf(4, 5)
val packet = PacketSchemaView.create(
    id = 7,
    title = "sensor",
    payload = originalPayload,
    child = child,
    samples = listOf(10, 20),
    titles = listOf("one", "two"),
    blobs = listOf(byteArrayOf(1), byteArrayOf(2, 3)),
    children = listOf(child),
)

val decoded = PacketSchemaView.decode(packet.raw).getOrThrow()
check(decoded.id == 7)
check(decoded.title == "sensor")
check(decoded.payload.contentEquals(originalPayload))
check(decoded.child.value == 42)
check(decoded.samples.toList() == listOf(10, 20))
check(decoded.titles.toList() == listOf("one", "two"))
check(decoded.blobs[1].contentEquals(byteArrayOf(2, 3)))
check(decoded.children[0].value == 42)
```

Use `decode` for untrusted bytes and inspect its `error` before choosing a
recovery action. `getOrThrow()` is convenient when the caller intentionally
uses exceptions.

## Ownership and validation

- `payload` returns a copy. `payloadSlice` exposes a bounded view over the
  original array; call `toByteArray()` when the data needs independent
  ownership.
- Nested views and repeated views borrow the frame's array. Keep that array
  unchanged while using a decoded view. Changing a variable-length prefix
  invalidates the repeated view's validated index.
- Framed fields are required, `order` values must be contiguous from zero, and
  decoding rejects trailing non-zero data. Appending an optional field is not
  a compatible schema change.
- Variable-length fields must begin at a byte-aligned position.
- `defaultValue`, parameterized nested schemas, and mutable framed fields are
  not supported by code generation.

For the wire-format tradeoffs and schema-evolution limits, see
[Architecture](../architecture.md) and
[ADR-0008](../adr/0008-framed-generated-views.md).

## Verify the integration

The repository's KMP TestKit consumer compiles generated common declarations
for supported targets. From the repository root, run:

```bash
./gradlew :kompact-gradle-plugin:test :kompact-ksp-integration:test
```

For a fixed-offset model, use the
[fixed-layout guide](define-message.md).
