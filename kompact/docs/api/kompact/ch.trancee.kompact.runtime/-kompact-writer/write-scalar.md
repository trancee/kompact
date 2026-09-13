//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactWriter](index.md)/[writeScalar](write-scalar.md)

# writeScalar

[common]\
fun [writeScalar](write-scalar.md)(type: [ScalarType](../-scalar-type/index.md), value: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

Writes [value](write-scalar.md) under [type](write-scalar.md): [ScalarType.bitWidth](../-scalar-type/bit-width.md) low bits as a two's-complement magnitude (1..64), dispatched to [writeBits](write-bits.md) (<=31) / [writeBitsLong](write-bits-long.md) (32..64). Replaces the writeInt/writeUInt/writeInt64/writeEnum overloads — one accessor per width-band (ergonomics-01: ScalarType consolidation). Pass a [ScalarType](../-scalar-type/index.md) carrying the band.