//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRuntime](index.md)/[decodeFullInt](decode-full-int.md)

# decodeFullInt

[common]\
fun [decodeFullInt](decode-full-int.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), type: [ScalarType](../-scalar-type/index.md)): [DetailedResult](../-detailed-result/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;

Opt-in diagnostics reads (ADR-0005 §2). Each returns a [DetailedResult](../-detailed-result/index.md) carrying, on failure, a [DetailedDecodeError](../-detailed-decode-error/index.md) with the byte offset of the failure (`bitOffset ushr 3`); on success the decoded value. These wrap the zero-alloc `read*` accessors and allocate the [DetailedResult](../-detailed-result/index.md) holder — they are NOT on the read hot path. Use [readScalar](read-scalar.md)/[readScalarAsLong](read-scalar-as-long.md)/[readFloat](read-float.md)/ [readDouble](read-double.md)/[readBool](read-bool.md) when offsets/errors are unneeded (Tickets 03/10).