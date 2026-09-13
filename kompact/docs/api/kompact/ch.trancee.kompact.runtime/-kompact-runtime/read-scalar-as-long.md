//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[readScalarAsLong](read-scalar-as-long.md)

# readScalarAsLong

[common]\
fun [readScalarAsLong](read-scalar-as-long.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), type: [ScalarType](../-scalar-type/index.md)): [LongResult](../-long-result/index.md)

Reads up to [ScalarType.bitWidth](../-scalar-type/bit-width.md) bits of [type](read-scalar-as-long.md) as a checked [LongResult](../-long-result/index.md) (1..64). Width/signedness derive from [type](read-scalar-as-long.md); sign extension (two's-complement) uses Long-arithmetic shifts. Replaces readScalarLong(w, b, signed); callers pass a [ScalarType](../-scalar-type/index.md) carrying the UInt64/Int64 bands (ergonomics-01: ScalarType consolidation). See [readScalarAsLongOrThrow](read-scalar-as-long-or-throw.md) for the exceptions variant.