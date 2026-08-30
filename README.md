# Kompact

Bit-packed Kotlin Multiplatform serialization for Bluetooth Low Energy, designed for allocation-free reads and interoperable C firmware.

> [!IMPORTANT]
> The Kompact v1 architecture and implementation specification are complete. Production implementation has not started, so the runtime, generator, and published artifacts do not exist yet. The closed [Kompact v1 implementation-ready specification](https://github.com/trancee/kompact/issues/1) map records every decision.

## Why Kompact

BLE payloads are small, and byte-aligned formats can spend more space on padding and metadata than the values require. Kompact defines each field at bit precision. A 5-bit value occupies 5 bits, including when it crosses a byte boundary.

The project has four goals:

- Pack fixed-size payloads without byte padding.
- Read scalar fields directly from caller-owned `ByteArray` storage without copying.
- Generate a typed Kotlin interface for shared Android and iOS code.
- Generate matching C99 constants and helpers for firmware.

## Current design direction

Kompact v1 is specified with these constraints:

- Schemas have fixed, versioned layouts and an explicit envelope.
- Bit offset zero is the least-significant bit of byte zero.
- Kotlin runtime and generated code live in `commonMain` and target Android/JVM, `iosArm64`, and `iosSimulatorArm64`.
- A checked factory validates the envelope, version, and payload length before creating a view.
- Scalar properties read bits directly from the underlying buffer.
- Writers update caller-owned buffers in place and reject invalid values before mutation.
- KSP processes schemas and generates Kotlin code.
- Build-time JVM tooling generates portable C99 masks and byte-array helpers. It does not generate packed structs or C bitfields.
- Performance claims require measurements for reads, writes, allocations, encoded size, and code size.

Variable-length fields, direct Swift export, compiler-plugin generation, and non-iOS Apple targets are outside the v1 scope.

## Example layout

A 16-bit telemetry payload can assign every bit without alignment padding:

```text
bits  0..3   battery status       4-bit enum
bits  4..13  speed               10-bit unsigned integer
bit      14  engine malfunction   1-bit boolean
bit      15  reserved             1 bit
```

The same schema will drive generated Kotlin accessors and C99 extraction helpers. Developers author annotated schema interfaces; generated checked facades expose value-class views and writers in Kotlin and header-only typed handles in C99.

## Specification status

The closed [Kompact v1 implementation-ready specification](https://github.com/trancee/kompact/issues/1) is the canonical decision map. Its child issues record:

- wire and envelope semantics;
- KSP and Gradle integration;
- generated Kotlin and C99 interfaces;
- validation and compatibility;
- cross-platform conformance; and
- performance budgets.

Project-specific terminology lives in [`CONTEXT.md`](CONTEXT.md).
