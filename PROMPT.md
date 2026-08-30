You are an expert systems architect and compiler engineer specializing in Kotlin Multiplatform (KMP), Kotlin/Native, and ultra-low-latency binary serialization.

We are building a custom, highly efficient serialization framework named **Kompact** for a KMP library targeting both **Android** and **iOS**.
The primary objective of Kompact is to serialize structured data for transmission over Bluetooth Low Energy (BLE). It must combine the absolute best features of modern libraries:
1. **Microscopic Payload Sizes** (like Protobuf's bit efficiency, with zero byte padding).
2. **Zero-Copy, Zero-Allocation Reads** (like FlatBuffers, utilizing native Kotlin Multiplatform inline/value classes to wrap raw byte arrays).
3. **Pure Kotlin Ergonomics** (designed to be generated via an annotation processor or compiler plugin).
4. **Firmware Compatibility** (ability to export matching C-header bitmask definitions).

---

### 1. Core Architectural & KMP Constraints

- **Multiplatform Value Classes:** Do NOT use JVM-specific annotations like `@JvmInline`. Use the standard Kotlin multiplatform `value class` syntax to ensure zero-heap-allocation wrapping on both JVM/Android and Kotlin/Native (iOS).
- **Zero Padding:** Data must be tightly bit-packed sequentially. If a field only requires 5 bits, it takes exactly 5 bits in the stream.
- **Zero Allocation Views:** Fields must be exposed as Kotlin `val` properties that execute bit-shifting arithmetic directly on the underlying common `ByteArray` buffer at runtime.
- **Platform-Agnostic Endianness:** The bit-shifting algorithms must behave identically on Android and iOS runtimes regardless of lower-level platform architectures. Keep logic grounded in standard bitwise operations (`shl`, `shr`, `and`, `or`) operating over common Kotlin `Byte` boundaries.

---

### 2. Targeted Common/KMP Syntax Example
The developer should be able to define a data model in the `commonMain` source set like this:

```kotlin
package com.kompact.generated

import com.kompact.runtime.*

@KompactModel
value class VehicleTelemetry(val raw: ByteArray) {
    // Layout matrix packed into 2 Bytes (16 bits total):
    // [0..3]   (4 bits): Battery Status Enum (0-15)
    // [4..13]  (10 bits): Speed integer (0-1023)
    // [14..14] (1 bit):   Is Engine Malfunction Active (Boolean)
    // [15..15] (1 bit):   Reserved/Unused
    
    @KompactField(bitOffset = 0, bitWidth = 4) 
    val batteryStatus: Int get() = KompactRuntime.readBits(raw, 0, 4)

    @KompactField(bitOffset = 4, bitWidth = 10) 
    val speed: Int get() = KompactRuntime.readBits(raw, 4, 10)

    @KompactField(bitOffset = 14, bitWidth = 1) 
    val isMalfunctioning: Boolean get() = KompactRuntime.readBitsBoolean(raw, 14)
}
```

---

### 3. Project Implementation Requirements

Please generate the code architecture split into the following phases, ensuring all code sits cleanly in a shared KMP `commonMain` context:

#### Phase 1: The Common Runtime Utility (`KompactRuntime`)
Write an optimized Kotlin file containing inline functions to read and write arbitrary bit-ranges from a standard common `ByteArray`.
- Must handle arbitrary bit-offsets that cross byte boundaries smoothly (e.g., reading a 10-bit integer starting at bit index 4 and bleeding into the second byte).
- Provide specialized common primitives for `readBits`, `writeBits`, and `readBitsBoolean`.

#### Phase 2: Multiplatform Annotation Definitions
Define the core common library annotations:
- `@KompactModel`: Marks an inline value class as a Kompact schema.
- `@KompactField(val bitOffset: Int, val bitWidth: Int)`: Annotates properties to document and validate their position in the binary stream.

#### Phase 3: A Concrete Shared Example Implementation
Provide a complete, working example of a `Kompact` value class using the `KompactRuntime` to demonstrate how the boilerplate will eventually look when automated. Include:
1. The manual bit-shifting implementation of a model containing an Enum (4 bits), an Integer (10 bits), and a Boolean (1 bit)—packed into a 2-byte array (`ByteArray`).
2. A cross-platform test using `kotlin.test` showing serialization (writing values into the array) and deserialization (instantiating the value class wrapper and instantly reading values).

#### Phase 4: C Header Exporter Specification
Draft a basic Kotlin utility function that can parse a Kompact data class declaration and print out a standard C `#define` macro header. This ensures our embedded/C firmware engineers can read the exact same BLE payload by applying the same bitmasks.
