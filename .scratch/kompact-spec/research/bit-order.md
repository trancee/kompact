# Bit-Ordering Convention for Zero-Copy Bit-Packed Streams

## Recommendation
Use LSB-first (little-endian) bit ordering for a zero-copy bit-packed stream, as it is the dominant convention in modern serialization frameworks and aligns with x86/ARM native bit ordering.

## Key Evidence

### LSB-First Convention (Recommended)

**Cap'n Proto Encoding Spec** states:
> "Booleans are packed bit-by-bit in little-endian order (the first bit is the least-significant bit of the first byte)."

**SLAC Protocol (ISO 15118 EV charging)** documentation confirms:
> The SLAC protocol transmits data with the least-significant-bit first ordering.

This convention means that for an integer crossing a byte boundary:
- Byte 0 contains bits 0-7 (LSB of field first)
- Byte 1 contains bits 8-15 (next LSB)
- Bit 0 = LSB of the integer value

To assemble from bytes in Kotlin multiplatform:
```kotlin
// Read a 12-bit value spanning bytes[0] and bytes[1]
val value = ((bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)) and 0x0FFF
```

### MSB-First Convention (Alternative)

**ASN.1 PER (ITU-T X.691)** specifies:
> "bits are transmitted most-significant-bit-first (big-endian) within each octet; the first bit emitted for a value is the high-order bit of the first byte"

This requires different assembly logic where higher-order bits come first in the byte stream.

### FlatBuffers Clarification

FlatBuffers uses little-endian byte order for multi-byte scalars but does not perform bit-packing beyond byte alignment—fields occupy whole bytes. This makes FlatBuffers unsuitable for bit-packed integer scenarios, but its little-endian byte-order convention aligns with the LSB-first recommendation.

## Boundary/Caveat

When implementing bit-packed integer assembly:
1. **Signed integers**: Use two's complement on the assembled value after bit reconstruction
2. **Byte operations**: Kotlin `Byte` is signed (-128 to 127); always use `.toInt() and 0xFF` for unsigned interpretation before bit operations
3. **Cross-platform consistency**: The `shl`/`shr`/`and`/`or` operations on `Byte` in Kotlin Multiplatform (JVM, Android, iosArm64, iosSimulatorArm64) require explicit masking to 0xFF to handle sign extension correctly on platforms where `Byte` arithmetic propagates signs

## Sources
- Cap'n Proto Encoding Specification, capnproto.org/encoding.html
- Wikipedia Bit Numbering, en.wikipedia.org/wiki/Bit_numbering
- ASN.1 X.691 PER specification (ITU-T)
- SLAC protocol documentation for ISO 15118 electric vehicle charging
- FlatBuffers format documentation

---

*Research for Kompact serialization framework - Wayfinder ticket 01-wire-format-bit-order*