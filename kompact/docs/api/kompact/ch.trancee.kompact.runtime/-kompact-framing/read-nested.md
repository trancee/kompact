//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)/[readNested](read-nested.md)

# readNested

[common]\
fun [readNested](read-nested.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), prefixBitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [NestedRegionResult](../-nested-region-result/index.md)

Typed [NestedRegionResult](../-nested-region-result/index.md) variant of nestedRegionOrNull (Ticket 05).

Parses the [prefixBitWidth](read-nested.md) length-prefix at [bitOffset](read-nested.md) and, on success, returns the `(startBit, bitLength)` of the payload region. On a bad prefix width, an unreadable prefix, an overflowing count (F-003), or a length-prefix that exceeds the remaining buffer, returns a typed [KompactDecodeError.BadLengthPrefix](../-kompact-decode-error/-bad-length-prefix/index.md) failure — never null — per the Ticket 06/09 invariant (`length-prefix > remaining bytes -> BadLengthPrefix`); skew is fail-fast, never silent.

Delegates to nestedRegionOrNull (no guard duplication needed since this function is no longer `inline`, so it can call `internal` helpers).