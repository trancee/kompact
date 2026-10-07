# Kompact

Kompact is a Kotlin Multiplatform library for reading and writing compact,
LSB-first bit-packed messages. Use the runtime directly for small fixed layouts,
or generate typed views from schemas. The framed API adds strings, byte arrays,
nested messages, and repeated fields with explicit length boundaries.

## Quick start

```kotlin
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

val writer = KompactWriter()
writer.writeScalar(ScalarType.of(4, signed = false), 5L) // battery status
writer.writeScalar(ScalarType.of(10, signed = false), 10L) // speed
writer.writeBool(true) // malfunction flag
val bytes = writer.build() // [0xA5, 0x40]

val battery = KompactRuntime.readScalar(bytes, 0, ScalarType.of(4, signed = false)).getOrThrow()
val speed = KompactRuntime.readScalar(bytes, 4, ScalarType.of(10, signed = false)).getOrThrow()
val malfunction = KompactRuntime.readBool(bytes, 14).getOrThrow()
```

The [getting-started tutorial](docs/getting-started.md) walks through this
frame and shows the expected bytes and decoded values.

## Install

The latest Maven Central release is `0.8.0`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.trancee.kompact:kompact:0.8.0")
}
```

The current development version in this checkout is `0.8.0`; it is
not published to Maven Central. To try unreleased changes, publish the snapshot
modules to Maven Local and follow the
[consumer setup guide](docs/how-to/consume-from-another-project.md).

## Choose a schema

- **Fixed layout:** use `bitOffset` and `bitWidth` when every field has a
  stable position. Generated value-class views are described in
  [How to define a fixed-layout schema](docs/how-to/define-message.md).
- **Sequential framed layout:** use `order` and length/count prefixes when a
  message contains strings, byte arrays, nested messages, or repeated values.
  See [How to define a framed schema](docs/how-to/define-framed-schema.md).

Both forms use the same runtime writer and checked decode APIs. The
[architecture guide](docs/architecture.md) explains the wire format, borrowed
data, and the tradeoffs between the two layouts.

## Documentation

| If you want to… | Start here |
| --- | --- |
| Build and decode your first frame | [Getting started](docs/getting-started.md) |
| Add Kompact to a Kotlin or KMP project | [Consume Kompact](docs/how-to/consume-from-another-project.md) |
| Define a fixed-layout model | [Fixed-layout schema guide](docs/how-to/define-message.md) |
| Define a sequential framed model | [Framed schema guide](docs/how-to/define-framed-schema.md) |
| Read or write strings, blobs, nested data, and repeats | [Long-form payloads](docs/how-to/long-form-payloads.md) |
| Handle malformed input | [Decode errors](docs/how-to/handle-decode-errors.md) |
| Pass frames across a BLE boundary | [BLE integration](docs/how-to/integrate-ble.md) |
| Look up public signatures and behavior | [API reference](docs/api-reference.md) |
| Understand design decisions | [Architecture](docs/architecture.md) |
| Contribute or run project checks | [Contributing](CONTRIBUTING.md) |

The generated, per-symbol API reference is also available in
[`kompact/docs/api/`](kompact/docs/api/index.md).

## Project modules

| Module | Purpose |
| --- | --- |
| `:kompact` | KMP runtime, annotations, and generated API documentation |
| `:kompact-ksp` | KSP processor for fixed-layout and framed schemas |
| `:kompact-gradle-plugin` | Common-source generation for supported KMP targets |

## License

Kompact is released into the public domain. See [`LICENSE`](LICENSE).
