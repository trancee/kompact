//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrame](index.md)

# KompactFrame

[common]\
class [KompactFrame](index.md)

Bounded forward-only reader over a borrowed array. Reads advance the cursor; direct read methods throw [KompactDecodeException](../-kompact-decode-exception/index.md) on malformed input. The block overload of [decode](-companion/decode.md) translates those failures into [KompactFrameResult](../-kompact-frame-result/index.md).

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [end](end.md) | [common]<br>val [end](end.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [raw](raw.md) | [common]<br>val [raw](raw.md): [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html) |
| [start](start.md) | [common]<br>val [start](start.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [readBits](read-bits.md) | [common]<br>fun [readBits](read-bits.md)(width: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)<br>Read 1..64 low bits in wire order, without crossing this frame's boundary. |
| [readBlob](read-blob.md) | [common]<br>fun [readBlob](read-blob.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactByteSlice](../-kompact-byte-slice/index.md)<br>Read a borrowed length-prefixed byte region without copying. |
| [readNested](read-nested.md) | [common]<br>fun [readNested](read-nested.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactByteSlice](../-kompact-byte-slice/index.md)<br>Read a bounded nested payload sharing the original backing array. |
| [readRepeated](read-repeated.md) | [common]<br>fun &lt;[T](read-repeated.md)&gt; [readRepeated](read-repeated.md)(countWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), elementWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), elementPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), decodeElement: ([KompactFrame](index.md)) -&gt; [T](read-repeated.md)): [KompactRepeatedView](../-kompact-repeated-view/index.md)&lt;[T](read-repeated.md)&gt;<br>Validate a count-prefixed sequence; retain sparse boundaries for variable elements and defer each element's value decoding until it is accessed. |
| [readString](read-string.md) | [common]<br>fun [readString](read-string.md)(prefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)<br>Read a length-prefixed UTF-8 string; throws [KompactDecodeException](../-kompact-decode-exception/index.md) for malformed input. |
| [requireComplete](require-complete.md) | [common]<br>fun [requireComplete](require-complete.md)()<br>Reject extra bytes/bits after the declared fields. |