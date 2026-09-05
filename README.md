# Kompact

A bit-packed, zero-allocation serialization framework for Kotlin Multiplatform.
Built for tiny, dense wire payloads (think BLE characteristics) that still need to
be safely decoded on the hot path — no boxing, no exception throwing, no
intermediate copies.

```
// Write 16 bits: 4 bits battery + 10 bits speed + 1 bit flag + 1 bit reserved
val w = KompactWriter()
w.writeScalar(bitWidth = 4,  value = 5L)    // battery = 5
w.writeScalar(bitWidth = 10, value = 10L)   // speed = 10
w.writeBool(true)                           // malfunction = true
val bytes: ByteArray = w.build()            // 2 bytes: 0xA5 0x40

// Read them back as typed results — no exceptions on the success path
val battery: Int      = KompactRuntime.readScalar(bytes, 0,  4, signed = false).getOrThrow()
val speed:    Int      = KompactRuntime.readScalar(bytes, 4, 10, signed = false).getOrThrow()
val flag:     Boolean  = KompactRuntime.readBool    (bytes, 14          ).getOrThrow()
```

## What's in this repo

- **`:kompact`** — the KMP runtime: bit primitives, a forward-only writer, framing
  helpers, and seven zero-alloc typed result value classes (`ByteResult`,
  `ShortResult`, `IntResult`, `LongResult`, `FloatResult`, `DoubleResult`,
  `BooleanResult`).
- **Targets**: `jvm` (JVM 21), `iosArm64`, `iosSimulatorArm64`. Android consumes
  the `jvm` artifact.
- **No codegen yet.** `@KompactModel` / `@KompactField` annotations are defined
  (and validated for source compatibility by `KompactFieldV1SurfaceTest`) but
  no KSP processor ships in this repository. Today you write the bit-shifting
  by hand, the way the bundled `VehicleTelemetry` example does.

## Where to go next

| If you want to … | Read |
| --- | --- |
| Try it end-to-end (write a frame, read it back) | **[`docs/getting-started.md`](docs/getting-started.md)** |
| Look up an exact API signature, parameter, or error | **[`docs/api-reference.md`](docs/api-reference.md)** |
| Understand the design choices (LSB-first, zero-alloc, value classes, framing) | **[`docs/architecture.md`](docs/architecture.md)** |
| Run / understand the CI gates and goldens | **[`docs/ci.md`](docs/ci.md)** |
| See all of the above at a glance | **[`docs/README.md`](docs/README.md)** |
| Read the original product brief | [`PROMPT.md`](PROMPT.md) |
| Read the locked implementation spec (tickets 01–13) | [`.scratch/kompact-spec/map.md`](.scratch/kompact-spec/map.md) |

## Status

`0.1.0-SNAPSHOT` — the runtime, writer, framing, and result value classes are
stable and exercised by the `commonTest` suite. The publication pipeline is
configured (Maven coordinates `ch.trancee.kompact:kompact`, license Apache-2.0)
but **no release has been cut to Maven Central yet**. Build from source or
`./gradlew :kompact:publishToMavenLocal` and consume the local snapshot.

## License

Apache License 2.0. See [`build.gradle.kts`](kompact/build.gradle.kts) for the
full publication metadata.
