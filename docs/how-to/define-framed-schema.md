# How to define a framed model

Use a framed model when a message contains strings, byte arrays, nested
models, or repeats. Kompact reads framed fields in order, so a
variable-length field does not need fixed offsets for the fields after it. For
fields at fixed bit positions, follow
[How to define a fixed-layout model](define-message.md) instead.

This guide needs version `0.10.0` or later of the code-generation plugin and
runtime.

## 1. Declare the schema

Put the schema in `commonMain`. Give each field a unique `order`, counting
from zero. The fields in this example cover every supported shape:

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

    @KompactField(order = 3, lengthPrefixWidth = 8)
    public val child: PayloadSchema = PayloadSchema()

    @KompactField(order = 4, bitWidth = 16, repeatCountWidth = 8)
    public val samples: List<Int> = emptyList()

    @KompactField(order = 5, lengthPrefixWidth = 8, repeatCountWidth = 8)
    public val titles: List<String> = emptyList()

    @KompactField(order = 6, lengthPrefixWidth = 8, repeatCountWidth = 8)
    public val blobs: List<ByteArray> = emptyList()

    @KompactField(order = 7, lengthPrefixWidth = 8, repeatCountWidth = 8)
    public val children: List<PayloadSchema> = emptyList()
}
```

Set the annotation arguments by field type:

- For a scalar, set `bitWidth`.
- For a `String`, a `ByteArray`, or a nested model, set `lengthPrefixWidth`.
- For a repeat of scalars, declare a `List` and set `bitWidth` and
  `repeatCountWidth`.
- For a repeat of strings, blobs, or nested models, declare a `List` and set
  `lengthPrefixWidth` and `repeatCountWidth`.

`lengthPrefixWidth` and `repeatCountWidth` are widths in bits. Use `8`, `16`,
or `32`. Both default to `8`.

Kompact infers a nested region from the property type. A property whose type is
a `@KompactModel(framed = true)` class, or a `List` of one, is a
length-prefixed nested region. If the type is a fixed-layout `@KompactModel`,
the processor reports an error.

## 2. Apply the code-generation plugin

Apply `ch.trancee.kompact.codegen` after Kotlin Multiplatform, and add the
runtime to `commonMain`:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.21"
    id("ch.trancee.kompact.codegen") version "0.11.0"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    androidNativeArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.11.0")
            }
        }
    }
}
```

The snapshot is not on Maven Central. To publish it to Maven Local and set up
the repositories, follow
[How to add Kompact to a project](consume-from-another-project.md#enable-schema-generation).

The plugin processes the `commonMain` schemas once and adds one generated
common source per schema to `commonMain`. It does not restrict Kotlin targets.
Add only the targets that your application uses, and check that the runtime
publishes a variant for each one. Do not also apply the standard KSP plugin to
the same schemas.

## 3. Use the generated view

The schema above generates `PacketSchemaView.kt` and `PayloadSchemaView.kt`.
Each file holds a view class and a holder class for caller-owned cursor
decoding. A view has these members:

- `create` builds a new frame from field values.
- `decode` reads a frame and returns a `KompactFrameResult`.
- `copy` returns a new frame. Fields that you do not pass keep their current
  values.
- One property per field. A repeat property is a lazy `KompactRepeatedView`.

Generated views carry `@KompactPreview`. Add
`@file:OptIn(KompactPreview::class)` to each file that uses them, or the
compiler warns.

```kotlin
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

For untrusted bytes, call `decode` and check its `error` before you choose
what to do. Call `getOrThrow()` only where you want an exception. See
[How to handle malformed input](handle-decode-errors.md).

`create` and `copy` store only the low `bitWidth` bits of each scalar. Check
application values against the field range before you pass them in.

## Follow the ownership and schema rules

Keep these ownership rules in mind when you use a decoded view:

- `payload` returns a copy. `payloadSlice` returns a `KompactByteSlice` that
  borrows the original array. Call `toByteArray()` on the slice when you need
  an independent copy.
- Nested views and repeat views borrow the frame's array. Keep that array
  unchanged while you use a decoded view. If a length prefix inside a repeat
  changes, the repeat view's validated index no longer matches the bytes.
- Views do not compare content. A fixed-layout view compares its backing
  `ByteArray` by identity, and a framed view uses instance identity. Compare
  decoded fields when you need content equality.

The processor enforces these schema rules at compile time:

- Every framed field is required. `order` values must be unique and
  contiguous from zero.
- Each string, blob, nested model, and repeat of those types must start on a
  byte boundary.
- Framed models cannot be mutable, and nested models cannot have type
  parameters.
- Every `@KompactModel` needs at least one `@KompactField`. The processor
  ignores properties without `@KompactField`.
- An empty schema or an invalid field is a compile-time error, and the
  processor generates no view for that schema. KSP defers a field only while
  its type is still unresolved.

At run time, `decode` rejects unread bytes and non-zero padding bits after the
last field with `KompactDecodeError.TrailingData`. Appending an optional field
is therefore not a compatible schema change. For the wire format and schema
evolution limits, see [Design and trade-offs](../architecture.md) and
[ADR-0008](../adr/0008-framed-generated-views.md).

## Add a version envelope

Kompact frames do not carry a schema version. If your application must tell
wire versions apart, it owns the outer envelope, the version identifiers, the
choice of decoder, and the migration policy.

The
[application-owned envelope example](../../kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/ApplicationEnvelopeExampleTest.kt)
writes a one-byte identifier before an encoded frame in caller-provided
storage. When it reads, it borrows the payload instead of copying it. Its
tests cover a round trip, a rejected unsupported version, and a destination
that is too small, which leaves the envelope unchanged.

The one-byte identifier belongs to the application. It is not a Kompact header
and Kompact makes no compatibility promise about it. When the identifier check
fails, the application rejects the frame or routes it to another decoder.

## Check the repository integration

To run the repository's own generated-code checks, run this command from the
repository root:

```bash
./gradlew :kompact-gradle-plugin:test :kompact-ksp-integration:test
```

`:kompact-gradle-plugin:test` builds a Kotlin Multiplatform consumer with
Gradle TestKit. It runs `jvmTest` and compiles the Android, iOS Arm64, iOS
Simulator Arm64, and Android Native Arm64 targets. `:kompact-ksp-integration:test`
runs this guide's `PacketSchema` through standard KSP on the JVM and tests the
generated views.
