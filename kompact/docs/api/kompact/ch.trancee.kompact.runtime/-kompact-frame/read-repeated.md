//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrame](index.md)/[readRepeated](read-repeated.md)

# readRepeated

[common]\
fun &lt;[T](read-repeated.md)&gt; [readRepeated](read-repeated.md)(countWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), elementWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), elementPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), decodeElement: ([KompactFrame](index.md)) -&gt; [T](read-repeated.md)): [KompactRepeatedView](../-kompact-repeated-view/index.md)&lt;[T](read-repeated.md)&gt;

Validate a count-prefixed sequence; retain sparse boundaries for variable elements and defer each element's value decoding until it is accessed.

Exactly one of [elementWidth](read-repeated.md) (1..64) and [elementPrefixWidth](read-repeated.md) (8, 16, or 32) must be non-zero, and [countWidth](read-repeated.md) must be 8, 16, or 32.

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html) | for an invalid count width or element shape. |
| [KompactDecodeException](../-kompact-decode-exception/index.md) | when the count prefix or element payloads are malformed. |