# Kompact

A bit-packed, zero-allocation serialization framework for Kotlin Multiplatform, designed for
short payloads (Bluetooth Low Energy and other small-frame transports).

Three modules ship:

| Module | What it does |
|---|---|
| `:kompact` | The KMP runtime: bit-level read/write primitives, value-class result types, the writer, the versioned-stream helper, the allocation counter. |
| `:kompact-ksp` | A JVM-only KSP processor that validates schemas at compile time and emits the value-class views. |
| `:kompact-example` | A working `VehicleTelemetry` schema showing write → byte array → read. |

## Where to go next

- New to Kompact — [How to use Kompact](docs/how-to-use-kompact.md) walks you through defining a schema, writing bytes, and reading them back.
- Looking up a specific API — the [reference](docs/reference/) mirrors the public surface (`KompactRuntime`, `KompactRead`, `KompactWriter`, the result types, the annotations, the KSP processor).
- Want to know *why* the framework works the way it does — the [design rationale](docs/explanation/design-rationale.md) explains the trade-offs that shaped the API.
- The spec that drove the implementation lives at `.scratch/kompact-spec/map.md` (Tickets 01–13 all resolved, destination locked).

## Build

```
./gradlew build
```

The build compiles all three modules for the JVM, `iosArm64`, and `iosSimulatorArm64` targets,
runs the 96-test suite, and checks the public-API golden files.

## License

See the repository's license file.
