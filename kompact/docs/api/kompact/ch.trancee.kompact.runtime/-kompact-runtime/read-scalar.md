//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readScalar](read-scalar.md)

# readScalar

[common]\
fun [readScalar](read-scalar.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), type: [ScalarType](../-scalar-type/index.md)): [IntResult](../-int-result/index.md)

Reads up to [ScalarType.bitWidth](../-scalar-type/bit-width.md) bits of [type](read-scalar.md) as a checked [IntResult](../-int-result/index.md). The width (1..32) and signedness come from [type](read-scalar.md), so a single accessor replaces the 8 per-width readInt8/16/32 and readUInt8/16/32 overloads (ergonomics-01: ScalarType consolidation). Sign extension uses Long-arithmetic shifts, bit-identical to the legacy accessors. Callers pass a [ScalarType](../-scalar-type/index.md); see [readScalarOrThrow](read-scalar-or-throw.md) for the exceptions variant.