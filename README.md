# Kompact

Kompact is a bit-packed, zero-allocation serialization library for Kotlin Multiplatform. It targets short payloads: Bluetooth Low Energy frames, sensors, anything where every byte costs.

Three modules ship:

| Module | What it does |
|---|---|
| `:kompact` | The KMP runtime. Bit-level read and write primitives, value-class result types, the writer, the versioned-stream helper, the allocation counter. |
| `:kompact-ksp` | A JVM-only KSP processor that validates schemas at compile time and generates the value-class views. |
| `:kompact-example` | A working `VehicleTelemetry` schema showing the full write-and-read round trip. |

## Where to go next

Pick the path that matches what you want to do.

- **I want to use Kompact.** Start with [How to use Kompact](docs/how-to-use-kompact.md). It walks you through defining a schema, writing bytes, and reading them back.
- **I want to look up a specific API.** The [reference](docs/reference/) mirrors the public surface. `KompactRuntime`, `KompactRead`, `KompactWriter`, the result types, the annotations, the KSP processor. One page per concern.
- **I want to know why it's built this way.** The [design rationale](docs/explanation/design-rationale.md) walks the trade-offs that shaped the API. Why bit-packed. Why value classes. Why length-prefixed. Why zero-allocation reads.

## Build

```
./gradlew build
```

The build compiles all three modules for the JVM, `iosArm64`, and `iosSimulatorArm64` targets, runs the test suite, and checks the public-API golden files. 60 tests run on Linux against the JVM target; the iOS targets compile but require a Mac to execute.

## License

See the repository's license file.
