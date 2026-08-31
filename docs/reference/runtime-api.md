# Runtime API reference

This page describes the public common Kotlin runtime in Kompact `0.1.0-SNAPSHOT`.

## `KompactRuntime`

All bit offsets are absolute packet offsets. A bit position `p` addresses byte `p / 8` and bit `p % 8`; bit zero is the least-significant bit of byte zero.

### `readBits`

```kotlin
fun readBits(packet: ByteArray, bitOffset: Int, bitWidth: Int): ULong
```

Reads an unsigned value from `1..64` bits. The range may cross byte boundaries.

Throws `IllegalArgumentException` when the offset is negative, the width is outside `1..64`, or the range exceeds the packet.

### `readSignedBits`

```kotlin
fun readSignedBits(packet: ByteArray, bitOffset: Int, bitWidth: Int): Long
```

Reads and sign-extends a two's-complement value from `2..64` bits.

Throws `IllegalArgumentException` for an invalid signed width or packet range.

### `readBitsBoolean`

```kotlin
fun readBitsBoolean(packet: ByteArray, bitOffset: Int): Boolean
```

Returns `false` for zero and `true` for one at the selected bit.

### `writeBits`

```kotlin
fun writeBits(
    packet: ByteArray,
    bitOffset: Int,
    bitWidth: Int,
    value: ULong,
): KompactWriteError?
```

Writes an unsigned value into `1..64` bits and preserves every bit outside the range.

Returns `KompactWriteError.ValueOutOfRange` without mutation when the value does not fit. Returns `null` on success. Throws `IllegalArgumentException` for an invalid packet range.

### `writeSignedBits`

```kotlin
fun writeSignedBits(
    packet: ByteArray,
    bitOffset: Int,
    bitWidth: Int,
    value: Long,
): KompactWriteError?
```

Writes a two's-complement value into `2..64` bits. Returns `ValueOutOfRange` without mutation when the value does not fit.

### Boolean access

```kotlin
fun writeBitsBoolean(packet: ByteArray, bitOffset: Int, value: Boolean)
```

Writes one bit. Packet-range preconditions are the same as `writeBits`.

### Floating-point access

```kotlin
fun readFloatBits(packet: ByteArray, bitOffset: Int): Float
fun readDoubleBits(packet: ByteArray, bitOffset: Int): Double
fun writeFloatBits(packet: ByteArray, bitOffset: Int, value: Float)
fun writeDoubleBits(packet: ByteArray, bitOffset: Int, value: Double)
```

Reads IEEE binary32 or binary64 raw bits. Writers preserve infinities and signed zero and canonicalize every NaN to positive quiet NaN:

| Type | Canonical written NaN |
| --- | --- |
| `Float` | `0x7fc00000` |
| `Double` | `0x7ff8000000000000` |

## `KompactDecodeResult`

```kotlin
sealed interface KompactDecodeResult<out T> {
    data class Success<T>(val value: T)
    data class Failure(val error: KompactDecodeError)
}
```

Factories return `Success` with a concrete view or writer, or `Failure` with one typed error. Failure metadata never contains packet bytes or attempted values.

## `KompactDecodeError`

| Variant | Metadata | Status |
| --- | --- | --- |
| `InvalidPacketLength` | expected and actual lengths | `INVALID_PACKET_LENGTH` |
| `ReservedSchemaId` | layout version | `RESERVED_SCHEMA_ID` |
| `UnknownSchemaId` | schema ID and version | `UNKNOWN_SCHEMA_ID` |
| `UnsupportedLayoutVersion` | schema ID and version | `UNSUPPORTED_LAYOUT_VERSION` |
| `NonzeroTailBits` | schema ID, version, bit offset | `NONZERO_TAIL_BITS` |
| `UnknownEnumCode` | schema ID, version, stable field path, bit offset, optional array index | `UNKNOWN_ENUM_CODE` |
| `NonzeroReservedBits` | schema ID, version, stable field path, bit offset, optional array index | `NONZERO_RESERVED_BITS` |
| `NonzeroAbsentOptional` | schema ID, version, stable field path, bit offset, optional array index | `NONZERO_ABSENT_OPTIONAL` |
| `InternalInvariantFailure` | operation name | `INTERNAL_INVARIANT_FAILURE` |

## `KompactWriteError`

| Variant | Metadata | Status |
| --- | --- | --- |
| `ValueOutOfRange` | bit width | `VALUE_OUT_OF_RANGE` |
| `IndexOutOfRange` | rejected index | `INDEX_OUT_OF_RANGE` |
| `UnknownEnumCode` | stable field path | `UNKNOWN_ENUM_CODE` |
| `InternalInvariantFailure` | operation name | `INTERNAL_INVARIANT_FAILURE` |

Generated Kotlin indexed reads throw `IndexOutOfBoundsException`. Generated Kotlin writes return `IndexOutOfRange`. Generated C indexed reads and writes return `KOMPACT_STATUS_INDEX_OUT_OF_RANGE`.

## Status assignments

`KompactStatusCode.value` and C `KOMPACT_STATUS_*` constants use the same one-byte assignments:

| Hex | Name |
| --- | --- |
| `0x00` | `OK` |
| `0x01` | `NULL_ARGUMENT` |
| `0x02` | `INVALID_PACKET_LENGTH` |
| `0x03` | `RESERVED_SCHEMA_ID` |
| `0x04` | `UNKNOWN_SCHEMA_ID` |
| `0x05` | `UNSUPPORTED_LAYOUT_VERSION` |
| `0x06` | `NONZERO_TAIL_BITS` |
| `0x07` | `UNKNOWN_ENUM_CODE` |
| `0x08` | `NONZERO_RESERVED_BITS` |
| `0x09` | `NONZERO_ABSENT_OPTIONAL` |
| `0x0a` | `VALUE_OUT_OF_RANGE` |
| `0x0b` | `INDEX_OUT_OF_RANGE` |
| `0x0c` | `INTERNAL_INVARIANT_FAILURE` |

Values `0x0d..0xff` are reserved.

For generated facades and field accessors, see the [schema authoring reference](schema-authoring.md). For the bitstream rationale, see [ADR-0001](../adr/0001-bitstream-and-scalar-wire-format.md).
