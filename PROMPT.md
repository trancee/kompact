You are an expert systems architect and compiler engineer specializing in Kotlin Multiplatform (KMP), Kotlin/Native, and compact binary serialization.

We are building a custom, highly efficient serialization framework named **Kompact** for a KMP library targeting both **Android** and **iOS**.
The primary design goal is to serialize structured data for transmission over Bluetooth Low Energy (BLE). Use the repository's source, tests, build configuration, and current documentation as the authority for implemented behavior; distinguish design goals from measured guarantees. The project aims to combine useful features of modern libraries:
1. **Microscopic Payload Sizes** (like Protobuf's bit efficiency, with zero byte padding).
2. **Low-copy reads** over caller-owned byte arrays. Zero-allocation behavior is a measurement target for specific call shapes and platforms, not a universal guarantee.
3. **Pure Kotlin Ergonomics** (designed to be generated via an annotation processor or compiler plugin).

---

### 1. Core Architectural & KMP Constraints

- **Multiplatform Value Classes:** Common declarations use Kotlin multiplatform `value class` syntax. JVM actuals require `@JvmInline`; Kotlin/Native actuals do not. Keep `@JvmInline` out of common source.
- **Zero Padding:** Data must be tightly bit-packed sequentially. If a field only requires 5 bits, it takes exactly 5 bits in the stream.
- **Read Views:** Expose schema fields as Kotlin properties over the underlying `ByteArray`. Preserve borrowing where the API documents it, and do not claim zero allocation without platform- and call-shape-specific measurements. See `docs/research/allocation-boxing-measurement.md`.
- **Platform-Agnostic Endianness:** The bit-shifting algorithms must behave identically on Android and iOS runtimes regardless of lower-level platform architectures. Keep logic grounded in standard bitwise operations (`shl`, `shr`, `and`, `or`) operating over common Kotlin `Byte` boundaries.

---

### 2. Common KMP Schema Contract

The common schema is an `expect` declaration. Generated or platform-specific
actuals provide the implementation; this contract is not a standalone
common-source getter implementation.

```kotlin
package ch.trancee.kompact.generated

import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactModel

@KompactModel
public expect value class VehicleTelemetry(public val raw: ByteArray) {
    // Layout matrix packed into 2 Bytes (16 bits total):
    // [0..3]   (4 bits): Battery Status Enum (0-15)
    // [4..13]  (10 bits): Speed integer (0-1023)
    // [14..14] (1 bit):   Is Engine Malfunction Active (Boolean)
    // [15..15] (1 bit):   Reserved/Unused

    @KompactField(bitOffset = 0, bitWidth = 4)
    public val batteryStatus: Int

    @KompactField(bitOffset = 4, bitWidth = 10)
    public val speed: Int

    @KompactField(bitOffset = 14, bitWidth = 1)
    public val isMalfunctioning: Boolean
}
```

---

### 3. Project Implementation Requirements

The repository already contains the runtime, annotations, generated models, and
cross-platform tests. Extend those existing components rather than duplicating
them. Keep platform-independent APIs and behavior in `commonMain`; put JVM
`@JvmInline` and Kotlin/Native actuals in their appropriate source sets.

#### Phase 1: The Common Runtime Utility (`KompactRuntime`)
Use or extend the existing common `KompactRuntime` functions to read and write arbitrary bit-ranges from a standard common `ByteArray`.
- Must handle arbitrary bit-offsets that cross byte boundaries smoothly (e.g., reading a 10-bit integer starting at bit index 4 and bleeding into the second byte).
- Provide specialized common primitives for `readBits`, `writeBits`, and `readBitsBoolean`.

#### Phase 2: Multiplatform Annotation Definitions
Use the existing common library annotations, extending them only when the requested schema contract requires it:
- `@KompactModel`: Marks an inline value class as a Kompact schema.
- `@KompactField(val bitOffset: Int, val bitWidth: Int)`: Annotates properties to document and validate their position in the binary stream.

#### Phase 3: A Concrete Shared Example Implementation
Keep a complete, working example of a `Kompact` model using the `KompactRuntime` or generated views. Put common contracts in `commonMain`, platform actuals in the supported target source sets, and serialization tests in `commonTest`. Include:
1. The manual bit-shifting implementation of a model containing an Enum (4 bits), an Integer (10 bits), and a Boolean (1 bit)—packed into a 2-byte array (`ByteArray`).
2. A cross-platform test using `kotlin.test` showing serialization (writing values into the array) and deserialization (instantiating the value class wrapper and instantly reading values).
