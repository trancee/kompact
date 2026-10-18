# Kompact

Kompact is a Kotlin Multiplatform library that reads and writes compact,
LSB-first bit-packed messages. For a small fixed layout, call the runtime
directly. For a larger message, annotate a schema class and let the KSP
processor generate a typed view. Framed schemas add strings, byte arrays,
nested messages, and repeated fields, each with an explicit length or count
prefix.

## Quick start

This example packs a 4-bit battery status, a 10-bit speed, and a 1-bit flag
into two bytes, then reads them back:

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

The [getting-started tutorial](docs/getting-started.md) builds this frame step
by step and shows the expected bytes and decoded values.

## Install

The latest Maven Central release is `0.9.0`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.trancee.kompact:kompact:0.9.0")
}
```

The development version in this checkout is `0.10.0-SNAPSHOT`. It is not on
Maven Central, and its public API differs from `0.9.0`. To try it, publish the
modules to Maven Local and follow the
[consumer setup guide](docs/how-to/consume-from-another-project.md). The same
guide shows how to apply the `ch.trancee.kompact.codegen` Gradle plugin that
generates views.

## Choose a schema

Kompact has two schema layouts:

- A fixed-layout model gives every field a `bitOffset` and `bitWidth`. Use it
  when every field has a stable position. A schema class `Name` generates a
  `NameView` value class. See
  [How to define a fixed-layout model](docs/how-to/define-message.md).
- A framed model gives every field an `order` and reads fields in sequence.
  Use it when a message contains strings, byte arrays, nested messages, or
  repeated values. See
  [How to define a sequential framed schema](docs/how-to/define-framed-schema.md).

Both layouts use the same `KompactWriter` and the same checked decode results.
The [architecture guide](docs/architecture.md) explains the wire format,
borrowed data, and the trade-offs between the two layouts.

## Documentation

| Goal | Start here |
| --- | --- |
| Build and decode your first frame | [Getting started](docs/getting-started.md) |
| Add Kompact to a Kotlin or KMP project | [Consume Kompact](docs/how-to/consume-from-another-project.md) |
| Define a fixed-layout model | [Fixed-layout schema guide](docs/how-to/define-message.md) |
| Define a sequential framed model | [Framed schema guide](docs/how-to/define-framed-schema.md) |
| Read or write strings, blobs, nested data, and repeats | [Long-form payloads](docs/how-to/long-form-payloads.md) |
| Handle malformed input | [Decode errors](docs/how-to/handle-decode-errors.md) |
| Pass frames across a BLE boundary | [BLE integration](docs/how-to/integrate-ble.md) |
| Measure runtime performance on a host | [Benchmark the runtime](docs/how-to/benchmark-runtime.md) |
| Look up public signatures and behavior | [API reference](docs/api-reference.md) |
| Understand design decisions | [Architecture](docs/architecture.md) |
| Contribute or run project checks | [Contributing](CONTRIBUTING.md) |

The [documentation map](docs/README.md) lists every guide, ADR, and research
note. Dokka generates a per-symbol API reference into
[`kompact/docs/api/`](kompact/docs/api/index.md).

## Project modules

The repository contains three published modules and two internal ones:

| Module | Published | Purpose |
| --- | --- | --- |
| `:kompact` | Yes | KMP runtime and schema annotations |
| `:kompact-ksp` | Yes | KSP processor that generates views and holders for fixed-layout and framed schemas |
| `:kompact-gradle-plugin` | Yes | Gradle plugin `ch.trancee.kompact.codegen`, which runs the processor once over `commonMain` |
| `:kompact-ksp-integration` | No | Tests that compile and run real generated code |
| `:benchmarks` | No | Host benchmarks for the runtime |

## License

Kompact is released into the public domain. See [`LICENSE`](LICENSE).
