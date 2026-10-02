//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrame](index.md)/[readRepeated](read-repeated.md)

# readRepeated

[common]\
fun &lt;[T](read-repeated.md)&gt; [readRepeated](read-repeated.md)(countWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), elementWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), elementPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), decodeElement: ([KompactFrame](index.md)) -&gt; [T](read-repeated.md)): [KompactRepeatedView](../-kompact-repeated-view/index.md)&lt;[T](read-repeated.md)&gt;

Validate a count-prefixed sequence; retain sparse boundaries for variable elements and defer each element's value decoding until it is accessed.