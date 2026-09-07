# Kompact

A bit-packed, zero-allocation serialization framework for Kotlin Multiplatform.
Built for tiny, dense wire payloads (think BLE characteristics) that still need to
be safely decoded on the hot path — no boxing, no exception throwing, no
intermediate copies.

```
import ch.trancee.kompact.runtime.ScalarType

// Write 16 bits: 4 bits battery + 10 bits speed + 1 bit flag + 1 bit reserved
val w = KompactWriter()
w.writeScalar(ScalarType.of(4,  signed = false), 5L)  // battery = 5
w.writeScalar(ScalarType.of(10, signed = false), 10L)  // speed = 10
w.writeBool(true)                                       // malfunction = true
val bytes: ByteArray = w.build()                        // 2 bytes: 0xA5 0x40

// Read them back as typed results — no exceptions on the success path
val battery: Int      = KompactRuntime.readScalar(bytes, 0,  ScalarType.of(4,  signed = false)).getOrThrow()
val speed:    Int      = KompactRuntime.readScalar(bytes, 4, ScalarType.of(10, signed = false)).getOrThrow()
val flag:     Boolean  = KompactRuntime.readBool    (bytes, 14          ).getOrThrow()
```

## Why the reader/writer pattern (not serialize/deserialize)

Kompact's `ByteArray` **is** the data structure. The value
class `@KompactModel value class VehicleTelemetry(val raw: ByteArray)` stores
the wire bytes directly. Field getters call `KompactRuntime.readBits` /
`readScalar` / `readBool` on that same buffer. There is no step that
turns bytes into a separate object, because that step allocates.

This matters because BLE characteristics are tiny (a few bytes) and
arrive frequently. The decoder runs on battery-powered devices. Every
heap allocation costs power and stalls the radio. Traditional frameworks
pay that cost twice: once on decode (allocate a data class, box every
field) and once on encode (build an object tree, then walk it).

Kompact avoids both by reading a primitive directly from the buffer with
zero heap activity. The result value classes (`IntResult`, `LongResult`,
`BooleanResult`, `FloatResult`, `DoubleResult`, and the rest) are
`@JvmInline` / `value class` wrappers over a single packed `Long`. On
success they cost exactly a `Long` on the stack — no object header, no
GC pressure.

Reads are also **lazy**. You pull only the fields you need. A 2-byte
telemetry frame with a 4-bit battery status and a 10-bit speed lets you
read `speed` without ever decoding `batteryStatus`. A
`deserialize(bytes) → FullObject` forces you to parse everything first.

This design also **cannot** be FlatBuffers-style random access.
FlatBuffers stores offset pointers so you can jump to any field. That
breaks when a field before it changes size. Kompact's v1 type set
includes variable-length strings, blobs, nested composites, and repeats.
So offsets would shift on every schema change. Reads are sequential,
parse-forward instead — the deliberate but necessary tradeoff: you
trade random-access field jumps for zero-allocation, lazy, sequential
reads.

## Creating and modifying frames

Construct a frame from field values with `VehicleTelemetry.create(...)`:

```kotlin
val tel = VehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)
// tel.raw is the 2-byte wire buffer: [0xA5, 0x40]
```

Modify a field in-place — the setter writes directly to the backing `ByteArray`:

```kotlin
tel.speed = 30
// tel.raw is now updated; no new allocation
```

Send the buffer over BLE:

```kotlin
bleCharacteristic.value = tel.raw
```

Receive a frame from BLE and decode it:

```kotlin
val tel = VehicleTelemetry(bleCharacteristic.value)
val speed = tel.speed      // 30
val flag  = tel.isMalfunctioning  // true
```

Setters work because the `ByteArray` is a mutable reference shared by the
value class. You read one field, modify one field, and transmit the same
buffer — no intermediate objects, no copy.

**Quick reference** — all four operations in one snippet:

```kotlin
// 1. One-liner create → raw bytes
val raw: ByteArray = VehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalformed = true).raw

// 2. Construct from raw bytes (e.g. received from BLE)
val tel = VehicleTelemetry(raw)

// 3. Overwrite a field in-place (writes directly to the backing buffer)
tel.speed = 30

// 4. Get the raw bytes again — no copy
bleCharacteristic.value = tel.raw
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
| Define your own message (with code snippets for common cases) | **[`docs/how-to/define-message.md`](docs/how-to/define-message.md)** |
| Pack / parse strings, blobs, nested composites, or repeated fields | **[`docs/how-to/long-form-payloads.md`](docs/how-to/long-form-payloads.md)** |
| Handle a `KompactDecodeError` without throwing on the hot path | **[`docs/how-to/handle-decode-errors.md`](docs/how-to/handle-decode-errors.md)** |
| Send a frame over BLE / receive one back | **[`docs/how-to/integrate-ble.md`](docs/how-to/integrate-ble.md)** |
| Consume Kompact from a separate Kotlin / KMP project | **[`docs/how-to/consume-from-another-project.md`](docs/how-to/consume-from-another-project.md)** |
| All how-to guides (task-oriented recipes) | **[`docs/how-to/README.md`](docs/how-to/README.md)** |
| Read the original product brief | [`PROMPT.md`](PROMPT.md) |
| Read the locked implementation spec (tickets 01–13) | [`.scratch/kompact-spec/map.md`](.scratch/kompact-spec/map.md) |

## Status

`0.1.0-SNAPSHOT` — the runtime, writer, framing, and result value classes are
stable and exercised by the `commonTest` suite. Publication is wired via
standard `maven-publish` + `signing` + Dokka with a custom Central Portal
Publisher API task (`centralPortalDeploy`, staging to `USER_MANAGED`). Maven
coordinates `ch.trancee.kompact:kompact`, license Apache-2.0, but **no
release has been cut to Maven Central yet** — the Portal namespace, PGP key,
and user token still require user authorization. Build from source or
`./gradlew :kompact:publishToMavenLocal` and consume the local snapshot.
See the [release contract](.scratch/kompact-spec/issues/14-maven-central-publishing.md)
for what's left to do.

## License

Apache License 2.0. See [`build.gradle.kts`](kompact/build.gradle.kts) for the
full publication metadata.
